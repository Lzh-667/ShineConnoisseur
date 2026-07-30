# ShineConnoisseur（光影鉴赏家）

电影影评社区平台 —— Spring Boot 3 + Vue 3 全栈项目。

> 支持用户浏览电影、发布影评、两级评论、关注互动、站内消息通知，以及完整的管理后台。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.3 |
| ORM | MyBatis-Plus | 3.5.12 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis (Lettuce) | 7 |
| 消息队列 | RabbitMQ | 3 |
| 搜索引擎 | Elasticsearch + IK 分词 | 8.x |
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

### 管理后台

- 数据仪表盘（缓存预热 + 定时刷新）
- 用户 / 电影 / 影评 / 评论 增删改查
- 独立认证体系（双轨 Token 会话）

## 架构亮点

- **双轨认证** — 用户端和管理端独立 Token + Redis Hash 会话 + ThreadLocal 上下文
- **缓存策略** — Cache-Aside 模式、ZSet 排行、Set 缓存、Hash 会话、启动预热 + 定时刷新
- **消息异步** — RabbitMQ + `TransactionSynchronizationManager` 保证事务一致性
- **登录保护** — 验证码 2min 有效 + 错误限流（5 次/10 分钟）
- **游客机制** — 公开 GET 接口注入 id=0 游客 DTO，避免 Service 层空指针
- **全文搜索** — Elasticsearch + IK 中文分词，覆盖电影名和影评内容

## 快速启动（Docker）

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd ShineConnoisseur

# 2. 修改 .env 中的密码（可选，默认即可启动）

# 3. 一键启动所有服务（MySQL + Redis + RabbitMQ + ES + 后端 + 前端）
docker-compose up -d

# 4. 等待服务就绪后访问
#    前端页面:    http://localhost
#    后端 API:    http://localhost:8080
#    Swagger 文档: http://localhost:8080/docs
#    RabbitMQ 管理: http://localhost:15672
```

首次启动会自动初始化数据库表结构并导入种子数据（60 部电影、33 个用户）。

## 本地开发

### 后端

```bash
cd backend/ShineConnoisseur

# 确保本地 MySQL / Redis / RabbitMQ / ES 已启动
# 修改 application.yaml 中的连接地址为 localhost

mvn spring-boot:run
# 启动在 http://localhost:8080
```

### 前端

```bash
cd frontend

npm install
npm run dev
# 启动在 http://localhost:5173，自动代理到后端 8080
```

## 项目结构

```
ShineConnoisseur/
├── backend/ShineConnoisseur/   # Spring Boot 后端（103 个 Java 源文件）
│   ├── src/main/java/com/lzh/
│   │   ├── controller/         # 用户端 API（12 个端点）
│   │   ├── controller/admin/   # 管理端 API（15 个端点）
│   │   ├── service/            # 业务逻辑层
│   │   ├── config/             # 拦截器、缓存、MQ、CORS、OpenAPI 配置
│   │   ├── cache/              # 缓存预热 + 定时刷新
│   │   └── consumer/           # RabbitMQ 消息消费者
│   └── src/main/resources/db/  # 建表 SQL + 测试数据
├── frontend/                   # Vue 3 前端（22 个页面视图）
│   └── src/
│       ├── views/              # 页面组件
│       ├── api/                # Axios 接口封装
│       ├── stores/             # Pinia 状态管理
│       └── router/             # 路由配置 + 导航守卫
├── docker/                     # Docker 配置
│   ├── mysql/init.sql          # 建表 + 150+ 行种子数据
│   ├── elasticsearch/Dockerfile # IK 分词器镜像
│   └── nginx/default.conf      # Nginx 反向代理配置
├── docs/
│   ├── api-summary.md          # 完整 API 接口契约（59 个端点）
│   └── frontend-design.md      # 前端设计文档
├── docker-compose.yml          # 6 服务编排 + 健康检查 + 持久化卷
└── .env                        # 部署环境变量
```

## API 概览

共 **59 个接口**，统一响应格式 `{ success, errorMsg, data, total }`。

| 模块 | 公开 GET | 需登录 | 合计 |
|------|----------|--------|------|
| 用户 | 4 | 8 | 12 |
| 电影 | 3 | 3 | 6 |
| 影评 | 5 | 5 | 10 |
| 评论 | 2 | 5 | 7 |
| 关注 | — | 4 | 4 |
| 消息 | — | 4 | 4 |
| 上传 | — | 2 | 2 |
| 管理端 | 1 | 13 | 14 |

详细接口文档见 [docs/api-summary.md](docs/api-summary.md)。

## 文档

- [API 接口契约](docs/api-summary.md) — 所有接口的请求/响应格式
- [前端设计文档](docs/frontend-design.md) — 页面清单、用户流程、技术方案

## License

MIT
