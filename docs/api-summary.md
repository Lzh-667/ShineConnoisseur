# ShineConnoisseur API 接口契约

> 前后端接口契约，基于后端 Controller 代码生成。字段名与后端 VO/DTO 完全一致。

---

## 统一响应格式

```json
{ "success": true, "errorMsg": null, "data": ..., "total": 100 }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | Boolean | 请求是否成功 |
| `errorMsg` | String | 失败时的错误信息 |
| `data` | Object / Array | 响应数据 |
| `total` | Long | 分页总数，非分页接口为 `null` |

**PageResult**: `{ total: Long, records: Array }` — 普通分页  
**ScrollResult**: `{ list: Array, hasMore: Boolean }` — 滚动分页

分页参数 `current`（页码，从 1 开始），每页固定 10 条。

---

## 公开读 vs 需登录

- **公开 GET**（游客可浏览）：电影详情/热门/搜索、影评详情/热门/搜索/列表、评论列表、用户信息
- **需登录**：所有 POST/PUT/DELETE + 个人中心类 GET（`/my`、`/me`、`/favorite`、消息、关注）

游客通过 `RefreshTokenInterceptor` 注入 id=0 的游客身份，`isLike`/`canEditAndDelete` 等字段自然返回 `false`。

---

## 1. 用户模块 — `/users`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/users/code` | 发送登录验证码 `?phone=` | 免 |
| POST | `/users/login/code` | 验证码登录 Body: LoginFormDTO | 免 |
| POST | `/users/login/password` | 密码登录 Body: LoginFormDTO | 免 |
| POST | `/users/registerCode` | 发送注册验证码 `?phone=` | 免 |
| POST | `/users/register` | 注册 Body: RegisterFormDTO | 免 |
| POST | `/users/resetCode` | 发送重置密码验证码 `?phone=` | 免 |
| POST | `/users/resetPassword` | 重置密码 Body: ResetPasswordDTO | 免 |
| GET | `/users/info/{id}` | 用户公开信息 → UserInfo | 公开 |
| GET | `/users/me` | 当前登录用户信息 | 需 |
| POST | `/users/logout` | 登出 | 需 |
| PUT | `/users/profile` | 修改资料 Body: UpdateProfileDTO | 需 |
| PUT | `/users/password` | 修改密码 Body: UpdatePasswordDTO | 需 |
| DELETE | `/users/account` | 注销账号（软删除） | 需 |

**LoginFormDTO**: `phone`, `username`, `code`, `password`  
**RegisterFormDTO**: `username`, `password`, `code`, `email`, `phone`, `confirmPassword`  
**ResetPasswordDTO**: `phone`, `code`, `password`, `confirmPassword`  
**UpdateProfileDTO**: `nickname`, `avatar`, `bio`, `gender` (至少填一项)  
**UpdatePasswordDTO**: `oldPassword`, `newPassword`

**UserInfo 字段**: `id`, `username`, `nickname`, `avatar`, `bio`, `gender`(0/1/2), `reviewCount`, `followingCount`, `followerCount`

---

## 2. 电影模块 — `/movies`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/movies/{movieId}` | 电影详情 → MovieVO | 公开 |
| GET | `/movies/hot` | 热门电影（Redis ZSet） | 公开 |
| GET | `/movies/search` | 搜索电影 `?keyword=&current=` | 公开 |
| GET | `/movies/favorite` | 我的收藏列表 `?current=` | 需 |
| GET | `/movies/or/not/{movieId}` | 是否已收藏 → Boolean | 需 |
| POST | `/movies/favorite/{movieId}/{isFavorite}` | 收藏/取消收藏 | 需 |

**MovieVO 字段**: `id`, `title`, `originalTitle`, `cover`, `director`, `actors`, `genre`, `region`, `language`, `releaseDate`(yyyy-MM-dd), `duration`(分钟), `summary`, `rating`(BigDecimal), `ratingCount`

---

## 3. 影评模块 — `/reviews`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/reviews/{reviewId}` | 影评详情 → ReviewVO | 公开 |
| GET | `/reviews/movie/{movieId}` | 影片下的影评列表 `?current=` | 公开 |
| GET | `/reviews/user/{userId}` | 用户影评列表 `?current=` | 公开 |
| GET | `/reviews/hot` | 热门影评（滚动分页）`?current=` | 公开 |
| GET | `/reviews/search` | 搜索影评 `?keyword=&current=&spoiler=` | 公开 |
| GET | `/reviews/my` | 我的影评（滚动分页）`?current=` | 需 |
| POST | `/reviews/publish/{movieId}` | 发布影评 Body: ReviewDTO | 需 |
| POST | `/reviews/like/{reviewId}` | 点赞/取消点赞 → LikeVO | 需 |
| PUT | `/reviews/{reviewId}` | 修改影评 Body: ReviewDTO | 需 |
| DELETE | `/reviews/{reviewId}` | 删除影评（软删除） | 需 |

**ReviewDTO**: `rating`(1-10), `title`(最长50), `content`(最长1000), `spoiler`(0/1)

**ReviewVO 字段**: `id`, `rating`, `title`, `content`, `spoiler`, `userId`, `userName`, `nickName`, `avatar`, `likeCount`, `commentCount`, `isLike`, `canEditAndDelete`, `movieVO`(关联电影), `createTime`

---

