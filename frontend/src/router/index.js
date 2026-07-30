import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getAdminToken } from '../utils/auth'

const routes = [
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('../views/Home.vue') },
      { path: 'movies', name: 'Movies', component: () => import('../views/MovieList.vue') },
      { path: 'reviews/search', name: 'ReviewSearch', component: () => import('../views/ReviewSearch.vue') },
      { path: 'movies/:id', name: 'MovieDetail', component: () => import('../views/MovieDetail.vue') },
      { path: 'reviews/:id', name: 'ReviewDetail', component: () => import('../views/ReviewDetail.vue') },
      { path: 'users/:id', name: 'UserProfile', component: () => import('../views/UserProfile.vue') },
      { path: 'messages', name: 'Messages', component: () => import('../views/Messages.vue'), meta: { requiresAuth: true } },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },
      { path: 'profile/edit', name: 'ProfileEdit', component: () => import('../views/ProfileEdit.vue'), meta: { requiresAuth: true } },
      { path: 'profile/reviews', name: 'MyReviews', component: () => import('../views/MyReviews.vue'), meta: { requiresAuth: true } },
      { path: 'profile/comments', name: 'MyComments', component: () => import('../views/MyComments.vue'), meta: { requiresAuth: true } },
      { path: 'profile/following', name: 'Following', component: () => import('../views/FollowList.vue'), meta: { requiresAuth: true } },
      { path: 'profile/followers', name: 'Followers', component: () => import('../views/FollowList.vue'), meta: { requiresAuth: true } },
      { path: 'profile/favorites', name: 'MyFavorites', component: () => import('../views/MyFavorites.vue'), meta: { requiresAuth: true } },
      { path: 'profile/password', name: 'ChangePassword', component: () => import('../views/ChangePassword.vue'), meta: { requiresAuth: true } },
    ],
  },
  {
    path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { guest: true },
  },
  {
    path: '/register', name: 'Register', component: () => import('../views/Register.vue'), meta: { guest: true },
  },
  {
    path: '/reset-password', name: 'ResetPassword', component: () => import('../views/ResetPassword.vue'), meta: { guest: true },
  },
  {
    path: '/admin/login', name: 'AdminLogin', component: () => import('../views/admin/AdminLogin.vue'), meta: { guest: true },
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../views/admin/Dashboard.vue') },
      { path: 'users', name: 'AdminUsers', component: () => import('../views/admin/UserManagement.vue') },
      { path: 'movies', name: 'AdminMovies', component: () => import('../views/admin/MovieManagement.vue') },
      { path: 'reviews', name: 'AdminReviews', component: () => import('../views/admin/ReviewManagement.vue') },
      { path: 'comments', name: 'AdminComments', component: () => import('../views/admin/CommentManagement.vue') },
      // 其他管理页面后续添加
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const hasToken = !!getToken()
  if (to.meta.guest && hasToken) {
    return { name: 'Home' }
  }
  if (to.meta.requiresAuth && !hasToken) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !getAdminToken()) {
    return { name: 'AdminLogin' }
  }
})

export default router
