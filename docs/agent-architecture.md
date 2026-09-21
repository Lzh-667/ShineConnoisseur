# ShineConnoisseur Agent 架构设计文档

> AI 助手服务（光影鉴赏家 Agent）架构设计，基于 `agent/` 目录代码生成。独立 Python 服务，端口 8001。

---

## 1. 定位与总体架构

Agent 是影评社区的 AI 助手：帮用户发现电影、查影评、获推荐、做总结、辅助创作影评，并具备长期记忆（用户画像）能力。

**技术栈**：Python 3.13 + FastAPI + LangChain 1.x（`create_agent`）+ LangGraph（checkpointer）

**外部依赖**（与后端共享基础设施，连接地址由环境变量注入；Docker 环境使用 Compose 服务名）：

| 依赖 | 用途 | 共享方式 |
|------|------|---------|
| MySQL `shineconnoisseur` | 只读取数（电影/影评/用户行为）+ 长期记忆表 | 与后端同库 |
| Redis | 会话元信息、画像缓存、热门榜、同步游标、embedding 缓存、限流 | 与后端同实例，Key 加 `agent:` 前缀 |
| Elasticsearch 8.15 | 关键词搜索（原 `movie`/`review` 索引）+ 语义检索（`movie_vec`/`review_vec`） | 复用后端索引 |
| DeepSeek API | 主循环模型 + 纯文本任务模型 | 独立 API Key |
| SiliconFlow API | BGE-M3 embedding（1024 维） | 独立 API Key |
| 后端 REST API（8080） | **唯一写通道**（发布影评等） | token 透传 |

**数据访问约定**：读操作直连 MySQL/ES/Redis；写操作调后端 REST API（`authorization` header 透传后端登录 token）。

```
前端 Vue (localhost:5173)
   │  authorization: <后端登录 token>
   ▼
Agent FastAPI (8001)
   ├── api/chat.py          ── 对话接口（非流式 / SSE 流式）
   ├── api/sessions.py      ── 会话列表/删除
   ├── api/profile.py       ── 用户画像查询
   ├── api/admin.py         ── 健康检查 / ES 同步 / 工具直调 / tool 统计
   │
   ├── agent/builder.py     ── create_agent 组装（model+tools+middleware+checkpointer）
   ├── agent/checkpointer.py── 对话状态持久化（SQLite / Memory）
   ├── agent/middleware.py  ── 画像注入 + 热门 tool 统计
   ├── agent/llm.py         ── 双模型工厂（主循环 / reasoner 直调）
   │
   ├── tools/               ── 16 个 LangChain 工具（查询/RAG/推荐/总结/创作/记忆）
   ├── rag/                 ── BGE-M3 embedding + ES 混合检索 + 索引同步
   ├── memory/              ── 用户画像长期记忆（规则聚合 + 惰性刷新 + 对话偏好）
   ├── services/            ── MySQL/Redis/ES 客户端、后端 REST、认证、会话、推荐引擎
   └── prompts/             ── 提示词模板（与代码分离）
            │
            ├── MySQL / Redis / ES（只读）
            └── 后端 8080 REST（写）
```

---

## 2. Agent 组装（builder.py）

LangChain 1.x `create_agent` 单例（`lru_cache`），组成要素：

| 要素 | 实现 | 说明 |
|------|------|------|
| model | DeepSeek `deepseek-v4-pro` | 主循环模型，支持 function calling，temperature 0.7 |
| tools | `app/tools` 全部 16 个工具 | 见第 6 节 |
| system_prompt | `prompts/system.md` + `prompts/plan.md` | 启动时加载拼接 |
| middleware | 3 个中间件，顺序执行 | 见下表 |
| checkpointer | SQLite（`data/agent_checkpoints.db`） | 崩溃可恢复；`agent_checkpoint=memory` 切内存版（调试） |
| context_schema | `AgentContext` | 运行期上下文注入 |

**AgentContext**（运行期上下文，工具通过 `ToolRuntime.context` 读取）：

```python
class AgentContext(BaseModel):
    user_id: int = 0      # 0 = 游客
    thread_id: str = ""
    token: str = ""       # 后端登录 token（写操作透传用）
```

**中间件链**（`agent/middleware.py`）：

