# ShineConnoisseur（光影鉴赏家）

电影影评社区与会员平台 —— Spring Boot 3 + Vue 3 全栈项目。

> 支持电影浏览与全文搜索、影评和两级评论、关注互动、异步站内消息、VIP 会员、支付宝/微信双渠道支付，以及可选的 AI 观影助手和完整管理后台。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.3 |
| ORM | MyBatis-Plus | 3.5.12 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis (Lettuce) | 7 |
| 消息队列 | RabbitMQ | 3 |
| 搜索引擎 | Elasticsearch + IK 分词 | 8.x |
| 支付渠道 | 支付宝沙箱 + 微信支付 API v3 | — |
| AI Agent | FastAPI + LangChain + LangGraph | Python 3.13 |
| 前端框架 | Vue 3 (Composition API) | 3.5 |
| 构建工具 | Vite | 8 |
| UI 组件库 | Element Plus | 2.14 |
| 状态管理 | Pinia | 4 |
| 容器化 | Docker + Docker Compose | — |

## 功能模块

### 用户端

- **用户系统** — 手机验证码登录 / 密码登录、注册、找回密码、修改资料、注销账号
- **电影浏览** — 热门排行（Redis ZSet）、详情展示、全文搜索（Elasticsearch）、收藏
- **影评发布** — 评分 + 文字、点赞排行、编辑删除、热门推荐
- **两级评论** — 根评论 + 子回复、支持点赞
- **关注系统** — 关注/取关、粉丝列表、关注列表
- **消息通知** — 点赞、评论、关注、系统通知（RabbitMQ 异步消费）
- **VIP 会员** — 套餐查询、会员开通与原子续期、会员状态查询，以及完整的前端会员中心
- **双渠道支付** — 支付宝电脑网站支付、微信 Native 扫码支付、订单轮询与取消、异步回调
- **AI 观影助手** — 流式对话、站内检索、个性化推荐、影评总结与创作，支持会话记忆和 RAG 语义检索

### 管理后台

- 数据仪表盘（缓存预热 + 定时刷新）
- 用户 / 电影 / 影评 / 评论 增删改查
- 独立认证体系（双轨 Token 会话）

## 架构亮点

- **双轨认证** — 用户端和管理端独立 Token + Redis Hash 会话 + ThreadLocal 上下文
- **缓存策略** — Cache-Aside 模式、ZSet 排行、Set 缓存、Hash 会话、启动预热 + 定时刷新
- **消息异步** — RabbitMQ 异步消费，并在业务事务提交后再投递，避免事务回滚时提前发送消息
- **登录保护** — 验证码 2min 有效 + 错误限流（5 次/10 分钟）
- **游客机制** — 公开 GET 接口注入 id=0 游客 DTO，避免 Service 层空指针
- **全文搜索** — Elasticsearch + IK 中文分词，覆盖电影名和影评内容
- **支付一致性** — 幂等键 + 唯一索引 + 行锁 + 条件状态迁移，防止重复下单、重复回调和重复发放权益
- **支付状态机** — 覆盖待发起、发起中、待支付、关单中、支付成功、已关闭、退款中和已退款
- **主动对账与补偿** — 定时查单弥补回调丢失；关闭前后确认渠道状态；无法履约时以固定退款单号执行幂等退款
- **过期关单** — 后台批量扫描过期订单，并通过轮询时间刷新避免异常订单长期占用任务窗口
- **持续集成** — GitHub Actions 自动校验后端构建、前端生产构建与 Docker Compose 配置；现有测试报告作为非阻断产物保留
- **Agent 独立服务** — FastAPI 服务复用 MySQL、Redis 和 Elasticsearch，写操作统一通过后端 API，使用 Compose Profile 按需启用

## 快速启动（Docker）

```bash
# 1. 克隆项目及 Agent 子模块
git clone --recurse-submodules <your-repo-url>
cd ShineConnoisseur

# 2. 生成本地配置并替换其中所有 replace-with-* 密码
cp .env.example .env

# 微信私钥如需使用，放在未纳入版本控制的目录：
# secrets/wechat/apiclient_key.pem

# 3. 启动基础服务（MySQL + Redis + RabbitMQ + ES + 后端 + 前端）
docker compose up -d --build

# 如需 AI 助手，先在 .env 填写 DeepSeek 和 SiliconFlow API Key，再执行：
docker compose --profile ai up -d --build

# 4. 等待服务就绪后访问
#    前端页面:    http://localhost
#    后端 API:    http://localhost:8080
#    Agent API:   http://localhost:8001（启用 ai profile 后）
#    Swagger 文档: http://localhost:8080/docs
#    RabbitMQ 管理: http://localhost:15672
```

首次启动会自动初始化完整数据库结构，并导入电影、用户和三档 VIP 套餐等演示数据。支付宝和微信参数可以暂时留空，此时社区主体功能正常启动，实际支付接口不可用。

