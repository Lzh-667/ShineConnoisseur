<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHotMovies, searchMovies } from '../api'
import { Search } from '@element-plus/icons-vue'

defineOptions({ name: 'MovieList' })

const router = useRouter()

const loading = ref(false)
const movies = ref([])
const current = ref(1)
const total = ref(0)
const searchPageSize = 8
const hotPageSize = 10
const filters = reactive({ title: '', genre: '', region: '' })
const genres = ['剧情','犯罪','励志','爱情','社会','灾难','奇幻','冒险','历史','悬疑','喜剧','传记','家庭','战争','心理','科幻','歌舞','法律','惊悚','动作','动画','音乐','温情']
const searchLabel = ref('')

const isSearching = computed(() => searchLabel.value !== '')
const pageTitle = computed(() => searchLabel.value ? `搜索：${searchLabel.value}` : '热门电影')

async function loadHot() {
  loading.value = true
  searchLabel.value = ''
  try {
    const res = await getHotMovies(current.value)
    const data = res.data
    if (Array.isArray(data)) {
      movies.value = data
      total.value = 0
    } else {
      movies.value = data?.records ?? []
      total.value = data?.total ?? 0
    }
  } catch {
    movies.value = []
  } finally { loading.value = false }
}

async function loadSearch() {
  loading.value = true
  searchLabel.value = filters.title || [filters.genre, filters.region].filter(Boolean).join(' ')
  try {
    const res = await searchMovies(filters.title, current.value, filters.genre, filters.region)
    movies.value = res.data?.records ?? []
    total.value = res.data.total ?? 0
  } finally { loading.value = false }
}

function onSearch() {
  current.value = 1
  if (filters.title || filters.genre || filters.region) loadSearch()
  else loadHot()
}

function onPageChange(p) {
  current.value = p
  if (searchLabel.value) loadSearch()
  else loadHot()
}

function normalizeCover(c) {
  if (!c) return ''
  if (c.startsWith('http://') || c.startsWith('https://') || c.startsWith('/')) return c
  return '/' + c
}

onMounted(() => loadHot())
</script>

<template>
  <div class="movie-page">
    <div class="page-header">
      <h2 class="page-title">{{ pageTitle }}</h2>
    </div>

    <div class="filter-bar">
      <el-input v-model="filters.title" placeholder="搜索电影..." clearable class="filter-input" />
      <div class="filter-right">
        <el-select v-model="filters.genre" placeholder="类型" clearable class="filter-small">
          <el-option v-for="g in genres" :key="g" :label="g" :value="g" />
        </el-select>
        <el-input v-model="filters.region" placeholder="地区" clearable class="filter-small" />
        <button class="search-btn" @click="onSearch">
          <el-icon :size="16"><Search /></el-icon>
          搜索
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="movies.length === 0" class="empty">暂无电影</div>
    <template v-else>
      <div class="movie-grid">
        <div v-for="m in movies" :key="m.id" class="movie-card" @click="router.push(`/movies/${m.id}`)">
          <div class="movie-cover">
            <img v-if="m.cover" :src="normalizeCover(m.cover)" :alt="m.title" />
            <div v-else class="cover-placeholder">🎬</div>
            <div class="cover-overlay">
              <span class="overlay-text">查看详情</span>
            </div>
          </div>
          <div class="movie-info">
            <span class="movie-title">{{ m.title }}</span>
            <div class="movie-meta">
              <span class="movie-rating" v-if="m.rating">★ {{ m.rating }}</span>
              <span class="movie-genre" v-if="m.genre">{{ m.genre }}</span>
            </div>
          </div>
        </div>
      </div>
      <el-pagination class="pager" background layout="prev, pager, next, jumper" :total="total" :page-size="hasFilter ? searchPageSize : hotPageSize" :current-page="current" @current-change="onPageChange" />
    </template>
  </div>
</template>

<style scoped>
.movie-page { max-width: 1040px; margin: 0 auto; padding: 20px 28px 40px; }
.page-title { font-size: 24px; color: #f5af19; margin: 0 0 8px; }
.page-desc { font-size: 13px; color: rgba(255,255,255,.25); margin: 4px 0 0; }

.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 16px; }
.filter-input { max-width: 340px; }
.filter-right { display: flex; gap: 10px; align-items: center; }
.filter-small { width: 120px; flex-shrink: 0; }

/* Filter dark inputs */
.filter-bar :deep(.el-input__wrapper),
.filter-bar :deep(.el-select__wrapper) {
  background: rgba(255,255,255,.03) !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.08) inset !important;
  border-radius: 10px !important;
  transition: all .25s !important;
}
.filter-bar :deep(.el-input__wrapper:hover),
.filter-bar :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255,255,255,.16) inset !important;
}
.filter-bar :deep(.el-input__wrapper.is-focus),
.filter-bar :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f5af19 inset !important;
}
.filter-bar :deep(.el-input__inner) { color: rgba(255,255,255,.7) !important; }
.filter-bar :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.22) !important; }

.search-btn {
  height: 40px; display: flex; align-items: center; gap: 5px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; color: #fff; padding: 0 20px; border-radius: 10px;
  font-size: 13px; font-weight: 600; cursor: pointer; transition: opacity .2s; white-space: nowrap;
}
.search-btn:hover { opacity: .85; }

.movie-grid { display: grid; gap: 16px; grid-template-columns: repeat(5, 1fr); }
@media (max-width: 1100px) { .movie-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 860px) { .movie-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 560px) { .movie-grid { grid-template-columns: repeat(2, 1fr); } }

.movie-card {
  background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.04);
  border-radius: 12px; overflow: hidden; cursor: pointer;
  transition: transform .3s ease, border-color .3s ease, box-shadow .3s ease;
}
.movie-card:hover { transform: translateY(-4px); border-color: rgba(245,175,25,.15); box-shadow: 0 12px 32px rgba(0,0,0,.35); }

.movie-cover { aspect-ratio: 2/3; background: rgba(255,255,255,.03); position: relative; overflow: hidden; }
.movie-cover img { width: 100%; height: 100%; object-fit: cover; transition: transform .4s ease; }
.movie-card:hover .movie-cover img { transform: scale(1.06); }
.cover-placeholder { display: flex; align-items: center; justify-content: center; height: 100%; font-size: 36px; }

.cover-overlay {
  position: absolute; inset: 0;
  background: rgba(0,0,0,.55); display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity .3s ease;
}
.movie-card:hover .cover-overlay { opacity: 1; }
.overlay-text { font-size: 12px; color: #fff; font-weight: 600; letter-spacing: 1px; }

.movie-info { padding: 10px 12px; }
.movie-title { display: block; font-size: 13px; font-weight: 600; color: rgba(255,255,255,.8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.movie-meta { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.movie-rating { font-size: 11px; color: #f5af19; font-weight: 600; }
.movie-genre { font-size: 11px; color: rgba(255,255,255,.3); }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.25); font-size: 14px; }
.pager { margin-top: 20px; justify-content: center; }
</style>
