# ShineConnoisseur 前端设计文档

> 基于最新后端 API 分析，前端实现参考。

---

## 1. 模块总览

| 模块 | 路由前缀 | 公开读 | 写操作 |
|------|----------|--------|--------|
| 电影浏览 | `/movies` | 详情/热门/搜索 | 收藏（需登录） |
| 影评 | `/reviews` | 详情/列表/热门/搜索/用户影评 | 发布/点赞/修改/删除 |
| 评论 | `/reviewComments` | 根评论/子回复/target | 发布/点赞/删除 |
| 用户 | `/users` | `/info/{id}` | 登录/注册/资料/密码等 |
| 关注 | `/follows` | — | 全部需登录 |
| 消息 | `/messages` | — | 全部需登录 |
| 上传 | `/upload` | — | 需登录 |
| 管理后台 | `/admins` | — | 独立认证 |

---

## 2. 接口清单

### 2.1 用户端 — 全部接口

**用户 `/users`**

| 方法 | 路径 | 说明 | 免登录 |
|------|------|------|--------|
| POST | `/users/code` | 发送登录验证码 `?phone=` | 是 |
| POST | `/users/login/code` | 验证码登录 → token | 是 |
| POST | `/users/login/password` | 密码登录 → token | 是 |
| POST | `/users/registerCode` | 发送注册验证码 `?phone=` | 是 |
| POST | `/users/register` | 注册 → token | 是 |
| POST | `/users/resetCode` | 发送重置密码验证码 `?phone=` | 是 |
| POST | `/users/resetPassword` | 重置密码 | 是 |
| GET | `/users/info/{id}` | 用户公开信息 | 公开 |
| GET | `/users/me` | 当前用户信息 | 否 |
| POST | `/users/logout` | 登出 | 否 |
| PUT | `/users/profile` | 修改资料 | 否 |
| PUT | `/users/password` | 修改密码 | 否 |
| DELETE | `/users/account` | 注销账号 | 否 |

**电影 `/movies`**

| 方法 | 路径 | 说明 | 免登录 |
|------|------|------|--------|
| GET | `/movies/{movieId}` | 电影详情 | 公开 |
| GET | `/movies/hot` | 热门电影（Redis ZSet） | 公开 |
| GET | `/movies/search` | 搜索 `?keyword=&current=` | 公开 |
| GET | `/movies/favorite` | 我的收藏 `?current=` | 否 |
| GET | `/movies/or/not/{movieId}` | 是否已收藏 → Boolean | 否 |
| POST | `/movies/favorite/{movieId}/{isFavorite}` | 收藏/取消 | 否 |

**影评 `/reviews`**

| 方法 | 路径 | 说明 | 免登录 |
|------|------|------|--------|
| GET | `/reviews/{reviewId}` | 影评详情 | 公开 |
| GET | `/reviews/movie/{movieId}` | 影片影评列表 `?current=` | 公开 |
| GET | `/reviews/user/{userId}` | 用户影评列表 `?current=` | 公开 |
| GET | `/reviews/hot` | 热门影评（滚动分页）`?current=` | 公开 |
| GET | `/reviews/search` | 搜索 `?keyword=&current=&spoiler=` | 公开 |
| GET | `/reviews/my` | 我的影评（滚动分页） | 否 |
| POST | `/reviews/publish/{movieId}` | 发布影评 Body: ReviewDTO | 否 |
| POST | `/reviews/like/{reviewId}` | 点赞/取消 → LikeVO | 否 |
| PUT | `/reviews/{reviewId}` | 修改影评 | 否 |
| DELETE | `/reviews/{reviewId}` | 删除（软删除） | 否 |

**评论 `/reviewComments`**

| 方法 | 路径 | 说明 | 免登录 |
|------|------|------|--------|
| GET | `/reviewComments/list/root/{reviewId}` | 根评论（滚动分页） | 公开 |
| GET | `/reviewComments/list/children/{rootId}` | 子回复（滚动分页） | 公开 |
| GET | `/reviewComments/{id}/target` | 查评论所属影评 → CommentTargetVO | 公开 |
| GET | `/reviewComments/my` | 我的评论（滚动分页） | 否 |
| POST | `/reviewComments/publish/{reviewId}` | 发表评论 Body: ReviewCommentDTO | 否 |
| POST | `/reviewComments/like/{reviewCommentId}` | 点赞/取消 → LikeVO | 否 |
| DELETE | `/reviewComments/{reviewCommentId}` | 删除（软删除） | 否 |

