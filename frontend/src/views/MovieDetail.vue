<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getMovieDetail, getMovieReviews, checkFavorite, toggleFavorite, publishReview, likeReview } from '../api'
import ReviewCard from '../components/ReviewCard.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const movie = ref(null)
const coverUrl = computed(() => {
  const c = movie.value?.cover
  if (!c) return ''
  if (c.startsWith('http://') || c.startsWith('https://') || c.startsWith('/')) return c
  return '/' + c
})
const loading = ref(false)
const notFound = ref(false)
const reviews = ref([])
const reviewLoading = ref(false)
const reviewCurrent = ref(1)
const reviewHasMore = ref(false)
const isFav = ref(false)
const favLoading = ref(false)

// publish
const showPublish = ref(false)
const publishForm = reactive({ rating: null, title: '', content: '', spoiler: 0 })
const publishLoading = ref(false)

async function loadMovie() {
  loading.value = true
  try {
    const res = await getMovieDetail(route.params.id)
    movie.value = res.data
    loadReviews()
    if (userStore.isLoggedIn) {
      checkFavorite(route.params.id).then(r => isFav.value = r.data === true).catch(() => {})
    }
  } catch { notFound.value = true } finally { loading.value = false }
}

async function loadReviews(page = 1) {
  reviewLoading.value = true
  try {
    const res = await getMovieReviews(route.params.id, page)
    const list = res.data?.records ?? []
    if (page === 1) reviews.value = list
    else reviews.value.push(...list)
    reviewHasMore.value = res.data?.total ? reviews.value.length < res.data.total : false
    reviewCurrent.value = page
  } finally { reviewLoading.value = false }
}

function loadMoreReviews() { if (reviewHasMore.value) loadReviews(reviewCurrent.value + 1) }

async function handleFavorite() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  favLoading.value = true
  try {
    await toggleFavorite(movie.value.id, !isFav.value)
    isFav.value = !isFav.value
  } finally { favLoading.value = false }
}

async function handlePublish() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  if (!publishForm.rating) { ElMessage.warning('请评分'); return }
  if (!publishForm.title.trim()) { ElMessage.warning('请输入标题'); return }
  if (!publishForm.content.trim()) { ElMessage.warning('请输入内容'); return }
  publishLoading.value = true
  try {
    await publishReview(movie.value.id, publishForm)
    ElMessage.success('影评发布成功')
    showPublish.value = false
    publishForm.rating = null; publishForm.title = ''; publishForm.content = ''; publishForm.spoiler = 0
    loadReviews(1)
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally { publishLoading.value = false }
}

function handleWriteReview() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  showPublish.value = true
}

onMounted(() => { loadMovie() })
</script>

<template>
  <div class="movie-detail-page">
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="notFound" class="loading-text">电影不存在</div>

    <template v-else-if="movie">
      <div class="movie-hero">
        <div class="cover-col">
          <img v-if="coverUrl" :src="coverUrl" :alt="movie.title" class="cover-img" />
          <div v-else class="cover-fallback">🎬</div>
        </div>
        <div class="info-col">
          <h1 class="title">{{ movie.title }}</h1>
          <p v-if="movie.originalTitle" class="subtitle">{{ movie.originalTitle }}</p>

          <div class="tags">
            <div v-if="movie.rating" class="rating-block">
              <span class="rating-score">★ {{ movie.rating }}</span>
              <span class="rating-count">{{ movie.ratingCount }}人评分</span>
            </div>
            <span v-if="movie.genre" class="tag">{{ movie.genre }}</span>
            <span v-if="movie.region" class="tag">{{ movie.region }}</span>
            <span v-if="movie.language" class="tag">{{ movie.language }}</span>
            <span v-if="movie.duration" class="tag">{{ movie.duration }}分钟</span>
            <span v-if="movie.releaseDate" class="tag">{{ movie.releaseDate }}</span>
          </div>

          <div v-if="movie.director || movie.actors" class="people">
            <p v-if="movie.director"><label>导演</label> {{ movie.director }}</p>
            <p v-if="movie.actors"><label>主演</label> {{ movie.actors }}</p>
          </div>

          <p v-if="movie.summary" class="summary">{{ movie.summary }}</p>

          <div class="action-row">
            <button class="fav-btn" :class="{ active: isFav }" :disabled="favLoading" @click="handleFavorite">
              {{ isFav ? '❤ 已收藏' : '♡ 收藏' }}
            </button>
            <button class="write-review-btn" @click="handleWriteReview">
              ✎ 写影评
            </button>
          </div>
        </div>
      </div>

      <div class="reviews-section">
        <h3 class="section-title">影评</h3>

        <div v-if="reviewLoading && reviews.length === 0" class="loading-text">加载中...</div>
        <div v-else-if="reviews.length === 0" class="empty">暂无影评，来写第一条吧</div>
        <div v-else class="review-list">
          <ReviewCard v-for="r in reviews" :key="r.id" :review="r" />
          <button v-if="reviewHasMore" class="load-more-btn" @click="loadMoreReviews">加载更多影评</button>
        </div>
      </div>

      <!-- publish dialog -->
      <el-dialog v-model="showPublish" width="480px" :close-on-click-modal="false">
        <template #header>
          <div class="dialog-title">写影评</div>
          <div class="dialog-movie-name">{{ movie.title }}</div>
        </template>

        <div class="publish-body">
          <div class="rating-row">
            <span class="rating-label">你的评分</span>
            <el-rate v-model="publishForm.rating" :max="10" show-score score-template="{value}分" />
          </div>

          <el-input v-model="publishForm.title" placeholder="输入影评标题" maxlength="50" show-word-limit class="publish-input" />

          <el-input v-model="publishForm.content" type="textarea" placeholder="写下你对该电影的感想..." :rows="5" resize="none" class="publish-textarea" />

          <label class="spoiler-check">
            <el-checkbox v-model="publishForm.spoiler" :true-value="1" :false-value="0" />
            <span>内容包含剧透</span>
          </label>

          <button class="publish-submit" :disabled="publishLoading" @click="handlePublish">
            {{ publishLoading ? '发布中...' : '发布影评' }}
          </button>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.movie-detail-page { max-width: 860px; margin: 0 auto; padding: 40px 24px 80px; }

