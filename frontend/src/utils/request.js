import axios from 'axios'
import { getToken, removeToken, getAdminToken, removeAdminToken } from './auth'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

service.interceptors.request.use(
  (config) => {
    const isAdmin = config.url.startsWith('/admins')
    const token = isAdmin ? getAdminToken() : getToken()
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error) => Promise.reject(error),
)

function redirectToLogin(path, admin = false) {
  const currentPath = path || window.location.pathname + window.location.search
  const skipPaths = ['/login', '/register', '/reset-password', '/admin/login']
  const query = skipPaths.some(p => currentPath.startsWith(p))
    ? undefined
    : { redirect: currentPath }

  const r = window.__router
  if (r) {
    if (admin) {
      r.push({ name: 'AdminLogin', query })
    } else {
      r.push({ name: 'Login', query })
    }
  }
}

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.success === false) {
      ElMessage.error(res.errorMsg || '请求失败')
      return Promise.reject(new Error(res.errorMsg || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const { status } = error.response
      if (status === 401) {
        const isAdmin = error.config.url.startsWith('/admins')
        if (isAdmin) {
          const hadToken = !!getAdminToken()
          removeAdminToken()
          ElMessage.error(hadToken ? '管理员登录已过期，请重新登录' : '请先登录')
          redirectToLogin(error.config._redirectPath, true)
        } else {
          const hadToken = !!getToken()
          removeToken()
          // 有 token 表示过期，无 token 表示访客访问了需登录的接口
          if (hadToken) {
            ElMessage.error('登录已过期，请重新登录')
          }
          // 访客不发错误提示，由组件自行决定是否跳转
          redirectToLogin(error.config._redirectPath)
        }
      } else {
        ElMessage.error(error.response.data?.errorMsg || '网络错误')
      }
    } else {
      ElMessage.error('网络连接异常')
    }
    return Promise.reject(error)
  },
)

export default service
