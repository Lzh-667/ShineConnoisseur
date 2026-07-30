<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getHotReviews, getHotMovies } from '../api'

defineOptions({ name: 'Home' })
import ReviewCard from '../components/ReviewCard.vue'

const router = useRouter()
const userStore = useUserStore()

const reviews = ref([])
const hotMovies = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const current = ref(1)
const hasMore = ref(true)
const loadError = ref(false)

let scrollEl = null

async function loadReviews(page = 1) {
  if (page === 1) loading.value = true
  else loadingMore.value = true
  loadError.value = false
  try {
    const res = await getHotReviews(page)
    const { list, hasMore: more } = res.data
    if (page === 1) reviews.value = list ?? []
    else reviews.value.push(...(list ?? []))
    hasMore.value = more ?? false
    current.value = page
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadHotMovies() {
  try {
    const res = await getHotMovies()
    hotMovies.value = res.data ?? []
  } catch { /* */ }
}

function onScroll() {
  if (loadingMore.value || !hasMore.value) return
  const { scrollTop, scrollHeight, clientHeight } = scrollEl
  if (scrollTop + clientHeight >= scrollHeight - 200) {
    loadReviews(current.value + 1)
  }
}

onMounted(() => {
  loadReviews()
  if (!userStore.isLoggedIn) {
    loadHotMovies()
  }
  scrollEl = document.documentElement
  document.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  document.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <!-- guest hero -->
  <div v-if="!userStore.isLoggedIn" class="home-guest">
    <div class="guest-hero">
      <div class="hero-bg"></div>
      <h1 class="hero-title">光影鉴赏家</h1>
      <p class="hero-subtitle">发现好电影，分享你的观点</p>
      <el-button class="hero-cta" size="large" round @click="router.push('/login')">立即加入</el-button>
    </div>

    <div v-if="hotMovies.length > 0" class="hot-movies-section">
      <h3 class="section-title">热门电影</h3>
      <div class="movie-grid">
        <div v-for="m in hotMovies" :key="m.title" class="movie-card" @click="router.push('/movies/' + m.id)">
          <div class="movie-cover">
            <img v-if="m.cover" :src="m.cover" :alt="m.title" />
            <div v-else class="movie-cover-placeholder">🎬</div>
          </div>
          <span class="movie-title">{{ m.title }}</span>
        </div>
      </div>
    </div>
  </div>

  <!-- review feed (everyone) -->
  <div class="home-feed">
    <div class="feed-header">
      <h2 class="feed-title">热门影评</h2>
      <p class="feed-subtitle">发现社区精彩观点</p>
    </div>

    <div v-if="loading" class="feed-skeleton">
      <div v-for="i in 3" :key="i" class="skeleton-card">
        <div class="skeleton-line skeleton-avatar"></div>
        <div class="skeleton-line skeleton-title"></div>
        <div class="skeleton-line skeleton-text"></div>
        <div class="skeleton-line skeleton-text short"></div>
      </div>
    </div>

    <div v-else-if="loadError && reviews.length === 0" class="feed-empty">
      <p>加载失败</p>
      <el-button @click="loadReviews()">重试</el-button>
    </div>

    <div v-else-if="reviews.length === 0" class="feed-empty">
      <p>暂无影评</p>
    </div>

    <template v-else>
      <TransitionGroup name="review-list" tag="div" class="review-list">
        <ReviewCard v-for="r in reviews" :key="r.id" :review="r" />
      </TransitionGroup>

      <div v-if="loadingMore" class="load-more">加载中...</div>
      <div v-else-if="!hasMore" class="load-end">— 已经到底了 —</div>
    </template>
  </div>
</template>

<style scoped>
/* ===== Feed ===== */
.home-feed { max-width: 680px; margin: 0 auto; padding: 44px 24px 80px; }

.feed-header { text-align: center; margin-bottom: 44px; }
.feed-title { font-size: 28px; font-weight: 700; color: #f5af19; margin: 0; }
.feed-subtitle { font-size: 14px; color: rgba(255,255,255,.35); margin: 8px 0 0; }

.review-list { display: flex; flex-direction: column; gap: 20px; }

.review-list-enter-active { transition: all .4s ease; }
.review-list-enter-from { opacity: 0; transform: translateY(20px); }

.feed-skeleton { display: flex; flex-direction: column; gap: 20px; }
.skeleton-card {
  background: rgba(255,255,255,.03); border-radius: 14px; padding: 24px;
}
.skeleton-line { background: rgba(255,255,255,.06); border-radius: 4px; margin-bottom: 12px; }
.skeleton-avatar { width: 40px; height: 40px; border-radius: 50%; }
.skeleton-title { width: 60%; height: 20px; }
.skeleton-text { width: 100%; height: 14px; }
.skeleton-text.short { width: 40%; }

.feed-empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); font-size: 14px; }

.load-more, .load-end { text-align: center; padding: 24px; color: rgba(255,255,255,.2); font-size: 13px; }

/* ===== Guest ===== */
.home-guest { text-align: center; }

.guest-hero {
  position: relative; padding: 120px 24px 96px; overflow: hidden;
}
.hero-bg {
  position: absolute; inset: 0;
  background: radial-gradient(ellipse at 50% 0%, rgba(245,175,25,.06) 0%, transparent 60%);
  pointer-events: none;
}
.hero-title {
  position: relative; font-size: 52px; font-weight: 800; color: #f5af19; margin: 0;
  letter-spacing: -1px; animation: heroFadeIn 1s ease-out;
}
@keyframes heroFadeIn {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: translateY(0); }
}
.hero-subtitle {
  position: relative; font-size: 18px; color: rgba(255,255,255,.4); margin: 16px 0 36px;
  opacity: 0; animation: heroFadeIn 1s ease-out .2s forwards;
}
.hero-cta {
  position: relative; --el-button-bg-color: #f5af19; --el-button-border-color: #f5af19;
  --el-button-hover-bg-color: #f7c04a; --el-button-hover-border-color: #f7c04a;
  font-weight: 600; padding: 12px 44px; font-size: 16px;
  opacity: 0; animation: heroFadeIn 1s ease-out .4s forwards;
}

.hot-movies-section { max-width: 960px; margin: 0 auto; padding: 0 28px 80px; }
.section-title { font-size: 20px; color: rgba(255,255,255,.65); margin: 0 0 28px; font-weight: 600; }

.movie-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 18px; }

.movie-card {
  background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.04);
  border-radius: 14px; padding: 16px; cursor: pointer;
  transition: transform .3s ease, border-color .3s ease, box-shadow .3s ease; text-align: center;
}
.movie-card:hover { transform: translateY(-5px); border-color: rgba(245,175,25,.15); box-shadow: 0 12px 32px rgba(0,0,0,.3); }

.movie-cover {
  width: 100%; aspect-ratio: 2/3; border-radius: 10px; overflow: hidden;
  background: rgba(255,255,255,.03); margin-bottom: 12px;
}
.movie-cover img { width: 100%; height: 100%; object-fit: cover; transition: transform .4s ease; }
.movie-card:hover .movie-cover img { transform: scale(1.06); }
.movie-cover-placeholder { display: flex; align-items: center; justify-content: center; height: 100%; font-size: 36px; }

.movie-title { font-size: 13px; color: rgba(255,255,255,.65); display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
</style>