**关注 `/follows`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/follows/list/follower` | 粉丝列表 `?current=` |
| GET | `/follows/list/following` | 关注列表 `?current=` |
| GET | `/follows/or/not/{id}` | 是否已关注 → Boolean |
| POST | `/follows/{id}/{isFollow}` | 关注/取关 |

**消息 `/messages`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/messages/list` | 消息列表 `?current=&type=` |
| GET | `/messages/unread/count` | 未读数 → Long |
| PUT | `/messages/read/{id}` | 单条已读 |
| PUT | `/messages/read/all` | 全部已读 |

消息 type: 0=关注 1=点赞影评 2=评论影评 3=点赞评论 4=回复评论

**上传 `/upload`**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload/avatar` | 上传头像 form-data: `file` |

### 2.2 管理端

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admins/login` | 登录 Body: AdminLoginDTO |
| POST | `/admins/logout` | 登出 |
| GET | `/admins/dashboard` | 数据看板 |
| GET | `/admins/users/list` | 用户列表 |
| GET | `/admins/users/info/{id}` | 用户详情 |
| PUT | `/admins/users/status/{id}` | 封禁/解封 |
| GET | `/admins/movies/list` | 电影列表 |
| POST | `/admins/movies/publish` | 发布电影 |
| PUT | `/admins/movies/update/{id}` | 编辑电影 |
| PUT | `/admins/movies/status/{id}` | 上架/下架 |
| GET | `/admins/reviews/list` | 影评列表 |
| PUT | `/admins/reviews/status/{id}` | 封禁/解封 |
| GET | `/admins/comments/list` | 评论列表 |
| PUT | `/admins/comments/status/{id}` | 封禁/解封 |
| POST | `/admins/upload/poster` | 上传海报 |

---

## 3. 请求/响应参考

### 3.1 统一格式

```json
{ "success": true, "errorMsg": null, "data": ..., "total": 100 }
```

- 普通分页: `data` 为 PageResult `{ total, records }`
- 滚动分页: `data` 为 ScrollResult `{ list, hasMore }`
- 分页参数 `current`（从 1 开始），每页 10 条

### 3.2 核心 DTO

| DTO | 字段 | 使用场景 |
|-----|------|---------|
| `LoginFormDTO` | `phone`, `username`, `code`, `password` | 登录 |
| `RegisterFormDTO` | `username`, `password`, `code`, `email`, `phone`, `confirmPassword` | 注册 |
| `ReviewDTO` | `rating`(1-10), `title`, `content`, `spoiler`(0/1) | 发布/修改影评 |
| `ReviewCommentDTO` | `rootId`(0=一级), `replyUserId`, `content` | 发表评论 |
| `UpdateProfileDTO` | `nickname`, `avatar`, `bio`, `gender` | 修改资料 |
| `UpdatePasswordDTO` | `oldPassword`, `newPassword` | 修改密码 |
| `AdminLoginDTO` | `username`, `password` | 管理员登录 |
| `AdminMovieDTO` | `title`, `originalTitle`, `cover`, `director`, `actors`, `genre`, `region`, `language`, `releaseDate`, `duration`, `summary` | 电影管理 |

### 3.3 核心 VO

**MovieVO**: `id`, `title`, `originalTitle`, `cover`, `director`, `actors`, `genre`, `region`, `language`, `releaseDate`, `duration`, `summary`, `rating`(BigDecimal), `ratingCount`

**ReviewVO**: `id`, `rating`, `title`, `content`, `spoiler`, `userId`, `userName`, `nickName`, `avatar`, `likeCount`, `commentCount`, `isLike`, `canEditAndDelete`, `movieVO`, `createTime`

**ReviewCommentVO**: `id`, `author`(UserDTO), `replyUser`(UserDTO), `rootId`, `content`, `likeCount`, `replyCount`, `isLike`, `canEditAndDelete`, `createTime`

**UserDTO**（嵌入 VO 中）: `id`, `username`, `nickname`, `avatar`

**UserInfo**: `id`, `username`, `nickname`, `avatar`, `bio`, `gender`, `reviewCount`, `followingCount`, `followerCount`

