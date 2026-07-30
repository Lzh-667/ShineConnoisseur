import { defineStore } from 'pinia'
import { getAdminToken, setAdminToken, removeAdminToken } from '../../utils/auth'
import { adminLogin, adminLogout } from '../../api/admin'

export const useAdminStore = defineStore('admin', {
  state: () => ({
    token: getAdminToken() || '',
    adminInfo: null,
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
  },

  actions: {
    async login(credentials) {
      const res = await adminLogin(credentials)
      this.token = res.data
      setAdminToken(this.token)
      this.adminInfo = { username: credentials.username }
    },

    async logout() {
      try {
        await adminLogout()
      } finally {
        this.token = ''
        this.adminInfo = null
        removeAdminToken()
      }
    },

    init() {
      this.token = getAdminToken() || ''
    },
  },
})
