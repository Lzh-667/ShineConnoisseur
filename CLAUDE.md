# ShineConnoisseur（光影鉴赏家）

电影影评社区平台。Spring Boot 3 后端 + Vue 3 前端。

## 功能

用户注册登录（手机验证码 + 密码）、电影浏览/搜索/收藏、影评发布/点赞/热门排行、两级评论、关注关系、站内消息通知（RabbitMQ 异步）、管理后台。

## 项目结构

```
backend/ShineConnoisseur/    # Spring Boot 3 后端，端口 8080
frontend/                    # Vue 3 前端，Vite + Element Plus
docs/                        # 开发文档
```

## 技术栈

**后端**: Spring Boot 3.5.3 + MyBatis-Plus 3.5.12 + MySQL 8.0 + Redis (Lettuce) + RabbitMQ + Elasticsearch
**前端**: Vue 3 + Vite + Pinia + Axios + Element Plus

## 开发规范

- 前端通过 REST API 调用后端，返回格式统一为 `{success, errorMsg, data, total}`
- 不随意新增后端接口，根据 VO 设计页面数据结构
- 管理端接口路由以 `/admins` 开头，Controller 放 `controller/admin/`
- 依赖注入用 `@Resource`，不用 `@Autowired`
- 对象转换用 Hutool `BeanUtil.copyProperties()`
