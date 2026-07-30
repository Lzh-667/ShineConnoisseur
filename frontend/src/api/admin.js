import request from '../utils/request'

/** 管理员登录 */
export function adminLogin(data) {
  return request({ url: '/admins/login', method: 'post', data })
}

/** 管理员登出 */
export function adminLogout() {
  return request({ url: '/admins/logout', method: 'post' })
}

/** 控制台统计 */
export function getDashboard() {
  return request({ url: '/admins/dashboard', method: 'get' })
}

/** 用户列表 */
export function getAdminUsers(current = 1) {
  return request({ url: '/admins/users/list', method: 'get', params: { current } })
}

/** 用户详情 */
export function getAdminUserInfo(id) {
  return request({ url: `/admins/users/info/${id}`, method: 'get' })
}

/** 切换用户状态 */
export function toggleUserStatus(id) {
  return request({ url: `/admins/users/status/${id}`, method: 'put' })
}

/** 电影列表 */
export function getAdminMovies(current = 1) {
  return request({ url: '/admins/movies/list', method: 'get', params: { current } })
}

/** 发布电影 */
export function publishMovie(data) {
  return request({ url: '/admins/movies/publish', method: 'post', data })
}

/** 更新电影 */
export function updateMovie(id, data) {
  return request({ url: `/admins/movies/update/${id}`, method: 'put', data })
}

/** 切换电影状态 */
export function toggleMovieStatus(id) {
  return request({ url: `/admins/movies/status/${id}`, method: 'put' })
}

/** 影评列表 */
export function getAdminReviews(current = 1) {
  return request({ url: '/admins/reviews/list', method: 'get', params: { current } })
}

/** 切换影评状态 */
export function toggleReviewStatus(id) {
  return request({ url: `/admins/reviews/status/${id}`, method: 'put' })
}

/** 评论列表 */
export function getAdminComments(current = 1) {
  return request({ url: '/admins/comments/list', method: 'get', params: { current } })
}

/** 切换评论状态 */
export function toggleCommentStatus(id) {
  return request({ url: `/admins/comments/status/${id}`, method: 'put' })
}
