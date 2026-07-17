---
name: shineconnoisseur-conventions
description: ShineConnoisseur movie review community backend — Spring Boot 3.5.3 + MyBatis-Plus + Redis + RabbitMQ. Enforces project-specific architecture, coding standards, Redis patterns, and layer conventions. Use for ALL code changes in this project.
---

# ShineConnoisseur 项目专属规范

> 电影影评社区后端。本 skill 确保所有代码修改遵循项目既定模式和约定。

---

## 项目架构

```
HTTP Request
  → RefreshTokenInterceptor (order=1, 从 Redis 加载用户/管理员到 ThreadLocal)
  → LoginInterceptor (order=2, 校验登录态, 未登录返回 401)
  → Controller (参数接收, 调用 Service, 返回 Result)
  → Service 接口 / ServiceImpl (业务逻辑, 缓存读写, MQ 发送)
  → Mapper extends BaseMapper<PO> (MyBatis-Plus 数据库访问)
  → MySQL / Redis / RabbitMQ
```

用户端 (`controller/`, `service/impl/`) 与管理端 (`controller/admin/`, `service/impl/admin/`) **物理隔离**，各自有独立的拦截器链和 ThreadLocal Holder。

---

## 技术栈 (精确版本)

| 组件 | 版本 | 注意 |
|------|------|------|
| Java | **21** | 可使用 Record、Switch Expression、Text Block 等特性 |
| Spring Boot | **3.5.3** | spring-boot-starter-web, aop, amqp, data-redis |
| MyBatis-Plus | **3.5.12** | **不是 JPA/Hibernate**，不能用 @Entity/@Repository |
| MySQL | **8.0.33** | 数据库名 `shineconnoisseur`, utf8mb4_unicode_ci |
| Redis | Lettuce 连接池 | StringRedisTemplate, **不是** RedisTemplate |
| Redisson | **3.23.5** | 已配置但业务代码未使用 |
| RabbitMQ | Spring AMQP | vhost `/shine` |
| Hutool | **5.8.40** | BeanUtil.copyProperties(), JSONUtil 做对象转换 |
| BCrypt | spring-security-crypto | 密码加密，**不是** Spring Security 框架 |

---

## 包结构约定

```
com.lzh/
├── controller/          # 用户端 API (XxxController)
├── controller/admin/    # 管理端 API (XxxController), 路由前缀 /admins/**
├── service/             # 接口 IXxxService
├── service/impl/        # 用户端实现 XxxServiceImpl
├── service/impl/admin/  # 管理端实现 XxxServiceImpl
├── mapper/              # XxxMapper extends BaseMapper<XxxPO>
├── po/                  # 持久化实体 @TableName 映射表名
├── dto/                 # 请求体/传输对象 XxxDTO
├── vo/                  # 响应视图 XxxVO
├── common/              # Result.java, PageResult.java
├── config/              # Spring @Configuration 类
├── utils/               # 拦截器, 常量, ThreadLocal Holder
├── cache/init/          # ApplicationRunner 缓存预热
├── cache/task/          # @Scheduled 定时刷新
└── consumer/            # RabbitMQ @RabbitListener
```

**新增文件必须放入正确的包，不允许跨层放错位置。**

---

## Controller 规范

```java
@RestController
@RequestMapping("/movies")
@Slf4j
public class MovieController {

    @Resource
    private IMovieService movieService;

    @GetMapping("/{movieId}")
    public Result getMovieInfo(@PathVariable Long movieId) {
        return movieService.getMovieInfo(movieId);
    }
}
```

**必须遵守：**

- **只做路由 + 参数接收 + 调用 Service**，绝不写业务逻辑
- **返回类型永远是 `Result`**，不使用 ResponseEntity 或裸对象
- **分页参数**：前端传 `current`（页码），每页固定 `SystemConstants.MAX_PAGE_SIZE = 10`，Service 层通过 `Page<PO>(current, MAX_PAGE_SIZE)` 分页
- **路径变量命名**：`{movieId}`、`{reviewId}`、`{userId}`、`{isFollow}` 等，保持项目已有风格
- 管理端 Controller 放在 `controller/admin/`，路由以 `/admins` 开头
- **不要**在 Controller 层做参数格式校验（用 `@Validated` + DTO 里的 `@Pattern`），复杂校验放 Service

### 禁止的做法

```java
// ❌ Controller 里写业务逻辑
// ❌ 返回非 Result 类型 (ResponseEntity, String, 裸对象)
// ❌ 手动构造 PageResult 而不是用 MyBatis-Plus Page
```