## 4. 评论模块 — `/reviewComments`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/reviewComments/list/root/{reviewId}` | 根评论列表（滚动分页）`?current=` | 公开 |
| GET | `/reviewComments/list/children/{rootId}` | 子回复列表（滚动分页）`?current=` | 公开 |
| GET | `/reviewComments/{id}/target` | 查评论所属影评 → CommentTargetVO | 公开 |
| GET | `/reviewComments/my` | 我的评论（滚动分页）`?current=` | 需 |
| POST | `/reviewComments/publish/{reviewId}` | 发表评论 Body: ReviewCommentDTO | 需 |
| POST | `/reviewComments/like/{reviewCommentId}` | 点赞/取消点赞 → LikeVO | 需 |
| DELETE | `/reviewComments/{reviewCommentId}` | 删除评论（软删除） | 需 |

**ReviewCommentDTO**: `rootId`(0=一级评论), `replyUserId`, `content`  
**CommentTargetVO**: `{ reviewId, commentId }`

**ReviewCommentVO 字段**: `id`, `author`(UserDTO), `replyUser`(UserDTO), `rootId`, `content`, `likeCount`, `replyCount`, `isLike`, `canEditAndDelete`, `createTime`

**UserDTO**（嵌入）: `id`, `username`, `nickname`, `avatar`

---

## 5. 关注模块 — `/follows`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/follows/list/follower` | 粉丝列表 `?current=` | 需 |
| GET | `/follows/list/following` | 关注列表 `?current=` | 需 |
| GET | `/follows/or/not/{id}` | 是否已关注 → Boolean | 需 |
| POST | `/follows/{id}/{isFollow}` | 关注/取关 | 需 |

---

## 6. 消息模块 — `/messages`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/messages/list` | 消息列表 `?current=&type=` | 需 |
| GET | `/messages/unread/count` | 未读消息数 → Long | 需 |
| PUT | `/messages/read/{id}` | 标记单条已读 | 需 |
| PUT | `/messages/read/all` | 全部标记已读 | 需 |

**消息类型**（type 筛选）: 0=关注, 1=点赞影评, 2=评论影评, 3=点赞评论, 4=回复评论

**MessageVO**: `id`, `type`, `fromUser`(UserDTO), `content`, `targetType`, `targetId`, `status`(0=未读), `createTime`

**跳转逻辑**: type=0 → `/users/{fromUserId}`; type=1/2 → `/reviews/{targetId}`; type=3/4 → 先调 `/reviewComments/{targetId}/target` 获取 reviewId → `/reviews/{reviewId}`

---

## 7. 文件上传 — `/upload`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/upload/avatar` | 上传头像 form-data: `file` → 图片路径 | 需 |

约束: jpg/jpeg/png/gif/webp，最大 5MB。

---

## 8. 管理员模块 — `/admins`

管理端独立认证体系（`admin` 表 + 独立 Token + 独立拦截器链）。

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/admins/login` | 登录 Body: AdminLoginDTO | 免 |
| POST | `/admins/logout` | 登出 | 需 |
| GET | `/admins/dashboard` | 数据看板 → AdminDashboardVO | 需 |
| GET | `/admins/users/list` | 用户列表 `?current=` | 需 |
| GET | `/admins/users/info/{id}` | 用户详情 → UserInfo | 需 |
| PUT | `/admins/users/status/{id}` | 封禁/解封用户 | 需 |
| GET | `/admins/movies/list` | 电影列表 `?current=` | 需 |
| POST | `/admins/movies/publish` | 发布电影 Body: AdminMovieDTO | 需 |
| PUT | `/admins/movies/update/{id}` | 编辑电影 Body: AdminMovieDTO | 需 |
| PUT | `/admins/movies/status/{id}` | 上架/下架 | 需 |
| GET | `/admins/reviews/list` | 影评列表 `?current=` | 需 |
| PUT | `/admins/reviews/status/{id}` | 封禁/解封影评 | 需 |
| GET | `/admins/comments/list` | 评论列表 `?current=` | 需 |
| PUT | `/admins/comments/status/{id}` | 封禁/解封评论 | 需 |
| POST | `/admins/upload/poster` | 上传电影海报 form-data: `file` | 需 |

**AdminLoginDTO**: `username`, `password`  
**AdminMovieDTO**: `title`, `originalTitle`, `cover`, `director`, `actors`, `genre`, `region`, `language`, `releaseDate`, `duration`, `summary`  
**AdminDashboardVO**: `userCount`, `movieCount`, `reviewCount`, `todayReviewCount`, `weekReviewCount`, `updateTime`

---

## 9. LikeVO（统一点赞返回）

`{ like: Boolean, likeCount: Integer }` — 点赞/取消点赞接口统一返回，`like` 为操作后状态。

---

## 接口总览

| 模块 | 数量 | 公开 |
|------|------|------|
| 用户 | 12 | 7 个免登（code/login/register/reset） + `/info/{id}` 公开 |
| 电影 | 6 | 详情/热门/搜索公开；收藏相关需登录 |
| 影评 | 10 | 详情/列表/热门/搜索/用户影评公开；发布/点赞/修改/删除需登录 |
| 评论 | 7 | 列表/target 公开；发布/点赞/删除/my 需登录 |
| 关注 | 4 | 全部需登录 |
| 消息 | 4 | 全部需登录 |
| 上传 | 1 | 需登录 |
| 管理员 | 15 | login 免登 |
| **合计** | **59** | |