.movie-hero { display: grid; grid-template-columns: 220px 1fr; gap: 32px; margin-bottom: 48px; align-items: start; }

.cover-col { aspect-ratio: 2/3; }
.cover-img { width: 100%; height: 100%; object-fit: cover; border-radius: 12px; }
.cover-fallback { width: 100%; height: 100%; background: rgba(255,255,255,.04); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 56px; }

.info-col { min-width: 0; }
.title { font-size: 28px; font-weight: 700; color: #f5af19; margin: 0; line-height: 1.3; }
.subtitle { font-size: 14px; color: rgba(255,255,255,.35); margin: 4px 0 14px; }

/* 评分 + 标签 */
.rating-block {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: 4px;
}
.rating-score {
  font-size: 22px;
  font-weight: 700;
  color: #f5af19;
}
.rating-count {
  font-size: 12px;
  color: rgba(255,255,255,.35);
  padding-top: 6px;
}

.tags { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 14px; }
.tag { font-size: 12px; color: rgba(255,255,255,.5); background: rgba(255,255,255,.05); padding: 3px 10px; border-radius: 8px; }

.people { margin-bottom: 12px; }
.people p { font-size: 13px; color: rgba(255,255,255,.5); margin: 3px 0; }
.people label { color: rgba(255,255,255,.3); margin-right: 6px; }

.summary { font-size: 14px; color: rgba(255,255,255,.6); line-height: 1.85; margin: 0 0 16px; }

/* 操作按钮 */
.action-row { display: flex; align-items: center; gap: 12px; }

.action-row button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: none;
  color: #1a1a2e;
  padding: 8px 20px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: all .2s;
  min-width: 96px;
}
.action-row button:hover { opacity: .85; transform: scale(1.02); }

.fav-btn { background: rgba(255,255,255,.12); color: rgba(255,255,255,.65); }
.fav-btn:hover { color: #f56c6c; }
.fav-btn.active { background: linear-gradient(135deg, #f56c6c, #e04545); color: #fff; }

.write-review-btn { background: linear-gradient(135deg, #f5af19, #f12711); }

/* 影评区域 */
.reviews-section { border-top: 1px solid rgba(255,255,255,.06); padding-top: 32px; }
.section-title { font-size: 18px; color: rgba(255,255,255,.7); margin: 0 0 20px; }

.review-list { display: flex; flex-direction: column; gap: 16px; }

.load-more-btn { background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.06); color: rgba(255,255,255,.4); font-size: 13px; padding: 10px; border-radius: 8px; cursor: pointer; width: 100%; }
.load-more-btn:hover { background: rgba(255,255,255,.08); color: rgba(255,255,255,.6); }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.empty .link { color: #409eff; cursor: pointer; }

/* 写影评弹窗 */
:deep(.el-dialog) {
  background: #1e1e30;
  border-radius: 16px;
  overflow: hidden;
}
:deep(.el-dialog__header) {
  background: linear-gradient(135deg, rgba(245,175,25,.12), rgba(245,175,25,.04));
  margin: 0;
  padding: 28px 28px 0;
  text-align: center;
  border-bottom: none;
}
.dialog-title { font-size: 18px; font-weight: 700; color: #f5af19; }
.dialog-movie-name { font-size: 13px; color: rgba(255,255,255,.35); margin-top: 4px; padding-bottom: 20px; }

:deep(.el-dialog__body) { padding: 24px 28px 28px; }

.publish-body { display: flex; flex-direction: column; gap: 16px; }

.rating-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.rating-label { font-size: 14px; color: rgba(255,255,255,.5); }

.publish-input :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.12) inset !important;
  border-radius: 10px !important; padding: 10px 14px;
}
.publish-input :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px rgba(255,255,255,.2) inset !important; }
.publish-input :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.publish-input :deep(.el-input__inner) { color: rgba(255,255,255,.7) !important; }
.publish-input :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.25) !important; }

.publish-textarea :deep(.el-textarea__inner) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.12) inset !important;
  border-radius: 10px !important; color: rgba(255,255,255,.7) !important;
  padding: 12px 14px; line-height: 1.8;
}
.publish-textarea :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.publish-textarea :deep(.el-textarea__inner::placeholder) { color: rgba(255,255,255,.25) !important; }

.spoiler-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: rgba(255,255,255,.4);
  cursor: pointer;
  padding-left: 2px;
}

.publish-submit {
  width: 100%;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border: none;
  color: #1a1a2e;
  padding: 12px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all .2s;
  margin-top: 4px;
}
.publish-submit:hover { opacity: .85; }
.publish-submit:disabled { opacity: .5; cursor: not-allowed; }
</style>