**LikeVO**: `{ like: Boolean, likeCount: Integer }`

**MessageVO**: `id`, `type`, `fromUser`(UserDTO), `content`, `targetType`, `targetId`, `status`(0=未读), `createTime`

**CommentTargetVO**: `{ reviewId, commentId }`

---

## 4. 用户流程

### 4.1 游客

```
首页（热门电影）→ 电影搜索/筛选 → 电影详情 → 影评列表 → 影评详情 → 评论列表
```

游客可完整浏览所有内容，点赞/收藏/评论等操作时引导登录。

### 4.2 登录

```
手机验证码登录: phone → /users/code → 输入验证码 → /users/login/code → token
密码登录: username + password → /users/login/password → token
注册: phone → /users/registerCode → 填写信息 → /users/register → token
```

登录后 Token 存 localStorage，每次请求 Header 带 `Authorization: <token>`。30min TTL，每次请求自动刷新。

### 4.3 已登录用户

```
电影浏览 → 收藏 / 发布影评 / 点赞影评 / 发表评论
个人中心 → 编辑资料（上传头像）→ 我的影评 → 我的评论 → 我的收藏
         → 关注/粉丝列表 → 他人主页 → 关注/取关
消息中心 → 未读角标 → 按类型筛选 → 点击跳转 → 标记已读
```

### 4.4 管理员

```
独立登录页 → 数据看板 → 用户/电影/影评/评论管理（列表+封禁/解封）
                      → 发布/编辑电影 + 上传海报
```

---

## 5. 页面清单

### 5.1 用户端

| 页面 | 路由 | 说明 |
|------|------|------|
| 首页 | `/` | 热门电影 + 搜索入口 |
| 电影搜索 | `/movies` | 关键词搜索 + 分页 |
| 电影详情 | `/movies/:id` | 信息 + 收藏 + 影评列表 |
| 写影评 | `/movies/:id/review/write` | 评分+标题+内容+剧透标记 |
| 影评详情 | `/reviews/:id` | 全文 + 两级评论 |
| 用户主页 | `/users/:id` | 公开信息 + 关注按钮 |
| 登录 | `/login` | 验证码/密码切换 |
| 注册 | `/register` | 注册表单 |
| 个人中心 | `/profile` | 信息 + 统计 |
| 编辑资料 | `/profile/edit` | 修改信息 + 上传头像 |
| 我的影评 | `/profile/reviews` | 滚动分页 |
| 我的评论 | `/profile/comments` | 滚动分页 |
| 我的收藏 | `/profile/favorites` | 电影收藏列表 |
| 关注/粉丝 | `/profile/following` `/profile/followers` | 分页列表 |
| 消息中心 | `/messages` | 列表+筛选+标记已读 |

### 5.2 管理端

| 页面 | 路由 |
|------|------|
| 登录 | `/admin/login` |
| 看板 | `/admin/dashboard` |
| 用户管理 | `/admin/users` |
| 电影管理 | `/admin/movies` |
| 影评管理 | `/admin/reviews` |
| 评论管理 | `/admin/comments` |

---

## 6. 消息跳转逻辑

| type | 含义 | 跳转 |
|------|------|------|
| 0 | 关注 | `/users/{fromUserId}` |
| 1 | 点赞影评 | `/reviews/{targetId}` |
| 2 | 评论影评 | `/reviews/{targetId}` |
| 3 | 点赞评论 | GET `/reviewComments/{targetId}/target` → `/reviews/{reviewId}` |
| 4 | 回复评论 | 同 type=3 |

---

## 7. 技术建议

| 层面 | 方案 |
|------|------|
| 框架 | Vue 3 + Vite |
| 路由 | Vue Router 4 |
| 状态管理 | Pinia（全局: 用户/auth/未读数；页面级: 组件内状态） |
| HTTP | Axios + 拦截器（自动带 Token，401 跳登录页） |
| UI 库 | Element Plus |
| 服务端缓存 | TanStack Query / Vue Query（电影详情 30min、热门 10min） |

### 乐观更新建议

点赞、收藏、关注、删除 → 先更新 UI，请求失败时回滚 + Toast 提示。

### 分页交互

- 普通分页（电影搜索、关注/粉丝）→ 分页器
- 滚动分页（热门影评、评论列表、个人影评/评论）→ 下拉加载更多 + `hasMore` 判断
