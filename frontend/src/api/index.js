import request from '../utils/request'

// ========== 用户认证 ==========

/** 发送登录验证码 */
export function sendLoginCode(phone) {
  return request({ url: '/users/code', method: 'post', params: { phone } })
}

/** 验证码登录 */
export function loginByCode(data) {
  return request({ url: '/users/login/code', method: 'post', data })
}

/** 密码登录 */
export function loginByPassword(data) {
  return request({ url: '/users/login/password', method: 'post', data })
}

/** 发送注册验证码 */
export function sendRegisterCode(phone) {
  return request({ url: '/users/registerCode', method: 'post', params: { phone } })
}

/** 注册 */
export function register(data) {
  return request({ url: '/users/register', method: 'post', data })
}

/** 获取用户信息 */
export function getUserInfo(id) {
  return request({ url: `/users/info/${id}`, method: 'get' })
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return request({ url: '/users/me', method: 'get' })
}

/** 登出 */
export function logout() {
  return request({ url: '/users/logout', method: 'post' })
}

/** 修改个人资料 */
export function updateProfile(data) {
  return request({ url: '/users/profile', method: 'put', data })
}

/** 修改密码 */
export function updatePassword(data) {
  return request({ url: '/users/password', method: 'put', data })
}

/** 发送重置密码验证码 */
export function sendResetCode(phone) {
  return request({ url: '/users/resetCode', method: 'post', params: { phone } })
}

/** 重置密码 */
export function resetPassword(data) {
  return request({ url: '/users/resetPassword', method: 'post', data })
}

// ========== 影评 ==========

/** 搜索影评（ES 全文搜索） */
export function searchReviews(keyword, current = 1, spoiler) {
  const params = { keyword, current }
  if (spoiler !== undefined && spoiler !== null) params.spoiler = spoiler
  return request({ url: '/reviews/search', method: 'get', params })
}

/** 热门影评（滚动分页） */
export function getHotReviews(current = 1) {
  return request({ url: '/reviews/hot', method: 'get', params: { current } })
}

/** 电影下的影评列表 */
export function getMovieReviews(movieId, current = 1) {
  return request({ url: `/reviews/movie/${movieId}`, method: 'get', params: { current } })
}

/** 我的影评 */
export function getMyReviews(current = 1) {
  return request({ url: '/reviews/my', method: 'get', params: { current } })
}

/** 某用户的影评 */
export function getUserReviews(userId, current = 1) {
  return request({ url: `/reviews/user/${userId}`, method: 'get', params: { current } })
}

/** 影评详情 */
export function getReviewDetail(reviewId) {
  return request({ url: `/reviews/${reviewId}`, method: 'get' })
}

/** 点赞/取消点赞影评 */
export function likeReview(reviewId) {
  return request({ url: `/reviews/like/${reviewId}`, method: 'post' })
}

/** 删除影评 */
export function deleteReview(reviewId) {
  return request({ url: `/reviews/${reviewId}`, method: 'delete' })
}

/** 修改影评 */
export function updateReview(reviewId, data) {
  return request({ url: `/reviews/${reviewId}`, method: 'put', data })
}

/** 发布影评 */
export function publishReview(movieId, data) {
  return request({ url: `/reviews/publish/${movieId}`, method: 'post', data })
}

/** 发布评论 */
export function publishComment(reviewId, data) {
  return request({ url: `/reviewComments/publish/${reviewId}`, method: 'post', data })
}

// ========== 评论 ==========

/** 我的评论 */
export function getMyComments(current = 1) {
  return request({ url: '/reviewComments/my', method: 'get', params: { current } })
}

/** 根评论列表 */
export function getRootComments(reviewId, current = 1) {
  return request({ url: `/reviewComments/list/root/${reviewId}`, method: 'get', params: { current } })
}

/** 子回复列表 */
export function getChildComments(rootId, current = 1) {
  return request({ url: `/reviewComments/list/children/${rootId}`, method: 'get', params: { current } })
}

/** 点赞/取消点赞评论 */
export function likeComment(commentId) {
  return request({ url: `/reviewComments/like/${commentId}`, method: 'post' })
}

/** 删除评论 */
export function deleteComment(commentId) {
  return request({ url: `/reviewComments/${commentId}`, method: 'delete' })
}

/** 查询评论所属影评（消息跳转用） */
export function getCommentTarget(commentId) {
  return request({ url: `/reviewComments/${commentId}/target`, method: 'get' })
}

// ========== 消息通知 ==========

/** 消息列表 */
export function getMessages(current = 1, type) {
  const params = { current }
  if (type !== undefined && type !== null && type !== '') params.type = type
  return request({ url: '/messages/list', method: 'get', params })
}

/** 未读消息数 */
export function getUnreadCount() {
  return request({ url: '/messages/unread/count', method: 'get' })
}

/** 标记单条已读 */
export function markMessageRead(id) {
  return request({ url: `/messages/read/${id}`, method: 'put' })
}

/** 全部已读 */
export function markAllMessagesRead() {
  return request({ url: '/messages/read/all', method: 'put' })
}

// ========== 关注 ==========

/** 粉丝列表 */
export function getFollowerList(current = 1) {
  return request({ url: '/follows/list/follower', method: 'get', params: { current } })
}

/** 关注列表 */
export function getFollowingList(current = 1) {
  return request({ url: '/follows/list/following', method: 'get', params: { current } })
}

/** 关注/取关 */
export function toggleFollow(userId, isFollow) {
  return request({ url: `/follows/${userId}/${isFollow}`, method: 'post' })
}

/** 是否已关注 */
export function isFollowing(userId) {
  return request({ url: `/follows/or/not/${userId}`, method: 'get' })
}

// ========== 电影 ==========

/** 搜索电影（ES 全文搜索） */
export function searchMovies(keyword, current = 1, genre, region) {
  return request({ url: '/movies/search', method: 'get', params: { keyword, current, genre, region } })
}

/** 热门电影 */
export function getHotMovies(current = 1) {
  return request({ url: '/movies/hot', method: 'get', params: { current } })
}

/** 电影列表 */
export function getMovieList(params) {
  return request({ url: '/movies/list', method: 'get', params })
}

/** 电影详情 */
export function getMovieDetail(movieId) {
  return request({ url: `/movies/${movieId}`, method: 'get' })
}

/** 是否已收藏 */
export function checkFavorite(movieId) {
  return request({ url: `/movies/or/not/${movieId}`, method: 'get' })
}

/** 收藏/取消收藏 */
export function toggleFavorite(movieId, isFavorite) {
  return request({ url: `/movies/favorite/${movieId}/${isFavorite}`, method: 'post' })
}

/** 收藏电影列表 */
export function getFavoriteMovies(current = 1) {
  return request({ url: '/movies/favorite', method: 'get', params: { current } })
}

// ========== 文件上传 ==========

/** 上传头像 */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ url: '/upload/avatar', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 上传海报 */
export function uploadPoster(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ url: '/admins/upload/poster', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}
