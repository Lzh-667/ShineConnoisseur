# ShineConnoisseur — AI 开发上下文

> 电影影评社区后端服务。单体 Spring Boot 3 应用，MyBatis-Plus + Redis + RabbitMQ。
> 每个 AI 对话开始时读取本文档，快速理解项目全貌。

---

## 1. 项目定位与核心业务

**ShineConnoisseur**（光影鉴赏家）是一个电影影评社区平台的后端 API 服务。

核心业务场景：
- 用户注册/登录（手机验证码 + 密码登录），Token + Redis 会话管理
- 电影浏览、搜索、收藏、热门排行（基于评分的 ZSet）
- 影评发布/修改/删除（每人每片限一条），点赞、热门排行（基于时间衰减算法）
- 影评评论（两级结构：根评论 + 子回复），点赞
- 用户关注/取关、粉丝列表
- 站内消息通知（关注、点赞、评论、回复）—— RabbitMQ 异步发送
- 管理后台（独立认证体系，用户/电影/影评/评论管理，数据看板）

---

## 2. 技术栈

| 类别 | 技术 | 版本 | 备注 |
|------|------|------|------|
| 语言 | Java | 21 | — |
| 框架 | Spring Boot | 3.5.3 | Web, AOP, AMQP, Data-Redis |
| ORM | MyBatis-Plus | 3.5.12 | `BaseMapper` + `ServiceImpl`，非 JPA |
| 数据库 | MySQL | 8.0.33 | `utf8mb4_unicode_ci` |
| 缓存 | Redis (Lettuce) | — | `StringRedisTemplate`，非 `RedisTemplate` |
| 分布式锁 | Redisson | 3.23.5 | 已配置，业务层未使用 |
| 消息队列 | RabbitMQ | Spring AMQP | Topic Exchange，Jackson JSON 序列化 |
| 工具库 | Hutool | 5.8.40 | `BeanUtil.copyProperties()` 做对象转换 |
| 密码加密 | BCrypt | spring-security-crypto | 非 Spring Security 框架 |
| 构建 | Maven | — | `mvnw` wrapper |

---

## 3. 项目结构

```
src/main/java/com/lzh/
├── ShineConnoisseurApplication.java   # @SpringBootApplication, @MapperScan, @EnableScheduling
├── controller/                        # 用户端 API Controller
├── controller/admin/                  # 管理端 API Controller（路由 /admins/**）
├── service/                           # 业务接口 IXxxService
├── service/impl/                      # 用户端实现
├── service/impl/admin/                # 管理端实现（与用户端物理隔离）
├── mapper/                            # MyBatis Mapper extends BaseMapper<PO>
├── po/                                # 持久化实体 @TableName
├── dto/                               # 请求体 / 内部传输对象
├── vo/                                # 响应视图对象
├── common/                            # Result.java, PageResult.java
├── config/                            # Spring 配置（拦截器、MyBatis、Redis、RabbitMQ、BCrypt、异常处理）
├── utils/                             # 拦截器、常量、ThreadLocal Holder
├── cache/init/                        # ApplicationRunner 启动缓存预热
├── cache/task/                        # @Scheduled 定时缓存刷新
└── consumer/                          # RabbitMQ @RabbitListener 消费者
```

### 分层职责

| 层 | 职责 | 关键约束 |
|----|------|----------|
| Controller | 路由接收、参数校验、调用 Service、返回 `Result` | 不写业务逻辑 |
| Service | 业务逻辑、缓存读写、MQ 发送、事务管理 | 接口 `IXxxService`，实现 `XxxServiceImpl` |
| Mapper | 数据库访问 | `extends BaseMapper<PO>`，复杂查询用注解或 XML |
| PO | 实体映射 | `@TableName` 表名，`@TableField` 下划线字段，`@TableId(type=AUTO)` |
| DTO | 入参/内部传输 | 只用于请求接收和内部传递，不暴露给前端 |
| VO | 响应视图 | 按前端展示需要裁剪字段 |