AI Agent 是可选服务。未启用 `ai` Profile 时主站和后端仍可正常运行；启用前必须配置 `DEEPSEEK_API_KEY` 与 `SILICONFLOW_API_KEY`，对外模型调用可能产生费用。

> MySQL 初始化脚本只会在数据卷首次创建时执行。更新表结构后请使用迁移脚本；仅在确认不需要本地数据时，才执行 `docker compose down -v` 后重新初始化。

## 本地开发

### 后端

```bash
cd backend/ShineConnoisseur

# 确保本地 MySQL / Redis / RabbitMQ / ES 已启动
# 按 .env.example 设置环境变量；application.yaml 不保存真实凭据

mvn spring-boot:run
# 启动在 http://localhost:8080
```

### 前端

```bash
cd frontend

npm install
npm run dev
# 启动在 http://localhost:5173，业务 API 代理到后端 8080，Agent API 代理到 8001
```

### AI Agent

```bash
# 首次克隆主仓库但未拉取子模块时执行
git submodule update --init --recursive

cd agent
cp .env.example .env
# 填写 Agent .env 中的模型 API Key 和本地中间件连接参数

python -m venv .venv
# Windows: .venv\Scripts\pip install -r requirements.txt
# Linux/macOS: .venv/bin/pip install -r requirements.txt
python run.py
# 启动在 http://localhost:8001
```

## 项目结构

```
ShineConnoisseur/
├── backend/ShineConnoisseur/   # Spring Boot 后端
│   ├── src/main/java/com/lzh/
│   │   ├── controller/         # 用户端 API（12 个端点）
│   │   ├── controller/admin/   # 管理端 API（15 个端点）
│   │   ├── service/            # 业务逻辑层
│   │   ├── config/             # 拦截器、缓存、MQ、CORS、OpenAPI 配置
│   │   ├── cache/              # 缓存预热 + 定时刷新
│   │   ├── consumer/           # RabbitMQ 消息消费者
│   │   └── task/               # 支付过期关单 + 主动对账
│   └── src/main/resources/db/  # 建表 SQL + 测试数据
├── frontend/                   # Vue 3 前端（26 个页面视图）
│   └── src/
│       ├── views/              # 页面组件
│       ├── api/                # Axios 接口封装
│       ├── stores/             # Pinia 状态管理
│       └── router/             # 路由配置 + 导航守卫
├── agent/                      # FastAPI AI Agent（Git Submodule，可选服务）
├── docker/                     # Docker 配置
│   ├── mysql/init.sql          # 完整建表 + 演示数据 + VIP 套餐
│   ├── elasticsearch/Dockerfile # IK 分词器镜像
│   └── nginx/default.conf      # Nginx 反向代理配置
├── docs/
│   ├── api-summary.md          # 完整 API 接口契约（68 个端点）
│   └── frontend-design.md      # 前端设计文档
├── docker-compose.yml          # 6 个基础服务 + 可选 Agent 服务
├── .github/workflows/ci.yml    # 后端、前端与 Compose 持续集成
├── .env.example                # 无真实密钥的环境变量模板
└── secrets/                    # 本地支付证书目录（Git 忽略）
```

## API 概览

共 **68 个接口**，统一响应格式 `{ success, errorMsg, data, total }`；支付平台回调按各渠道协议返回 HTTP 响应。

| 模块 | 公开 GET | 需登录 | 合计 |
|------|----------|--------|------|
| 用户 | 4 | 8 | 12 |
| 电影 | 3 | 3 | 6 |
| 影评 | 5 | 5 | 10 |
| 评论 | 2 | 5 | 7 |
| 关注 | — | 4 | 4 |
| 消息 | — | 4 | 4 |
| 上传 | — | 2 | 2 |
| VIP会员 | — | 2 | 2 |
| 支付 | 2 个渠道回调 | 5 | 7 |
| 管理端 | 1 | 13 | 14 |

支付订单创建要求客户端为每次创建意图生成 `Idempotency-Key` 请求头。订单状态：`0` 待发起、`1` 支付成功、`2` 已关闭、`3` 已退款、`4` 发起中、`5` 待支付、`6` 关单中、`7` 退款中。

详细接口文档见 [docs/api-summary.md](docs/api-summary.md)。

## 文档

- [API 接口契约](docs/api-summary.md) — 所有接口的请求/响应格式
- [前端设计文档](docs/frontend-design.md) — 页面清单、用户流程、技术方案

## 配置安全

- 仓库只提交 `.env.example`，`.env`、支付私钥和证书均被 Git 忽略。
- MySQL、Redis、RabbitMQ 密码没有代码内置默认值，Docker 启动时缺失会直接报错。
- 支付密钥通过环境变量或只读 `secrets/` 目录注入，不写入镜像和源码。
- Agent API Key 仅通过 `.env` 注入容器，不提交到主仓库或子模块。
- 公开部署前请更换所有示例密码，并使用 HTTPS 可访问地址配置支付回调。

## License

MIT