| 中间件 | 钩子 | 职责 |
|--------|------|------|
| `ProfileInjectionMiddleware` | `before_model` | 每轮模型调用前把用户画像摘要注入 system 消息，带 `## 当前用户画像` 标记防重复注入（幂等） |
| `ToolUsageMiddleware` | `wrap_tool_call` | 每次工具调用写入 Redis ZSet（月维度），支撑热门 tool 统计 |
| `SummarizationMiddleware` | LangChain 内置 | 上下文超 60000 tokens 时压缩，保留最近 20 条消息 |

> 注意：agent 走 `ainvoke`/`astream` 异步调用，自定义中间件钩子必须同时实现 sync + async 两个版本。

**双模型分工**（`agent/llm.py`）：

| 模型 | 用途 | 特点 |
|------|------|------|
| `deepseek-v4-pro` | 主循环（工具编排、草稿、标题） | function calling，timeout 60s |
| `deepseek-v4-flash` | 总结/情感分析直调 | 不支持 function calling，**不进工具循环**，timeout 120s |

---

## 3. 对话链路

### 3.1 非流式 `POST /api/agent/chat`

1. 认证：解析 `authorization` header → 读后端 Redis Hash `login:token:{token}` → 得到 userId / nickname（无 token 为游客 userId=0）
2. 限流：滑动窗口 `agent:rate:{userId}`，每分钟 `chat_rate_limit`（默认 10）次，超限返回 `发言太频繁了`
3. 会话：`threadId` 为空则生成 uuid；`touch_session` 维护会话元信息（新会话用首条消息前 20 字做标题）
4. 组装 `AgentContext(user_id, thread_id, token)` 并调用 `agent.ainvoke`，300s 超时
5. 返回 `{threadId, reply, tools[]}`，`tools` 含每次工具调用的 name/args/摘要（ToolMessage 内容前 120 字）

### 3.2 SSE 流式 `POST /api/agent/chat/stream`

`agent.astream(stream_mode=["messages", "updates"])`，事件协议：

| event | data | 时机 |
|-------|------|------|
| `message` | `{delta}` | 模型逐 token 输出 |
| `tool` | `{name, status:"end", summary}` | 每个工具执行完成 |
| `source` | `{type, id, title, score}` | `semantic_search` 返回结果逐条推送（前端展示引用来源） |
| `done` | `{threadId, durationMs}` | 正常结束 |
| `error` | `{message}` | 异常 |

`EventSourceResponse` 心跳 `ping=15s`，保证连接存活。

---

## 4. 认证与会话

### 4.1 认证（services/auth.py）

- 复用**后端登录会话**：后端登录时在 Redis 写 Hash `login:token:{token}`（字段 id/username/nickname/avatar）
- agent 只读该 Hash 解析身份，**不自己签发会话**
- 无 token / token 失效 → 游客 `{userId: 0}`，前端对应体验与后端游客拦截器一致

### 4.2 会话双存储

| 存储 | Key/位置 | 内容 | TTL |
|------|---------|------|-----|
| 会话元信息 | Redis Hash `agent:session:meta:{threadId}` | userId/title/messageCount/createdAt/updatedAt | 7 天 |
| 对话历史 | LangGraph SQLite checkpointer | 完整消息流（供恢复/压缩） | 永久 |

- 会话列表用 `SCAN` 匹配 + 内存过滤（避免 `KEYS` 阻塞），按 updatedAt 倒序分页（每页 10 条）
- 删除会话需登录且校验归属（401/403）

---

## 5. Redis Key 清单

全部收敛于 `services/redis_client.py::AgentRedisKeys`，禁止手写 Key：

| Key 模式 | 类型 | 用途 | TTL |
|----------|------|------|-----|
| `agent:session:meta:{threadId}` | Hash | 会话元信息 | 7 天 |
| `agent:tool:stats:{YYYYMM}` | ZSet | 热门 tool 调用排行（月维度） | 永久 |
| `agent:profile:{userId}` | String(JSON) | 用户画像缓存 | 30 分钟 |
| `agent:sync:review:cursor` | String | review_vec 增量同步游标 | 永久 |
| `agent:sync:embed:cache:{md5}` | String(JSON) | embedding 结果缓存（按文本 md5） | 30 天 |
| `agent:rate:{userId}` | String(计数) | 聊天限流 | 60 秒窗口 |

