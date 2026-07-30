<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getCurrentUser } from '../api'
import { EditPen, Lock, Star, Document, ChatDotSquare, User, Avatar } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)

async function load() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  loading.value = true
  try {
    const res = await getCurrentUser()
    userStore.userInfo = res.data
  } finally { loading.value = false }
}

const menuItems = [
  { label: '编辑资料', path: '/profile/edit', icon: EditPen },
  { label: '修改密码', path: '/profile/password', icon: Lock },
  { label: '我的收藏', path: '/profile/favorites', icon: Star },
  { label: '我的影评', path: '/profile/reviews', icon: Document },
  { label: '我的评论', path: '/profile/comments', icon: ChatDotSquare },
  { label: '我的关注', path: '/profile/following', icon: User },
  { label: '我的粉丝', path: '/profile/followers', icon: Avatar },
]

onMounted(load)
</script>

<template>
  <div class="profile-page">
    <div v-if="loading" class="loading-text">加载中...</div>
    <template v-else-if="userStore.userInfo">
      <div class="profile-header">
        <el-avatar :size="80" :src="userStore.userInfo.avatar" class="profile-avatar" />
        <div class="profile-info">
          <h2>{{ userStore.userInfo.nickname || userStore.userInfo.username }}</h2>
          <p class="bio">{{ userStore.userInfo.bio || '这个人很懒，什么都没写' }}</p>
          <p class="username">@{{ userStore.userInfo.username }}</p>
        </div>
      </div>

      <div class="stats-row">
        <div class="stat">
          <span class="num">{{ userStore.userInfo.reviewCount ?? 0 }}</span>
          <span class="label">影评</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat">
          <span class="num">{{ userStore.userInfo.followingCount ?? 0 }}</span>
          <span class="label">关注</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat">
          <span class="num">{{ userStore.userInfo.followerCount ?? 0 }}</span>
          <span class="label">粉丝</span>
        </div>
      </div>

      <div class="menu-list">
        <div v-for="m in menuItems" :key="m.path" class="menu-item" @click="router.push(m.path)">
          <span class="menu-icon">
            <el-icon :size="18"><component :is="m.icon" /></el-icon>
          </span>
          <span class="menu-label">{{ m.label }}</span>
          <span class="menu-arrow">›</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile-page { max-width: 560px; margin: 0 auto; padding: 48px 24px 80px; }

.profile-header { display: flex; align-items: center; gap: 24px; margin-bottom: 28px; }
.profile-avatar { border: 2px solid rgba(245,175,25,.2); flex-shrink: 0; }
.profile-info h2 { font-size: 22px; color: #f5af19; margin: 0; }
.bio { font-size: 14px; color: rgba(255,255,255,.45); margin: 6px 0; line-height: 1.5; }
.username { font-size: 13px; color: rgba(255,255,255,.25); margin: 0; }

.stats-row {
  display: flex; align-items: center; gap: 0; margin-bottom: 32px;
  padding: 20px 24px; background: rgba(255,255,255,.02);
  border: 1px solid rgba(255,255,255,.05); border-radius: 14px;
  justify-content: space-around;
}
.stat { text-align: center; flex: 1; }
.stat-divider { width: 1px; height: 28px; background: rgba(255,255,255,.06); }
.num { display: block; font-size: 22px; font-weight: 700; color: rgba(255,255,255,.85); font-variant-numeric: tabular-nums; }
.label { font-size: 11px; color: rgba(255,255,255,.3); margin-top: 4px; text-transform: uppercase; letter-spacing: .5px; }

.menu-list { display: flex; flex-direction: column; gap: 2px; }
.menu-item {
  display: flex; align-items: center; gap: 14px; padding: 14px 18px;
  background: rgba(255,255,255,.015); border-radius: 12px; cursor: pointer;
  transition: all .2s;
}
.menu-item:hover { background: rgba(255,255,255,.04); }
.menu-icon {
  width: 38px; height: 38px; border-radius: 10px;
  background: rgba(255,255,255,.04); display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,.4); flex-shrink: 0; transition: all .2s;
}
.menu-item:hover .menu-icon { background: rgba(245,175,25,.1); color: #f5af19; }
.menu-label { flex: 1; font-size: 15px; color: rgba(255,255,255,.7); }
.menu-arrow { color: rgba(255,255,255,.15); font-size: 20px; }

.loading-text { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
</style>
