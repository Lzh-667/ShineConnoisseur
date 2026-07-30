<script setup>
import { ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { getFavoriteMovies } from '../api'

defineOptions({ name: 'MyFavorites' })

const router = useRouter()

const loading = ref(false)
const movies = ref([])
const current = ref(1)
const total = ref(0)
const pageSize = 10

async function load() {
  loading.value = true
  try {
    const res = await getFavoriteMovies(current.value)
    movies.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally { loading.value = false }
}

function onPageChange(p) { current.value = p; load() }

function normalizeCover(c) {
  if (!c) return ''
  if (c.startsWith('http://') || c.startsWith('https://') || c.startsWith('/')) return c
  return '/' + c
}

onActivated(() => {
  current.value = 1
  load()
})
</script>

<template>
  <div class="favorites-page">
    <h2 class="page-title">我的收藏</h2>

    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="movies.length === 0" class="empty">暂无收藏</div>
    <template v-else>
      <div class="movie-grid">
        <div v-for="m in movies" :key="m.id" class="movie-card" @click="router.push(`/movies/${m.id}`)">
          <div class="movie-cover">
            <img v-if="m.cover" :src="normalizeCover(m.cover)" :alt="m.title" />
            <div v-else class="cover-placeholder">🎬</div>
          </div>
          <div class="movie-info">
            <span class="movie-title">{{ m.title }}</span>
          </div>
        </div>
      </div>
      <el-pagination v-if="total > pageSize" class="pager" background layout="prev, pager, next, jumper" :total="total" :page-size="pageSize" :current-page="current" @current-change="onPageChange" />
    </template>
  </div>
</template>

<style scoped>
.favorites-page { max-width: 960px; margin: 0 auto; padding: 40px 24px 80px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0 0 24px; }

.movie-grid { display: grid; gap: 20px; grid-template-columns: repeat(5, 1fr); }
@media (max-width: 1100px) { .movie-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 860px) { .movie-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 560px) { .movie-grid { grid-template-columns: repeat(2, 1fr); } }
.movie-card { background: rgba(255,255,255,.03); border-radius: 12px; overflow: hidden; cursor: pointer; transition: transform .3s ease, box-shadow .3s ease; }
.movie-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,.25); }
.movie-cover { aspect-ratio: 2/3; background: rgba(255,255,255,.05); }
.movie-cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { display: flex; align-items: center; justify-content: center; height: 100%; font-size: 40px; }
.movie-info { padding: 12px; }
.movie-title { display: block; font-size: 14px; font-weight: 600; color: rgba(255,255,255,.8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.pager { margin-top: 24px; justify-content: center; }
</style>
