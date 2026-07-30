# ShineConnoisseur 后端 — AI 开发上下文

> Spring Boot 3.5.3 + MyBatis-Plus 3.5.12 + Redis (Lettuce) + RabbitMQ + Elasticsearch

---

## 1. 项目结构

```
src/main/java/com/lzh/
├── controller/             # 用户端 API
├── controller/admin/       # 管理端 API（路由 /admins/**）
├── service/                # IXxxService 接口
├── service/impl/           # 用户端实现
├── service/impl/admin/     # 管理端实现
├── mapper/                 # extends BaseMapper<PO>
├── po/                     # @TableName 实体
├── dto/                    # 请求体
├── vo/                     # 响应视图
├── common/                 # Result, PageResult, BusinessException
├── config/                 # 拦截器、MyBatis、Redis、RabbitMQ、异常处理、CORS、OpenAPI
├── utils/                  # 拦截器实现、UserHolder、AdminHolder、常量
├── cache/init/             # ApplicationRunner 缓存预热
├── cache/task/             # @Scheduled 定时刷新
├── consumer/               # RabbitMQ @RabbitListener
├── document/               # ES 文档
└── repository/             # ES Repository

src/main/resources/db/      # 建表 + 测试数据 SQL
```

### 分层约束

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | 路由 + 参数校验 + 调用 Service | 写业务逻辑 |
| Service | 业务逻辑、缓存、MQ、事务 | 手写 SQL |
| Mapper | 数据库访问 | 写业务逻辑 |
| PO | 字段用 `@TableField` 映射下划线 | 手动设 createTime/updateTime |
| DTO/VO | 入参与出参 | DTO 暴露给前端 |

---

## 2. 认证体系

**双轨认证**: 用户端和管理端各有独立的 Token + Redis Hash 会话 + ThreadLocal + 拦截器链。

```
HTTP Request
  → RefreshTokenInterceptor (order=1)  ← 从 Redis 加载用户到 UserHolder；公开 GET 无 token 时注入游客(id=0)
  → LoginInterceptor (order=2)         ← UserHolder 为空则 401
  → Controller
```

- **公开 GET 端点**（无需登录）: `/movies/**`（不含 `/favorite`、`/or/`）、`/reviews/**`（不含 `/my`）、`/reviewComments/**`（不含 `/my`、`/like/`、`/publish/`）、`/users/info/**`
- **游客机制**: `RefreshTokenInterceptor` 对公开 GET 请求注入 id=0 的游客 DTO，避免 Service 层 NPE
- 登录 Redis Key: `login:token:{token}`，TTL 30min
- 验证码: `login:code:{phone}`，2~5min；错误计数 10min 内限 5 次

---

## 3. 业务设计要点

### 电影
- 评分: movie 表存 `rating_sum` + `rating_count`，读时算平均分
- 热门排行: Redis ZSet `movie:hot:`，score = `ratingCount*10 + ratingSum`，每 19 分钟全量重建
- 详情缓存: Cache-Aside + 空值防穿透（10min TTL）+ 随机偏移防雪崩

### 影评
- 每人每片限一条（review 表 user_id + movie_id 唯一约束）
- 软删除: `@TableLogic(value="1", delval="0")`，status: 0=删除 1=正常 2=封禁
- 热门排行: ZSet，时间衰减算法 `(likeCount*10 + commentCount*5 + 20) / sqrt(hours + 2)`，每天 6 点刷新
- 发布/删除时增量更新 movie 表 rating 计数

### 评论
- 两级结构: 根评论 `root_id=0`，子回复 `root_id` 指根评论
- 发布时增量更新 review 表 `comment_count`

### 点赞 / 收藏 / 关注
- 共用模式: **DB 唯一约束 + Redis Set 缓存**
- 关注包含空值哨兵 + 短 TTL 防穿透

### 消息通知
- `RabbitTemplate.convertAndSend()` → Topic Exchange `message.exchange` → 消费者幂等 INSERT
- `TransactionSynchronizationManager` 确保事务提交后才发 MQ
- 消息类型: 关注(0) 点赞影评(1) 评论(2) 点赞评论(3) 回复(4)

### 管理后台
- 独立 `admin` 表 + 独立拦截器链 `RefreshAdminTokenInterceptor` + `AdminLoginInterceptor`
- 功能: 用户管理、电影/影评/评论管理、数据看板（启动预热 + 每小时刷新）

---

## 4. Redis 使用模式

| 模式 | 数据结构 | 示例 Key | TTL |
|------|---------|----------|-----|
| Cache-Aside + 空值 | String(JSON) | `movie:info:{id}` | 30min+随机 |
| ZSet 排行 | ZSet | `movie:hot:`, `review:hot:` | 无过期，定时重建 |
| Set 缓存 | Set | `followings:{userId}` | 60min |
| Hash 会话 | Hash | `login:token:{token}` | 30min |
| String 验证码 | String | `login:code:{phone}` | 2~5min |

**规则**: Key 用 `RedisConstants` 常量；写入后主动删/更新缓存，不等 TTL；不用 `keys`/`scan`。

---

## 5. 开发规范

### 统一响应
```java
Result.ok() / Result.ok(data) / Result.ok(list, total) / Result.fail("原因")
```

### Controller
- `@Resource` 注入，`@RestController` + `@RequestMapping`
- 只做路由，返回 `Result`，不写业务逻辑
- 管理端放 `controller/admin/`，路由 `/admins/**`

### Service
- `extends ServiceImpl<Mapper, PO>` + `implements IXxxService`
- 业务失败 `return Result.fail()`，不抛异常
- DB 更新失败等非预期错误 `throw new BusinessException("原因")`

### 异常处理 (`WebExceptionAdvice`)
- `BusinessException` → 消息直接返回客户端
- `DuplicateKeyException` → "数据已存在"
- `DataIntegrityViolationException` → "数据约束冲突"
- `HttpMessageNotReadableException` → "请求格式错误"
- `RuntimeException` → "服务器异常"（兜底）

### MyBatis-Plus
- PO: `@TableName` + `@TableField` + `@TableId(type=AUTO)`
- 分页: `new Page<>(current, MAX_PAGE_SIZE)`，每页 10 条
- 禁止手写 SQL、禁止 >5 个条件的 Lambda 链

### 常量
- `SystemConstants`: 业务状态码、消息类型
- `RedisConstants`: Redis Key 前缀 + TTL
- `MQConstants`: Exchange/Queue 名称

---

## 6. 修改必查

**写入操作**: 关联计数是否更新？Redis 缓存是否清理？MQ 消息是否发送？唯一约束是否冲突？

**新增 API**: 是否公开读（无须改动）？是否管理端（放 `controller/admin/`）？返回是否是 `Result`？

**绝对禁止**: `@Autowired`、JPA、手写 Redis Key、抛异常表业务失败、`keys *`/`scan`、手动设时间字段

---

## 7. 常用命令

```bash
./mvnw compile              # 编译
./mvnw test                 # 测试
./mvnw spring-boot:run      # 运行 (8080)
./mvnw package -DskipTests  # 打包
```

依赖: MySQL `192.168.100.129:3306` / Redis `192.168.100.129:6379` / RabbitMQ `192.168.100.129:5672` vhost `/shine` / ES `192.168.100.129:9200`

API 文档: `http://localhost:8080/docs`
