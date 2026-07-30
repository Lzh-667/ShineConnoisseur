<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getUserInfo, toggleFollow, isFollowing, getUserReviews } from '../api'
import ReviewCard from '../components/ReviewCard.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const info = ref(null)
const loading = ref(false)
const following = ref(false)
const followLoading = ref(false)

const reviews = ref([])
const reviewLoading = ref(false)
const reviewCurrent = ref(1)
const reviewHasMore = ref(false)

const isSelf = () => userStore.userInfo?.id === info.value?.id

async function load() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getUserInfo(Number(id))
    info.value = res.data
    if (userStore.isLoggedIn && !isSelf()) {
      const r = await isFollowing(Number(id))
      following.value = r.data === true
    }
    loadReviews()
  } finally { loading.value = false }
}

async function loadReviews(page = 1) {
  reviewLoading.value = true
  try {
    const res = await getUserReviews(info.value.id, page)
    const list = res.data?.list ?? []
    if (page === 1) reviews.value = list
    else reviews.value.push(...list)
    reviewHasMore.value = res.data?.hasMore ?? false
    reviewCurrent.value = page
  } finally { reviewLoading.value = false }
}

function loadMoreReviews() {
  if (reviewHasMore.value) loadReviews(reviewCurrent.value + 1)
}

async function handleToggleFollow() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  followLoading.value = true
  try {
    await toggleFollow(info.value.id, !following.value)
    following.value = !following.value
    info.value.followerCount = (info.value.followerCount ?? 0) + (following.value ? 1 : -1)
    if (userStore.userInfo) {
      userStore.userInfo.followingCount = (userStore.userInfo.followingCount ?? 0) + (following.value ? 1 : -1)
    }
    ElMessage.success(following.value ? '已关注' : '已取消关注')
  } finally { followLoading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="profile-page">
    <div v-if="loading" class="loading-text">加载中...</div>
    <template v-else-if="info">
      <div class="profile-header">
        <el-avatar :size="80" :src="info.avatar" />
        <div class="profile-info">
          <h2>{{ info.nickname || info.username }}</h2>
          <p class="bio">{{ info.bio || '这个人很懒，什么都没写' }}</p>
          <p class="username">@{{ info.username }}</p>
        </div>
        <div v-if="!isSelf()" class="follow-area">
          <el-button
            :type="following ? 'default' : 'primary'" size="small" round
            :loading="followLoading" @click="handleToggleFollow"
          >
            {{ following ? '已关注' : '关注' }}
          </el-button>
        </div>
      </div>

      <div class="stats-row">
        <div class="stat"><span class="num">{{ info.reviewCount ?? 0 }}</span><span class="label">影评</span></div>
        <div class="stat"><span class="num">{{ info.followingCount ?? 0 }}</span><span class="label">关注</span></div>
        <div class="stat"><span class="num">{{ info.followerCount ?? 0 }}</span><span class="label">粉丝</span></div>
      </div>

      <div class="reviews-section">
        <h3 class="section-title">TA的影评</h3>
        <div v-if="reviewLoading && reviews.length === 0" class="loading-text">加载中...</div>
        <template v-else-if="reviews.length > 0">
          <ReviewCard v-for="r in reviews" :key="r.id" :review="r" />
          <div v-if="reviewHasMore" class="load-more" @click="loadMoreReviews">
            {{ reviewLoading ? '加载中...' : '加载更多' }}
          </div>
        </template>
        <div v-else class="empty-text">暂无影评</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile-page { max-width: 680px; margin: 0 auto; padding: 40px 24px 80px; }

.profile-header { display: flex; align-items: center; gap: 20px; }
.profile-info { flex: 1; }
.profile-info h2 { font-size: 22px; color: #f5af19; margin: 0; }
.bio { font-size: 14px; color: rgba(255,255,255,.5); margin: 6px 0; }
.username { font-size: 13px; color: rgba(255,255,255,.3); margin: 0; }
.follow-area {
  flex-shrink: 0;
  --el-button-bg-color: #f5af19;
  --el-button-border-color: #f5af19;
  --el-button-hover-bg-color: #f7c04a;
  --el-button-hover-border-color: #f7c04a;
}

.stats-row { display: flex; gap: 24px; margin-top: 24px; padding: 16px; background: rgba(255,255,255,.03); border-radius: 12px; }
.stat { text-align: center; flex: 1; }
.num { display: block; font-size: 20px; font-weight: 700; color: rgba(255,255,255,.85); }
.label { font-size: 12px; color: rgba(255,255,255,.4); margin-top: 2px; }

.loading-text { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }

.reviews-section { margin-top: 32px; }
.section-title { font-size: 16px; font-weight: 600; color: rgba(255,255,255,.6); margin: 0 0 16px; padding-bottom: 12px; border-bottom: 1px solid rgba(255,255,255,.08); }
.reviews-section .review-card { margin-bottom: 16px; }
.load-more { text-align: center; padding: 12px; color: #f5af19; cursor: pointer; font-size: 14px; border-radius: 8px; transition: background .2s; }
.load-more:hover { background: rgba(255,255,255,.04); }
.empty-text { text-align: center; padding: 40px 0; color: rgba(255,255,255,.25); font-size: 14px; }
</style>