另复用后端 Key：`login:token:{token}`（认证）、`movie:hot:` / `review:hot:`（热门榜 ZSet）、`movie:info:{id}`（电影详情缓存）。

---

## 6. 工具清单（16 个）

### 6.1 查询工具（QUERY_TOOLS，能力 1）

数据链路与后端保持一致（同权重、同降级）：

| 工具 | 数据链路 |
|------|---------|
| `list_hot_movies` | 直读 Redis ZSet `movie:hot:` → MySQL 补详情 |
| `list_hot_reviews` | 直读 Redis ZSet `review:hot:` → MySQL 补详情 |
| `get_movie_detail` | Redis `movie:info:{id}` 缓存 → MySQL（Cache-Aside 同构） |
| `list_movie_reviews` | MySQL（点赞降序，每页 10 条） |
| `search_movies` | ES bool+multiMatch（title^3, originalTitle^2, director^2, actors）→ 失败降级 MySQL LIKE |
| `search_reviews` | ES（title^3, movieTitle^2, content）→ 失败降级 MySQL LIKE |

### 6.2 RAG 语义检索（RAG_TOOLS，能力 2）

`semantic_search(query, index=movie|review, top_k, genre/region/spoiler)` — 按语义（不要求关键词精确匹配）搜索，如「时间旅行」「穿越时空」能互相命中。详见第 7 节。

### 6.3 推荐与对比（RECOMMEND_TOOLS，能力 3）

| 工具 | 策略 |
|------|------|
| `recommend_for_scene` | 8 个场景模板（约会/亲子/治愈/悬疑/动作/科幻/恐怖/深夜）→ 模板映射条件推荐，候选不足时放宽评分到 6.5 补足；自动排除登录用户已收藏电影 |
| `recommend_by_favorites` | 收藏协同：聚合收藏电影的类型偏好 top3 → 推荐同类型未收藏/未评分电影；无收藏时热度兜底 |
| `recommend_by_conditions` | 条件筛选（类型/最低评分/地区/年代范围）+ 热度排序；评分两级策略：严格集（有评分且达标）不足时用暂无评分电影补足（标记 `noRating`） |
| `compare_movies` | 2-5 部电影字段对齐对比（评分/类型/地区/导演/演员/上映/片长/简介），供 markdown 表格输出 |

**热度分与后端热门算法一致**：`ratingCount*10 + ratingSum`。数据量小（60 部），候选池全量拉取后在内存过滤排序。

**年代范围解析**：支持 `1990-2000`、`>2000`、`<2000`、`1994`（含全角符号）。

### 6.4 影评总结与分析（SUMMARY_TOOLS，能力 4）

| 工具 | 说明 |
|------|------|
| `summarize_reviews` | 取该电影最多 50 条影评（点赞序）→ reasoner 直调总结整体口碑/评分倾向/高赞观点/共识优缺点；支持 `focus` 指定维度；<3 条影评时强制注明「结论仅供参考」 |
| `analyze_review_sentiment` | 输出结构化 JSON：overview / positive / negative / mixed / ratingStats；宽松解析 LLM 输出（容忍 markdown 代码块包裹） |

### 6.5 创作与发布（CREATIVE_TOOLS，能力 5）

| 工具 | 说明 |
|------|------|
| `draft_review` | 电影详情 + 已有影评片段（RAG 注入）→ 起草 200-400 字影评；支持 `user_prompt`（风格/角度）、`rating`（口吻匹配） |
| `generate_review_title` | 给出 3 个候选标题，可结合正文 |
| `publish_review` | **唯一写操作**：走后端 REST `POST /reviews/publish/{movieId}`，token 透传；发布前必须获得用户明确确认（system prompt 硬性规则）；识别「数据已存在」（每人每片限一条） |

### 6.6 长期记忆（MEMORY_TOOLS，能力 6）

| 工具 | 说明 |
|------|------|
| `get_user_profile` | 读当前用户画像（惰性刷新），推荐前先了解偏好 |
| `save_preference` | 用户在对话中**明确表达**的偏好（「我喜欢诺兰」「我只看国产片」）写入事件表；`confidence=high` 才合并进画像，`medium` 仅存档；禁止从单次查询行为推断 |

---

## 7. RAG 语义检索

### 7.1 索引设计

独立向量索引，**不动后端原 `movie`/`review` 索引**：