---

## Service 规范

```java
public interface IMovieService {
    Result getMovieInfo(Long movieId);
    Result listMovies(String title, String genre, String region, Integer current);
    Result listHotMovies();
}
```

```java
@Service
@Slf4j
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements IMovieService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MovieMapper movieMapper;
}
```

**必须遵守：**

- **依赖注入统一用 `@Resource`**，不用 `@Autowired`
- **需要 MyBatis-Plus 自带方法时**：`extends ServiceImpl<XxxMapper, XxxPO>`，否则只 `implements IXxxService`
- **管理端 Service 放在 `service/impl/admin/`** 包下，不混入用户端
- 日志：`log.error("描述信息", exception)` 记录异常，`log.info()` 记录关键流程
- **业务失败返回 `Result.fail("原因")`**，不抛异常（除非是不可恢复的运行时错误）
- **数据写入后必须处理缓存一致性**：删除或更新相关 Redis Key

---

## MyBatis-Plus / Mapper 规范

```java
@Mapper
public interface MovieMapper extends BaseMapper<Movie> {
    // 复杂查询写在这里或 XML
}
```

**必须遵守：**

- **每个 PO 对应一个 Mapper**，继承 `BaseMapper<PO>`
- **PO 类必须标注 `@TableName("movie")`** 显式映射表名
- **下划线字段用 `@TableField` 显式映射**：`@TableField("review_count") private Integer reviewCount;`
- **主键用 `@TableId(type = IdType.AUTO)`**
- **分页用 MyBatis-Plus 的 `Page<PO>`**：`mapper.selectPage(new Page<>(current, MAX_PAGE_SIZE), wrapper)`
- 复杂查询：优先 `@Select` 注解，特别复杂时用 `src/main/resources/mapper/XxxMapper.xml`
- **禁止**手写 SQL 拼接字符串
- **禁止**在 Service 里用 `LambdaQueryWrapper` 写超长链式调用（超过 5 个条件拆成方法）
- 实体与表名映射不一致时用 `@TableName(autoResultMap = true)` 支持 TEXT 等大字段

---

## 数据库规范 (MySQL 8.0)

### 核心表关系

```
user ─→ review (user_id + movie_id UNIQUE, 每人每片一条影评)
user ─→ review_comment (root_id 实现两级评论)
user ─→ user_follow, movie_favorite, like_record, message
movie ─→ review, movie_favorite
admin ─ 独立表, 不关联 user
```

### 必须遵守

- **新增表**：在 `src/main/resources/db/create_xxx.sql` 添加建表语句，同时创建 PO 和 Mapper
- **唯一约束**：关联表必须有 (user_id, target_id) 组合唯一索引防重复
- **状态字段用 Integer**：1=正常, 0=禁用/删除（定义在 `SystemConstants`）
- **计数冗余**：review_count、like_count、follower_count 等冗余在父表，**写入时必须同步更新**
- **评分冗余**：movie 表存 `rating_sum` + `rating_count`，读取时计算平均值，新增/修改/删除影评时增量更新
- **软删除**：review 和 review_comment 通过 status 字段标记（0=删除, 1=正常, 2=审核），不物理删除
- **时间字段**：所有表的 `create_time` 和 `update_time` 由 `MyMetaObjectHandler` 自动填充，**PO 里不手动设值**
- **字符集**：统一 `utf8mb4_unicode_ci`
- 自增主键类型统一用 `Long`，数据库列类型 `BIGINT`

### 状态常量对照 (SystemConstants)

| 常量 | 值 | 适用 |
|------|-----|------|
| `USER_STATUS_NORMAL` | 1 | user.status |
| `USER_STATUS_BAN` | 0 | user.status |
| `REVIEW_STATUS_NORMAL` | 1 | review.status |
| `REVIEW_STATUS_BAN` | 0 | review.status |
| `REVIEW_STATUS_AUDITING` | 2 | review.status |
| `COMMENT_STATUS_NORMAL` | 1 | review_comment.status |
| `COMMENT_STATUS_BAN` | 0 | review_comment.status |
| `MOVIE_STATUS_NORMAL` | 1 | movie.status |
| `MOVIE_STATUS_BAN` | 0 | movie.status |

---

## Redis 使用规范

### Key 命名与常量

所有 Redis Key 前缀统一在 `RedisConstants` 中定义，**禁止**在业务代码里拼接字符串字面量。

### 五种使用模式

#### 模式 1：Cache-Aside + 空值缓存（防穿透）

