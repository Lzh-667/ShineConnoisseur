<script setup>
import { watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

let pollTimer = null

const navItems = [
  { path: '/', label: '首页' },
  { path: '/movies', label: '电影' },
  { path: '/reviews/search', label: '影评' },
]

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout().then(() => {
      ElMessage.success('已退出登录')
      router.push('/login')
    })
  }).catch(() => {})
}

watch(() => userStore.isLoggedIn, (val) => {
  if (val) {
    userStore.fetchUnreadCount()
    pollTimer = setInterval(() => userStore.fetchUnreadCount(), 30000)
  } else {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }
})

onMounted(() => {
  if (userStore.isLoggedIn) {
    userStore.init()
    userStore.fetchUnreadCount()
    pollTimer = setInterval(() => userStore.fetchUnreadCount(), 30000)
  }
})

onUnmounted(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
})
</script>

<template>
  <div class="main-layout">
    <header class="main-header">
      <div class="header-inner">
        <div class="header-left">
          <router-link to="/" class="logo">
            <span class="logo-dot"></span>
            光影鉴赏家
          </router-link>
          <nav class="nav-links">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ active: isActive(item.path) }"
            >
              {{ item.label }}
            </router-link>
          </nav>
        </div>

        <span class="header-slogan">发现电影之美，记录你的光影瞬间</span>

        <div class="header-right">
          <template v-if="userStore.isLoggedIn">
            <router-link to="/messages" class="msg-btn" :class="{ active: isActive('/messages') }">
              <el-icon :size="20"><Bell /></el-icon>
              <span v-if="userStore.unreadCount > 0" class="msg-badge">{{ userStore.unreadCount > 99 ? '99+' : userStore.unreadCount }}</span>
            </router-link>

            <div class="user-trigger" @click="router.push('/profile')">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar" />
              <span class="user-name">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '用户' }}</span>
            </div>
            <button class="logout-btn" title="退出登录" @click="handleLogout">
              <el-icon :size="18"><SwitchButton /></el-icon>
            </button>
          </template>
          <template v-else>
            <el-button class="header-login-btn" size="small" round @click="router.push('/login')">登录</el-button>
            <el-button size="small" round @click="router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main-content">
      <router-view v-slot="{ Component }">
        <keep-alive :include="['MovieList', 'Home', 'MyFavorites']">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.main-layout { display: flex; flex-direction: column; min-height: 100vh; }

.main-header {
  position: sticky; top: 0; z-index: 100;
  background: rgba(15, 15, 26, .92); backdrop-filter: blur(16px) saturate(180%);
  border-bottom: 1px solid rgba(255,255,255,.06);
}

.header-inner {
  display: flex; justify-content: space-between; align-items: center;
  height: 64px; padding: 0 40px; max-width: 1280px; margin: 0 auto; width: 100%;
}

.header-left { display: flex; align-items: center; gap: 36px; }

.logo {
  display: flex; align-items: center; gap: 8px;
  font-size: 18px; font-weight: 700; color: #f5af19; text-decoration: none; white-space: nowrap;
}
.logo-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: linear-gradient(135deg, #f5af19, #f12711);
}

.nav-links { display: flex; gap: 2px; }

.nav-item {
  position: relative; color: rgba(255,255,255,.55);
  text-decoration: none; padding: 8px 18px; border-radius: 10px;
  font-size: 14px; font-weight: 500; transition: all .2s;
}
.nav-item:hover { color: rgba(255,255,255,.85); background: rgba(255,255,255,.04); }
.nav-item.active { color: #f5af19; background: rgba(245,175,25,.08); }

.header-right { display: flex; align-items: center; gap: 14px; }

.msg-btn {
  position: relative; text-decoration: none; color: rgba(255,255,255,.45);
  padding: 6px; border-radius: 8px; transition: all .2s; display: flex; align-items: center;
}
.msg-btn:hover { color: rgba(255,255,255,.7); background: rgba(255,255,255,.06); }
.msg-btn.active { color: #f5af19; background: rgba(245,175,25,.08); }

.msg-badge {
  position: absolute; top: 0; right: -2px;
  background: #f56c6c; color: #fff; font-size: 10px;
  min-width: 18px; height: 18px; line-height: 18px; text-align: center;
  border-radius: 9px; padding: 0 4px; font-weight: 600;
}

.user-trigger {
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  padding: 4px 10px 4px 4px; border-radius: 10px; transition: background .2s;
}
.user-trigger:hover { background: rgba(255,255,255,.06); }
.user-name { font-size: 14px; color: rgba(255,255,255,.8); max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.logout-btn {
  background: none; border: none; color: rgba(255,255,255,.3); cursor: pointer;
  padding: 6px; border-radius: 8px; transition: all .2s; display: flex; align-items: center;
}
.logout-btn:hover { color: #f56c6c; background: rgba(245,108,108,.08); }

.header-login-btn { --el-button-bg-color: #f5af19; --el-button-border-color: #f5af19; font-weight: 600; }

.main-content { flex: 1; }

.header-slogan {
  font-size: 13px; font-weight: 300; color: rgba(245,175,25,.4);
  letter-spacing: 2px; white-space: nowrap; font-style: italic;
}
</style>
