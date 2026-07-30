<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getFollowingList, getFollowerList, toggleFollow, isFollowing } from '../api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isFollowers = computed(() => route.name === 'Followers')
const title = computed(() => isFollowers.value ? '我的粉丝' : '我的关注')

const loading = ref(false)
const list = ref([])
const current = ref(1)
const total = ref(0)
const pageSize = 10

async function load() {
  loading.value = true
  try {
    const api = isFollowers.value ? getFollowerList : getFollowingList
    const res = await api(current.value)
    const users = res.data?.records ?? []
    total.value = res.data?.total ?? 0

    // 关注列表默认全部已关注；粉丝列表需查询是否互关
    if (isFollowers.value) {
      const results = await Promise.all(users.map(u => isFollowing(u.id).catch(() => ({ data: false }))))
      users.forEach((u, i) => u._following = results[i]?.data === true)
    } else {
      users.forEach(u => u._following = true)
    }
    list.value = users
  } finally { loading.value = false }
}

function onPageChange(p) { current.value = p; load() }

async function handleToggleFollow(user, index) {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  const newState = !user._following
  try {
    await toggleFollow(user.id, newState)
    list.value[index]._following = newState
    if (userStore.userInfo) {
      userStore.userInfo.followingCount = (userStore.userInfo.followingCount ?? 0) + (newState ? 1 : -1)
    }
    ElMessage.success(newState ? '已关注' : '已取消关注')
  } catch {}
}

onMounted(load)
watch(() => route.name, () => { current.value = 1; load() })
</script>

<template>
  <div class="list-page">
    <h2 class="page-title">{{ title }}</h2>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="list.length === 0" class="empty">暂无数据</div>
    <template v-else>
      <div class="user-list">
        <div v-for="(u, i) in list" :key="u.id" class="user-item" @click="router.push(`/users/${u.id}`)">
          <el-avatar :size="44" :src="u.avatar" />
          <div class="user-body">
            <span class="user-name">{{ u.nickname || u.username }}</span>
            <span class="user-bio">{{ u.bio || '' }}</span>
          </div>
          <el-button
            :type="u._following ? 'default' : 'primary'" size="small" round
            @click.stop="handleToggleFollow(u, i)"
          >
            {{ u._following ? '已关注' : '关注' }}
          </el-button>
        </div>
      </div>
      <el-pagination v-if="total > pageSize" class="pager" background layout="prev, pager, next" :total="total" :page-size="pageSize" :current-page="current" @current-change="onPageChange" />
    </template>
  </div>
</template>

<style scoped>
.list-page { max-width: 680px; margin: 0 auto; padding: 40px 24px 80px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0 0 24px; }

.user-list { display: flex; flex-direction: column; gap: 4px; }
.user-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; background: rgba(255,255,255,.02); border-radius: 10px;
  cursor: pointer; transition: background .2s;
}
.user-item:hover { background: rgba(255,255,255,.06); }
.user-body { flex: 1; min-width: 0; }
.user-name { font-size: 14px; font-weight: 600; color: rgba(255,255,255,.8); }
.user-bio { font-size: 12px; color: rgba(255,255,255,.35); display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.pager { margin-top: 24px; justify-content: center; }
</style>