适用场景：**电影详情、用户信息**等单条数据缓存

```
查 Redis → 命中返回
         → 值为 "empty" → 返回不存在
         → miss → 查 DB
                → 不存在 → 缓存 "empty" (TTL_EMPTY = 10 min)
                → 存在 → 缓存 JSON (TTL_NORMAL + 随机偏移防雪崩)
```

#### 模式 2：ZSet 热门排行

适用场景：**热门电影、热门影评**

- 定时任务（@Scheduled）全量重建 ZSet，score 按算法计算
- 查询时 `reverseRangeWithScores(0, maxSize - 1)` 取 Top N
- ZSet **不设过期时间**，靠定时任务刷新

#### 模式 3：Set 关联关系 + 哨兵

适用场景：**关注列表、粉丝列表、点赞集合、收藏集合**

- 缓存 Set 结构，TTL 60min（关注）或永不过期（点赞/收藏）
- miss 时查 DB 重建 Set
- **空 Set 用 "empty" 哨兵值防穿透**，空哨兵 TTL 设为 20-30 min
- 写入时主动删除 Key，下次查询触发重建

#### 模式 4：Hash 会话存储

适用场景：**用户 Token、管理员 Token**

- Key: `login:token:{token}` / `admin:token:{token}`
- 存 DTO 字段的 Hash 结构（不是 JSON String）
- TTL 30min，RefreshTokenInterceptor 每次请求刷新
- 拦截器从 Hash 读字段拼装 UserDTO / AdminDTO 到 ThreadLocal

#### 模式 5：String 验证码 + 错误计数

适用场景：**登录验证码、注册验证码、密码错误计数**

- 验证码 TTL 2~5min
- 错误计数 TTL 10min，超过阈值（5 次）拒绝请求
- 用 `stringRedisTemplate.opsForValue()` 操作

### 必须遵守

- **数据写入后必须主动更新/删除相关缓存**，不能等 TTL 自然过期
- **严禁** keys 命令或 scan 遍历 — 用精确 Key 操作
- TTL 加随机偏移（±5min）防缓存雪崩
- 老版本数据残留：功能下线时同步检查 RedisConstants 是否有对应 Key 可清理

---

## 对象转换规范

```java
// PO → VO：使用 Hutool
MovieVO vo = BeanUtil.copyProperties(movie, MovieVO.class);

// 列表转换
List<MovieVO> voList = poList.stream()
    .map(po -> BeanUtil.copyProperties(po, MovieVO.class))
    .toList();

// DTO → PO：同样用 copyProperties
Movie movie = BeanUtil.copyProperties(dto, Movie.class);
```

- 对象转换统一用 Hutool `BeanUtil.copyProperties()` 或 `JSONUtil`
- **禁止**手写 getter/setter 逐字段赋值（字段多时易遗漏）

---

## 认证与拦截器规范

### 请求头

```
authorization: <token>
```

### 拦截器链

| Order | 拦截器 | 路径 | 职责 |
|-------|--------|------|------|
| 1 | `RefreshTokenInterceptor` | `/**` 排除 `/admins/**` | 从 Redis 加载 UserDTO 到 UserHolder |
| 1 | `RefreshAdminTokenInterceptor` | `/admins/**` | 从 Redis 加载 AdminDTO 到 AdminHolder |
| 2 | `LoginInterceptor` | 排除白名单 | 检查 UserHolder，空则 401 |
| 2 | `AdminLoginInterceptor` | 排除 `/admins/login` | 检查 AdminHolder，空则 401 |

### 新增 API 时检查

- [ ] 是否需要**免登录**？→ 加入 `InterceptorConfig` 的 `excludePathPatterns`
- [ ] 是否**管理端**接口？→ 放 `controller/admin/`，路由以 `/admins` 开头
- [ ] Controller 方法是否通过 `UserHolder.getUser()` / `AdminHolder.getAdmin()` 获取当前用户？

---

## 消息队列规范 (RabbitMQ)

### 架构

- Exchange: `message.exchange` (Topic, 持久化)
- Queue: `message.queue` (持久化)
- Routing Key: `message.#`
- 消费者: `MessageConsumer` (持久化到 message 表)

### 消息类型 (SystemConstants)

| type | 常量 | 触发场景 |
|------|------|----------|
| 0 | `MESSAGE_TYPE_FOLLOW` | 用户关注 |
| 1 | `LIKE_REVIEW` | 点赞影评 |
| 2 | `COMMENT` | 发表评论 |
| 3 | `LIKE_COMMENT` | 点赞评论 |
| 4 | `REPLY_COMMENT` | 回复子评论 |