用户端与管理端 Service 实现在不同子包（`impl/` vs `impl/admin/`），各自有独立的拦截器链和 ThreadLocal Holder。

---

## 4. 核心业务设计

### 4.1 用户体系

- **双轨认证**：用户端和管理端各有一套独立的 Token + Redis 会话 + ThreadLocal 体系
- 登录：手机验证码登录 OR 用户名密码登录，密码 BCrypt 加密
- 验证码存储在 Redis（2-5 min TTL），错误计数限流（10 min 内最多 5 次）
- Token 存为 Redis Hash（30 min TTL），每次请求由拦截器刷新
- 当前用户通过 `UserHolder.getUser()` (返回 `UserDTO`) 或 `AdminHolder.getAdmin()` (返回 `AdminDTO`) 获取

### 4.2 电影模块

- **评分计算**：`movie` 表存 `rating_sum`（评分总和）+ `rating_count`（评分数量），读取时实时算平均分
- **热门排行**：Redis ZSet，score = `ratingCount * 10 + ratingSum`，每 30 分钟定时全量重建
- **详情缓存**：Cache-Aside + 空值防穿透，TTL 30 min + 随机偏移防雪崩
- **收藏**：`movie_favorite` 表 (user_id, movie_id) 唯一约束，Redis Set 缓存收藏者

### 4.3 影评模块

- 每人每片只有一条影评（review 表 user_id + movie_id 唯一约束）
- 发布/修改/删除影评时增量更新 movie 表的 rating_sum 和 rating_count
- **软删除**：status 字段标记（1=正常, 0=封禁, 2=审核中），不物理删除
- **热门排行**：ZSet，score = `(likeCount*10 + commentCount*5 + 20) / sqrt(hoursSinceCreation + 2)`，每天 6 点刷新
- 点赞通过 `like_record` 表 + Redis Set 双重记录

### 4.4 评论模块

- **两级结构**：根评论 `root_id = 0`，子回复 `root_id` 指向根评论 ID
- 评论表存 `reply_user_id` 标识被回复者
- 发布评论时增量更新 review 表的 `comment_count`

### 4.5 点赞 / 收藏 / 关注

- 三者共用模式：**DB 唯一约束 + Redis Set 缓存**
- like_record: (user_id, target_id, target_type) 唯一
- movie_favorite: (user_id, movie_id) 唯一
- user_follow: (user_id, follow_user_id) 唯一
- 关注/取关时更新 user 表的 `follower_count` / `following_count`
- 关注关系 Redis Set 含空值哨兵 + 短 TTL 防穿透

### 4.6 消息通知

- 所有通知异步发送：`RabbitTemplate.convertAndSend()` → RabbitMQ → `MessageConsumer` → 持久化到 `message` 表
- 消息类型：关注(0)、点赞影评(1)、评论(2)、点赞评论(3)、回复评论(4)
- 消息内容通过 `MessageDTO` 传递，包含发送者、接收者、类型、目标

### 4.7 管理员模块

- 独立 `admin` 表，不关联 `user` 表
- 独立拦截器链：`RefreshAdminTokenInterceptor` + `AdminLoginInterceptor`
- 功能：用户管理（封禁/解封）、电影管理（发布/编辑/上下架）、影评管理（封禁/解封）、评论管理、数据看板
- 看板数据缓存：启动预热 + 每小时刷新

---

## 5. 核心技术设计

### 5.1 认证拦截器链

```
HTTP Request
  → RefreshTokenInterceptor (order=1)  ← 从 Redis Hash 加载 UserDTO 到 UserHolder, 刷新 TTL
      或 RefreshAdminTokenInterceptor (order=1)  ← 同上，管理端
  → LoginInterceptor (order=2)  ← 检查 UserHolder 是否为空，空则 401
      或 AdminLoginInterceptor (order=2)  ← 同上，管理端
  → Controller
```

拦截器注册在 `InterceptorConfig`，白名单路径硬编码在其中。**新增免登录接口时必须同步更新白名单。**

