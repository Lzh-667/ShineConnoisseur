import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '../../utils/auth'
import { loginByPassword, loginByCode, getCurrentUser, getUserInfo, getUnreadCount, logout as logoutApi } from '../../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null,
    unreadCount: 0,
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    userId: (state) => state.userInfo?.id ?? null,
  },

  actions: {
    async loginByPasswordAction(credentials) {
      const res = await loginByPassword(credentials)
      this.token = res.data
      setToken(this.token)
      try { const me = await getCurrentUser(); this.userInfo = me.data } catch {}
    },

    async loginByCodeAction(credentials) {
      const res = await loginByCode(credentials)
      this.token = res.data
      setToken(this.token)
      try { const me = await getCurrentUser(); this.userInfo = me.data } catch {}
    },

    async fetchUserInfo(userId) {
      const res = await getUserInfo(userId)
      this.userInfo = res.data
    },

    async fetchUnreadCount() {
      if (!this.isLoggedIn) { this.unreadCount = 0; return }
      try {
        const res = await getUnreadCount()
        this.unreadCount = res.data ?? 0
      } catch { /* 静默失败 */ }
    },

    decreaseUnread(n) {
      this.unreadCount = Math.max(0, this.unreadCount - n)
    },

    async init() {
      if (this.token && !this.userInfo) {
        try { const me = await getCurrentUser(); this.userInfo = me.data } catch {}
      }
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        this.token = ''
        this.userInfo = null
        this.unreadCount = 0
        removeToken()
      }
    },
  },
})
