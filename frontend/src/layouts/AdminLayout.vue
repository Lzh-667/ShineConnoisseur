<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '../stores/modules/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, User, Film, StarFilled, ChatDotSquare, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()

const navItems = [
  { path: '/admin', label: '控制台', icon: DataAnalysis },
  { path: '/admin/users', label: '用户管理', icon: User },
  { path: '/admin/movies', label: '电影管理', icon: Film },
  { path: '/admin/reviews', label: '影评管理', icon: StarFilled },
  { path: '/admin/comments', label: '评论管理', icon: ChatDotSquare },
]

const activeItem = computed(() => navItems.find(i => {
  if (i.path === '/admin') return route.path === '/admin'
  return route.path.startsWith(i.path)
}))

function isActive(path) {
  if (path === '/admin') return route.path === '/admin'
  return route.path.startsWith(path)
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出管理后台吗？', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    adminStore.logout().then(() => {
      ElMessage.success('已退出管理后台')
      router.push('/admin/login')
    })
  }).catch(() => {})
}
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-brand" @click="router.push('/admin')">
        <div class="brand-logo">
          <span class="logo-icon">✦</span>
        </div>
        <div class="brand-text">
          <span class="brand-name">光影鉴赏家</span>
          <span class="brand-sub">管理后台</span>
        </div>
      </div>
      <nav class="sidebar-nav">
        <div
          v-for="item in navItems" :key="item.path"
          class="nav-item" :class="{ active: isActive(item.path) }"
          @click="router.push(item.path)"
        >
          <span class="nav-indicator" />
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ item.label }}</span>
        </div>
      </nav>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <span class="topbar-title">{{ activeItem?.label || '管理后台' }}</span>
        <button class="logout-btn" @click="handleLogout">
          <el-icon :size="16"><SwitchButton /></el-icon>
          退出
        </button>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; background: #0b0b14; }

/* ===== Sidebar ===== */
.sidebar {
  width: 240px; background: #0d0d1a;
  border-right: 1px solid rgba(255,255,255,.05);
  display: flex; flex-direction: column; flex-shrink: 0;
}

.sidebar-brand {
  display: flex; align-items: center; gap: 12px; padding: 24px 20px 20px;
  cursor: pointer; border-bottom: 1px solid rgba(245,175,25,.15);
}
.brand-logo {
  width: 40px; height: 40px; border-radius: 12px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.logo-icon { font-size: 20px; color: #fff; }
.brand-text { display: flex; flex-direction: column; }
.brand-name { font-size: 16px; font-weight: 700; color: #f5af19; }
.brand-sub { font-size: 11px; color: rgba(255,255,255,.25); margin-top: 1px; }

.sidebar-nav { padding: 16px 12px; display: flex; flex-direction: column; gap: 2px; flex: 1; }
.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 11px 14px; margin-bottom: 2px;
  border-radius: 10px; cursor: pointer; transition: all .2s; color: rgba(255,255,255,.4); font-size: 14px; position: relative; overflow: hidden;
}
.nav-indicator {
  position: absolute; left: 0; top: 50%; transform: translateY(-50%);
  width: 3px; height: 0; border-radius: 0 3px 3px 0;
  background: #f5af19; transition: height .25s ease;
}
.nav-item:hover { background: rgba(255,255,255,.03); color: rgba(255,255,255,.7); }
.nav-item.active { background: rgba(245,175,25,.08); color: #f5af19; }
.nav-item.active .nav-indicator { height: 20px; }
.nav-icon { font-size: 18px; flex-shrink: 0; }
.nav-label { font-weight: 500; }

/* ===== Main Area ===== */
.main-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }

.topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 36px;
  background: rgba(13,13,26,.7); backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255,255,255,.05);
  position: sticky; top: 0; z-index: 10;
}
.topbar-title { font-size: 17px; font-weight: 600; color: rgba(255,255,255,.8); }

.logout-btn {
  display: flex; align-items: center; gap: 5px; padding: 7px 14px;
  background: rgba(255,255,255,.04); border: none;
  border-radius: 8px; color: rgba(255,255,255,.3); font-size: 13px;
  cursor: pointer; transition: all .2s;
}
.logout-btn:hover { color: #f56c6c; background: rgba(245,108,108,.1); }

.content { flex: 1; padding: 32px 36px; overflow-y: auto; }
</style>