### 5.2 Redis 使用模式

所有 Key 前缀定义在 `RedisConstants`，业务代码禁止拼接 Key 字符串。

| 模式 | 数据结构 | Key 示例 | TTL | 适用场景 |
|------|---------|----------|-----|----------|
| **Cache-Aside + 空值** | String (JSON) | `movie:info:{id}` | 30min + 随机 | 电影详情，空值缓存防穿透 |
| **ZSet 排行** | ZSet | `movie:hot:` | 无过期，定时重建 | 热门电影/影评 |
| **Set + 哨兵** | Set | `followings:{userId}` | 60min / 永久 | 关注、粉丝、点赞、收藏 |
| **Hash 会话** | Hash | `login:token:{token}` | 30min | 用户/管理员 Token |
| **String 验证码** | String | `login:code:{phone}` | 2~5min | 验证码、错误计数 |

**关键规则：**
- 写入数据后**必须主动删除或更新相关缓存**，不能等 TTL 自然过期
- 空值缓存 TTL = 10 min（`RedisConstants.TTL_EMPTY`），正常 TTL 加随机偏移防雪崩
- 不使用 `keys` 命令或 `scan` 遍历

### 5.3 RabbitMQ

- 单一 Topic Exchange：`message.exchange`，持久化
- 单一队列：`message.queue`，路由键 `message.#`
- 消费者幂等：消息只做 INSERT 到 message 表，失败抛异常回队
- 消息转换：`Jackson2JsonMessageConverter`

### 5.4 数据库设计原则

- **计数冗余**：review_count、like_count、comment_count、follower_count、rating_sum、rating_count 冗余在父表，写入时同步增量更新
- **软删除**：review 和 review_comment 通过 status 字段，不物理删除
- **唯一约束**：所有关联表都有 (user_id, target_id) 组合唯一约束
- **时间自动填充**：`MyMetaObjectHandler` 对 `createTime`（INSERT）和 `updateTime`（INSERT + UPDATE）自动处理，PO 不手动设值
- **自增主键**：统一 `BIGINT` → Java `Long`

---

## 6. 开发规范

### 6.1 统一响应

所有接口返回 `Result`：`{success, errorMsg, data, total}`。

```java
Result.ok()              // 成功无数据
Result.ok(data)          // 成功带数据
Result.ok(list, total)   // 分页列表
Result.fail("原因")       // 业务失败
```

分页参数名 `current`（页码），每页大小固定 `SystemConstants.MAX_PAGE_SIZE = 10`。

### 6.2 Controller 规范

```java
@RestController
@RequestMapping("/movies")
public class MovieController {
    @Resource
    private IMovieService movieService;

    @GetMapping("/{movieId}")
    public Result getMovieInfo(@PathVariable Long movieId) {
        return movieService.getMovieInfo(movieId);
    }
}
```

- 只做路由 + 参数校验 + 调用 Service，**业务逻辑零容忍**
- 返回类型永远是 `Result`
- 管理端 Controller 放 `controller/admin/`，路由以 `/admins` 开头

### 6.3 Service 规范

```java
public interface IMovieService {
    Result getMovieInfo(Long movieId);
}

@Service
@Slf4j
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements IMovieService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
}
```

- **依赖注入：`@Resource`**，不用 `@Autowired`
- 需要 MyBatis-Plus 自带 CRUD 时 `extends ServiceImpl<Mapper, PO>`
- 业务失败返回 `Result.fail()`，不抛异常
- 异常仅用 `log.error("描述", e)` 记录

### 6.4 Mapper / MyBatis-Plus 规范

```java
@Mapper
public interface MovieMapper extends BaseMapper<Movie> { }

@Data
@TableName("movie")
public class Movie {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("rating_sum")
    private Double ratingSum;
}
```