| 索引 | 文档 | 向量 |
|------|------|------|
| `movie_vec` | 电影：id/title/originalTitle/director/actors/genre/region/summary/status | `content_embedding` |
| `review_vec` | 影评：id/title/content/movieId/movieTitle/spoiler/status | `content_embedding` |

- 文本字段 IK 分词（`ik_max_word`/`ik_smart`），与后端索引结构一致，保证 BM25 路质量均衡
- `content_embedding`：BGE-M3 1024 维 dense_vector，cosine + HNSW（m=16, ef_construction=100），单分片零副本

**embedding 文本拼接**：电影 = 标题+原名+导演+演员+类型+地区+简介；影评 = 标题+电影名+正文。

### 7.2 混合检索（es_hybrid.py）

```
用户 query
   ├─ ① BM25 路：multi_match（与后端同加权）+ status 过滤
   ├─ ② 向量路：query 向量化 → knn(cosine) + 独立 filter，num_candidates=100
   └─ ③ 客户端 RRF 融合：score(d) = Σ 1/(60 + rank(d))，窗口 top50，取 top_k
        （ES 8.15 基础版不支持服务端 RRF，客户端实现与 ES 服务端语义一致）
```

ES 异常时降级 MySQL LIKE（与后端降级策略一致），返回 `score=0.0` 的兜底结果。

### 7.3 同步策略（sync.py）

| 数据 | 策略 | 细节 |
|------|------|------|
| 电影 | 启动全量 + 每日一次 | status=1 写入索引，其余清理（删除已下架文档） |
| 影评 | 增量轮询（5 分钟） | `update_time > 游标` 的批次（100 条）；status≠1 删除；**失败不推进游标**（下次重试）；游标存 Redis `agent:sync:review:cursor`，缺失时自动全量 |

后端发布/删除影评后 5 分钟内进入/移出 `review_vec`。

### 7.4 embedding 成本控制（embeddings.py）

- 批量 32 条 + 指数退避重试（最多 4 次）
- 结果按文本 md5 缓存 Redis 30 天 → 重同步/重复 query 不重复计费
- 同步任务跑在 `asyncio.to_thread`，不阻塞事件循环

---

## 8. 长期记忆（用户画像）

### 8.1 表结构

与业务表同库（`shineconnoisseur`），前缀 `agent_` 隔离；服务启动时 `create_all(checkfirst=True)` 幂等建表，`scripts/init_db.sql` 供手动初始化。

| 表 | 说明 |
|----|------|
| `agent_user_profile` | 每用户一行：genre/actor/director/region 偏好频次（JSON）、rating_tendency、scene_prefs/watch_prefs（对话提取）、profile_summary（VARCHAR 500，注入用） |
| `agent_preference_event` | 偏好事件流水：source（1=收藏 2=影评 3=对话）、item_type、item_value、weight |

### 8.2 画像构建（双来源）

| 来源 | 内容 | 方式 |
|------|------|------|
| 规则聚合 | 收藏电影 → 类型/演员/导演/地区频次 top5/3；影评评分 → 打分倾向（avg/count/min/max） | 实时 SQL 聚合，数据量小不持久化中间态 |
| 对话偏好 | 「我喜欢诺兰」等明确表述 → `save_preference` | 写事件表；high 置信度合并进画像 JSON 字段并重建摘要 |

### 8.3 三级读取 + 惰性刷新（extractor.get_or_refresh）

```
Redis 缓存(30min) ──命中且新鲜──▶ 返回
        │ 未命中
        ▼
MySQL 画像 ──未过期──▶ 回填缓存，返回
        │ 过期（用户最新行为时间 > 画像 updated_at）
        ▼
规则重算 build_profile ──▶ 写库 + 写缓存，返回
```

### 8.4 注入时机

`ProfileInjectionMiddleware` 在**每轮模型调用前**把 `profileSummary` 追加到 system 消息（带标记防重复），并指示模型「个性化推荐时可结合偏好，但不要复述画像内容」。游客（userId=0）跳过注入。

---

## 9. 限流与运维

### 9.1 聊天限流

Redis 滑动窗口：`INCR agent:rate:{userId}` + 首次 `EXPIRE 60s`，超过 `chat_rate_limit`（默认 10 次/分钟）拒绝。游客与登录用户同一规则（按 userId 区分）。

