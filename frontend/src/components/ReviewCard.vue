<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { likeReview } from '../api'
import { ElMessage } from 'element-plus'

const props = defineProps({
  review: { type: Object, required: true },
})

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const TRUNCATE_LENGTH = 200
const expanded = ref(false)
const isLike = ref(props.review.isLike)
const likeCount = ref(props.review.likeCount)
const likeLoading = ref(false)

const contentDisplay = computed(() => {
  if (expanded.value || props.review.content.length <= TRUNCATE_LENGTH) {
    return props.review.content
  }
  return props.review.content.slice(0, TRUNCATE_LENGTH) + '...'
})

const needTruncate = computed(() => props.review.content.length > TRUNCATE_LENGTH)

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function handleLike() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  if (likeLoading.value) return
  likeLoading.value = true
  // 乐观更新
  const prev = isLike.value
  isLike.value = !isLike.value
  likeCount.value += isLike.value ? 1 : -1
  try {
    const res = await likeReview(props.review.id)
    isLike.value = res.data.like
    likeCount.value = res.data.likeCount
  } catch {
    isLike.value = prev
    likeCount.value += prev ? 1 : -1
  } finally {
    likeLoading.value = false
  }
}

function goToDetail() {
  router.push(`/reviews/${props.review.id}`)
}

function goToUser(e) {
  e.stopPropagation()
  router.push(`/users/${props.review.userId}`)
}

function goToMovie(e) {
  e.stopPropagation()
  if (props.review.movieId) {
    router.push(`/movies/${props.review.movieId}`)
  }
}
</script>

<template>
  <article class="review-card" @click="goToDetail">
    <header class="review-header">
      <div class="author" @click="goToUser">
        <el-avatar :size="40" :src="review.avatar" />
        <div class="author-info">
          <span class="author-name">{{ review.nickName || review.userName }}</span>
          <span class="review-time">{{ formatTime(review.createTime) }}</span>
        </div>
      </div>
      <div class="rating-badge">
        <span class="rating-star">★</span>
        <span class="rating-num">{{ review.rating }}</span>
      </div>
    </header>

    <div class="review-body">
      <div v-if="review.movieTitle" class="review-movie" @click="goToMovie">
        <span class="movie-icon">🎬</span>
        <span>来自：{{ review.movieTitle }}</span>
      </div>
      <h3 class="review-title">
        <span v-if="review.spoiler" class="spoiler-tag">剧透</span>
        {{ review.title }}
      </h3>
      <p class="review-content">{{ contentDisplay }}</p>
      <button v-if="needTruncate" class="expand-btn" @click.stop="expanded = !expanded">
        {{ expanded ? '收起' : '展开全文' }}
      </button>
    </div>

    <footer class="review-footer" @click.stop>
      <button class="action-btn" :class="{ active: isLike }" @click="handleLike">
        <span class="action-icon">{{ isLike ? '❤' : '♡' }}</span>
        <span>{{ likeCount || '' }}</span>
      </button>
      <button class="action-btn">
        <span class="action-icon">💬</span>
        <span>{{ review.commentCount || '' }}</span>
      </button>
    </footer>
  </article>
</template>

<style scoped>
.review-card {
  background: rgba(255,255,255,.03);
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: background .3s ease, transform .3s ease, box-shadow .3s ease;
  border: 1px solid rgba(255,255,255,.06);
}
.review-card:hover {
  background: rgba(255,255,255,.05);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0,0,0,.2);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.author {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.author:hover .author-name { color: #f5af19; }

.author-info { display: flex; flex-direction: column; }
.author-name { font-size: 14px; font-weight: 600; color: rgba(255,255,255,.75); transition: color .2s; }
.review-time { font-size: 12px; color: rgba(255,255,255,.3); margin-top: 2px; }

.rating-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  color: #fff;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.rating-star { font-size: 12px; }

.review-body { margin-bottom: 16px; }

.review-movie {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #f5af19;
  background: rgba(245,175,25,.08);
  padding: 2px 10px;
  border-radius: 10px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: background .2s;
}
.review-movie:hover { background: rgba(245,175,25,.15); }
.review-movie .movie-icon { font-size: 11px; }

.spoiler-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: #ff6b6b;
  background: rgba(255,107,107,.15);
  padding: 1px 7px;
  border-radius: 6px;
  margin-right: 6px;
  vertical-align: middle;
}

.review-title {
  font-size: 18px;
  font-weight: 600;
  color: rgba(255,255,255,.8);
  margin: 0 0 10px;
  line-height: 1.4;
}

.review-content {
  font-size: 15px;
  color: rgba(255,255,255,.5);
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.expand-btn {
  background: none;
  border: none;
  color: #f5af19;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 0;
  margin-top: 4px;
}
.expand-btn:hover { opacity: .7; }

.review-footer {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid rgba(255,255,255,.06);
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  font-size: 14px;
  color: rgba(255,255,255,.35);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: all .25s ease;
  transform: scale(1);
}
.action-btn:hover { background: rgba(255,255,255,.06); color: rgba(255,255,255,.5); transform: scale(1.05); }
.action-btn.active { color: #f56c6c; }
.action-btn.active:hover { color: #e04545; }
.action-icon { font-size: 16px; }
</style>