- PO 字段用 `@TableField` 显式映射下划线名
- 分页：`new Page<>(current, MAX_PAGE_SIZE)`
- **禁止**手写拼接 SQL、禁止在 Service 层写超长 LambdaQueryWrapper 链式调用（>5 个条件拆方法）
- 新增表：同步创建 `db/create_xxx.sql` + PO + Mapper

### 6.5 DTO / VO 转换

```java
MovieVO vo = BeanUtil.copyProperties(movie, MovieVO.class);
List<MovieVO> list = poList.stream().map(p -> BeanUtil.copyProperties(p, MovieVO.class)).toList();
```

对象转换统一用 Hutool，禁止逐字段手写 getter/setter。

### 6.6 异常处理

- 业务失败：`return Result.fail("原因")`，**不抛异常**
- RuntimeException：`WebExceptionAdvice` 兜底，返回 `Result.fail("服务器异常")`

### 6.7 常量管理

| 常量类型 | 位置 | 示例 |
|----------|------|------|
| 业务状态码 | `SystemConstants` | `USER_STATUS_NORMAL=1`, `REVIEW_STATUS_AUDITING=2` |
| Redis Key + TTL | `RedisConstants` | `MOVIE_INFO_KEY`, `TTL_MOVIE_INFO` |
| MQ Exchange/Queue | `MQConstants` | `MESSAGE_EXCHANGE = "message.exchange"` |
| 消息类型 | `SystemConstants` | `MESSAGE_TYPE_FOLLOW=0`, `LIKE_REVIEW=1` |

---

## 7. 修改代码注意事项

### 写入操作必查

1. 关联计数是否同步更新？（review_count, like_count, comment_count, follower_count, rating_sum/count）
2. 是否需要删除/更新对应的 Redis 缓存？
3. 是否需要发送 MQ 消息通知？
4. 唯一约束是否会冲突？

### 新增 API 必查

1. 是否需要加入 `InterceptorConfig` 白名单？
2. 返回类型是否是 `Result`？
3. 分页是否用了 `MAX_PAGE_SIZE`？
4. 管理端接口是否放在了 `controller/admin/` 下？

### 绝对禁止

| 禁止 | 原因 | 正确做法 |
|------|------|----------|
| `@Autowired` | 项目统一 `@Resource` | `@Resource` |
| JPA `@Entity` / `@Repository` | 项目用 MyBatis-Plus | `@TableName` / `BaseMapper` |
| Controller 写业务逻辑 | 破坏分层 | 放到 Service |
| 返回非 `Result` | 破坏统一响应 | `Result.ok()` / `Result.fail()` |
| 手写 Redis Key 字符串 | 分散维护 | `RedisConstants` 常量 |
| 抛异常表示业务失败 | 项目约定 | `return Result.fail()` |
| 引入 Spring Security | 自建拦截器体系 | 扩展 `LoginInterceptor` |
| 手动设 `createTime/updateTime` | 自动填充 | `MyMetaObjectHandler` |
| `keys *` 或 `scan` | 性能灾难 | 精确 Key 操作 |
```

---

### 修复注意事项

- 最小改动原则：只改任务相关文件，不重构无关代码
- 不破坏拦截器链：用户端和管理端拦截器独立
- 缓存一致性：写入后主动处理 Redis，不依赖 TTL
- MQ 消息：新增消息类型时同步更新 `SystemConstants` 常量和 `MessageConsumer`
- SQL 变更：表结构变更同步更新 `db/` 脚本和 `po/` 实体
- 不提交敏感信息：`application.yaml` 含密码，勿泄露

### 常用命令

```bash
mvnw compile              # 编译
mvnw test                 # 测试
mvnw spring-boot:run      # 运行 (端口 8080)
mvnw package -DskipTests  # 打包
mvnw clean                # 清理
```

### 前置依赖

MySQL `192.168.100.129:3306/shineconnoisseur` / Redis `192.168.100.129:6379` / RabbitMQ `192.168.100.129:5672` vhost `/shine`

数据库初始化：按序执行 `src/main/resources/db/create_*.sql`，可选 `insert_*.sql` 导入测试数据。