### 发送消息

```java
rabbitTemplate.convertAndSend(MQConstants.MESSAGE_EXCHANGE, "message.follow", messageDTO);
```

- 消息体用 `MessageDTO`，包含发送者、接收者、消息类型、目标类型、目标 ID、内容
- **新增通知类型**：同步更新 `SystemConstants` 的消息类型常量和 `MessageConsumer` 的处理逻辑

---

## 修改代码时的"必检清单"

每次修改代码，你必须确认以下所有项：

### 写入操作必须检查

1. [ ] **关联计数是否同步更新？**（影评数、点赞数、评论数、关注数、评分总和）
2. [ ] **Redis 缓存是否处理？**（删除或更新对应的 Key）
3. [ ] **是否需要发送 MQ 消息？**（关注、点赞、评论、回复）
4. [ ] **唯一约束是否可能冲突？**（review: user_id+movie_id, follow: user_id+follow_user_id 等）

### 读取操作必须检查

1. [ ] **分页是否用 `SystemConstants.MAX_PAGE_SIZE`？**
2. [ ] **是否先查 Redis 缓存？**（按对应模式：Cache-Aside / ZSet / Set）
3. [ ] **是否处理了空缓存穿透？**（"empty" 哨兵或空 Set + 短 TTL）

### 新增文件必须检查

1. [ ] **Controller 是否放对包？**（用户端 vs 管理端）
2. [ ] **依赖注入是否用的 `@Resource`？**
3. [ ] **返回类型是否是 `Result`？**
4. [ ] **SQL 脚本是否同步添加到 `db/` 目录？**
5. [ ] **是否需要加入白名单？**

### 禁止事项（绝对不要做）

| 禁止 | 正确做法 |
|------|----------|
| 使用 `@Autowired` | 使用 `@Resource` |
| 使用 JPA `@Entity` / `@Repository` | 使用 MyBatis-Plus `@TableName` / `BaseMapper` |
| 在 Controller 写业务逻辑 | 放到 Service 层 |
| 返回非 `Result` 类型 | 统一返回 `Result` |
| 字符串拼接 Redis Key | 使用 `RedisConstants` 常量 |
| 抛异常表示业务失败 | 返回 `Result.fail()` |
| 手写 setter/getter 逐字段赋值 | 用 `BeanUtil.copyProperties()` |
| 在 PO 里手动设 `createTime` | 由 `MyMetaObjectHandler` 自动填充 |
| 用 Spring Security 框架 | 项目用自建拦截器 + 注解，不要引入 Spring Security |
| 改造为 JPA 风格 | 项目是 MyBatis-Plus，保持一致 |

---

## 快速参考

### 关键类位置

| 类 | 路径 |
|----|------|
| 业务状态常量 | `com.lzh.utils.SystemConstants` |
| Redis Key 常量 | `com.lzh.utils.RedisConstants` |
| MQ 常量 | `com.lzh.utils.MQConstants` |
| 分页大小 | `SystemConstants.MAX_PAGE_SIZE` = 10 |
| 用户 ThreadLocal | `com.lzh.utils.UserHolder` |
| 管理员 ThreadLocal | `com.lzh.utils.AdminHolder` |
| 拦截器注册 | `com.lzh.config.InterceptorConfig` |
| 自动填充 | `com.lzh.config.MyMetaObjectHandler` |
| 全局异常处理 | `com.lzh.config.WebExceptionAdvice` |
| 统一响应 | `com.lzh.common.Result` |
| 分页结果 | `com.lzh.common.PageResult` |

### 常用代码片段

```java
// 分页查询
Page<Movie> page = new Page<>(current, SystemConstants.MAX_PAGE_SIZE);
Page<Movie> result = movieMapper.selectPage(page, wrapper);

// 获取当前用户
UserDTO user = UserHolder.getUser();
Long userId = user.getId();

// Redis 查缓存
String cached = stringRedisTemplate.opsForValue().get(RedisConstants.MOVIE_INFO_KEY + movieId);

// Redis 写缓存 + 随机 TTL
long ttl = RedisConstants.TTL_MOVIE_INFO + RandomUtil.randomLong(0, 300);
stringRedisTemplate.opsForValue().set(key, jsonStr, ttl, TimeUnit.SECONDS);

// 发送 MQ 消息
rabbitTemplate.convertAndSend(MQConstants.MESSAGE_EXCHANGE, "message.follow", messageDTO);
```