### 9.2 运维接口（api/admin.py）

| 接口 | 说明 |
|------|------|
| `GET /api/agent/health` | 五连检查：mysql（SELECT 1）/ redis（ping）/ es（ping）/ llm / embedding（API Key 是否配置） |
| `POST /api/agent/es/sync?type=all\|movie\|review` | 手动触发向量索引同步 |
| `GET /api/agent/tool-stats?month=YYYYMM` | 热门 tool 排行（ZSet top20） |
| `POST /api/agent/tools/{toolName}` | 工具直调（调试/前端快捷能力）；依赖运行期上下文的工具（`runtime` 参数）拒绝直调 |

### 9.3 启动/关闭

- 启动：预热 MySQL/Redis 连接 → 幂等建表 → 后台任务 `run_sync_loop`（确保索引 + 首轮同步，之后影评 5 分钟轮询、电影每日重同步），不阻塞启动
- 关闭：取消同步任务、关闭 Redis 连接池、关闭 ES 客户端

---

## 10. 提示词体系（prompts/）

| 文件 | 用途 |
|------|------|
| `system.md` | 主 system prompt：角色定位、数据边界（严禁编造站内数据、附 id）、工具使用规则、推荐规则、剧透敏感、发布确认流程、安全边界 |
| `plan.md` | 观影计划输出规范（markdown 结构 + 硬性要求），启动时拼接到 system prompt |
| `summary.md` | 影评总结模板 |
| `sentiment.md` | 正负面观点分析模板（要求输出 JSON） |
| `review_draft.md` | 影评草稿模板 |
| `title.md` | 标题生成模板 |

关键安全规则（system prompt 硬约束）：数据只来自工具结果、引用附 id、剧透先提示、`publish_review` 必须用户确认后调用、不泄露其他用户隐私。

---

## 11. 部署运行

主项目已将 Agent 注册为 Git Submodule，并通过可选 Compose Profile 编排：

```bash
git submodule update --init --recursive
docker compose --profile ai up -d --build
```

也可以单独进行本地开发：

```bash
cd agent
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt   # Windows；Linux 用 .venv/bin/pip
cp .env.example .env
.venv/Scripts/python run.py                     # uvicorn，端口 8001
```

关键环境变量（`.env`，见 `config/settings.py`）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DEEPSEEK_API_KEY` / `SILICONFLOW_API_KEY` | 空 | 未配置时 health 检查报 fail，也可用系统环境变量 |
| `MYSQL_HOST` / `REDIS_HOST` / `ES_URL` | 必填 | Compose 中分别使用 `mysql`、`redis`、`http://elasticsearch:9200` |
| `BACKEND_URL` | `http://localhost:8080` | Compose 中使用 `http://backend:8080`，写操作走后端 REST |
| `AGENT_CHECKPOINT` | `sqlite` | `sqlite`（生产）/ `memory`（调试） |
| `CHAT_RATE_LIMIT` | 10 | 每分钟聊天次数 |
| `DEEPSEEK_MODEL` / `DEEPSEEK_REASONER_MODEL` | `deepseek-v4-pro` / `deepseek-v4-flash` | 双模型 |

---

## 12. 目录结构

```
agent/
├── run.py                  # 启动入口（uvicorn :8001）
├── requirements.txt
├── .env / .env.example     # 环境变量配置
├── data/agent_checkpoints.db  # LangGraph 对话状态（SQLite）
├── scripts/init_db.sql     # 长期记忆表 DDL（手动初始化用）
├── tests/                  # pytest 单测（es_hybrid / memory / recommendation / summary_tools）
└── app/
    ├── main.py             # FastAPI 入口 + 启动/关闭生命周期
    ├── config/settings.py  # pydantic-settings 配置
    ├── api/                # chat / sessions / profile / admin / schemas
    ├── agent/              # builder / checkpointer / llm / middleware / context / system_prompt
    ├── tools/              # 16 个工具（query/rag/recommend/summary/creative/memory）
    ├── rag/                # embeddings / es_hybrid / index_templates / sync
    ├── memory/             # models / store / extractor
    ├── services/           # mysql / es_client / redis_client / backend_api / auth /
    │                       #   session_store / profile / recommendation
    └── prompts/            # system / plan / summary / sentiment / review_draft / title
```
