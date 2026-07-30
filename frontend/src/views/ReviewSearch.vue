<script setup>
import { ref, reactive, computed } from 'vue'
import { searchReviews } from '../api'
import { Search } from '@element-plus/icons-vue'

defineOptions({ name: 'ReviewSearch' })
import ReviewCard from '../components/ReviewCard.vue'

const loading = ref(false)
const reviews = ref([])
const current = ref(1)
const total = ref(0)
const pageSize = 6
const searched = ref(false)

const filters = reactive({ keyword: '', spoiler: undefined })

const searchLabel = ref('')

function setSpoiler(val) {
  filters.spoiler = val
  if (searched.value && filters.keyword.trim()) {
    current.value = 1
    loadResults()
  }
}
const pageTitle = computed(() => {
  if (!searched.value) return '搜索影评'
  return searchLabel.value ? `搜索：${searchLabel.value}` : '搜索影评'
})

async function loadResults() {
  loading.value = true
  searchLabel.value = filters.keyword
  searched.value = true
  try {
    const res = await searchReviews(filters.keyword, current.value, filters.spoiler)
    const data = res.data ?? {}
    reviews.value = data.list ?? data.records ?? []
    total.value = data.total ?? data.pages ?? 0
  } finally { loading.value = false }
}

function onSearch() {
  current.value = 1
  if (!filters.keyword.trim()) return
  loadResults()
}

function onPageChange(p) {
  current.value = p
  loadResults()
}
</script>

<template>
  <div class="search-page" :class="{ 'search-done': searched }">
    <!-- 背景光晕 -->
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>

    <!-- 初始状态：居中搜索区 -->
    <div class="search-hero">
      <div class="hero-icon">
        <span class="icon-circle">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
            <path d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z"/>
            <path d="M21 21l-4.35-4.35"/>
            <path d="M8 11h6"/>
            <path d="M7 14.5l2.5-2.5-2.5-2.5"/>
          </svg>
        </span>
      </div>
      <h2 class="hero-title">探索精彩影评</h2>
      <p class="hero-desc">搜索你感兴趣的电影评论，发现社区中的深度观点</p>

      <div class="search-box">
        <div class="search-row">
          <el-input
            v-model="filters.keyword"
            placeholder="输入片名或关键词..."
            size="large"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
          />
          <button class="search-btn" @click="onSearch">
            <el-icon :size="18"><Search /></el-icon>
            搜索
          </button>
        </div>
        <div class="filter-row">
          <span class="spoiler-label">剧透</span>
          <button
            class="dot-toggle"
            :class="{ active: filters.spoiler === undefined }"
            @click="setSpoiler(undefined)"
          >全部</button>
          <button
            class="dot-toggle"
            :class="{ active: filters.spoiler === 0 }"
            @click="setSpoiler(0)"
          >否</button>
          <button
            class="dot-toggle"
            :class="{ active: filters.spoiler === 1 }"
            @click="setSpoiler(1)"
          >是</button>
        </div>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searched" class="search-results">
      <div class="results-header">
        <div class="results-header-left">
          <span class="results-icon">✦</span>
          <h3 class="results-title">{{ pageTitle }}</h3>
        </div>
        <span v-if="total > 0" class="results-count">共 {{ total }} 条结果</span>
      </div>

      <div v-if="loading" class="loading-state">
        <span class="loading-spinner"></span>
        <p>搜索中...</p>
      </div>

      <div v-else-if="reviews.length === 0" class="empty-result">
        <div class="empty-visual">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" stroke-linecap="round">
            <circle cx="11" cy="11" r="7"/>
            <path d="M21 21l-4.35-4.35"/>
            <path d="M8 11h6" stroke-width="1.5" opacity="0"/>
          </svg>
        </div>
        <p class="empty-title">没有找到相关影评</p>
        <p class="empty-hint">试试其他关键词或调整筛选条件</p>
      </div>

      <template v-else>
        <div class="review-list">
          <div
            v-for="(r, i) in reviews"
            :key="r.id"
            class="review-card-wrapper"
            :style="{ animationDelay: `${i * 0.06}s` }"
          >
            <ReviewCard :review="r" />
          </div>
        </div>
        <el-pagination
          class="pager"
          background
          layout="prev, pager, next, jumper"
          :total="total"
          :page-size="pageSize"
          :current-page="current"
          @current-change="onPageChange"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.search-page {
  position: relative;
  max-width: 780px;
  min-height: calc(100vh - 64px);
  margin: 0 auto;
  padding: 80px 28px 60px;
  transition: padding .4s ease;
  overflow: hidden;
}
.search-done {
  padding-top: 36px;
}

/* ===== 背景光晕 ===== */
.bg-orb {
  position: fixed; border-radius: 50%; pointer-events: none; filter: blur(120px); opacity: .07;
  z-index: 0;
}
.orb-1 { width: 420px; height: 420px; background: #f5af19; top: -160px; right: -100px; animation: orbFloat1 10s ease-in-out infinite; }
.orb-2 { width: 320px; height: 320px; background: #667eea; bottom: -120px; left: -80px; animation: orbFloat2 12s ease-in-out infinite; }
.orb-3 { width: 240px; height: 240px; background: #f12711; top: 50%; left: 50%; transform: translate(-50%, -50%); animation: orbFloat3 14s ease-in-out infinite; }
@keyframes orbFloat1 {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(-30px, 20px); }
  66% { transform: translate(15px, -25px); }
}
@keyframes orbFloat2 {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(25px, -15px); }
  66% { transform: translate(-20px, 30px); }
}
@keyframes orbFloat3 {
  0%, 100% { transform: translate(-50%, -50%) scale(1); }
  50% { transform: translate(-50%, -50%) scale(1.3); }
}

/* ===== Hero ===== */
.search-hero {
  position: relative; z-index: 1;
  text-align: center;
  margin-bottom: 48px;
  transition: all .4s ease;
}
.search-done .search-hero {
  margin-bottom: 28px;
}
.search-done .hero-icon,
.search-done .hero-desc {
  display: none;
}
.search-done .hero-title {
  font-size: 22px;
  margin-bottom: 16px;
}

.hero-icon { margin-bottom: 24px; }
.icon-circle {
  display: inline-flex; align-items: center; justify-content: center;
  width: 96px; height: 96px; border-radius: 50%;
  background: linear-gradient(135deg, rgba(245,175,25,.1), rgba(245,175,25,.04));
  border: 1px solid rgba(245,175,25,.15);
  color: #f5af19;
  animation: iconPulse 3s ease-in-out infinite;
  box-shadow: 0 0 40px rgba(245,175,25,.06), inset 0 0 20px rgba(245,175,25,.04);
}
@keyframes iconPulse {
  0%, 100% { box-shadow: 0 0 40px rgba(245,175,25,.06), inset 0 0 20px rgba(245,175,25,.04); }
  50% { box-shadow: 0 0 60px rgba(245,175,25,.12), inset 0 0 30px rgba(245,175,25,.08); }
}

.hero-title {
  font-size: 28px; font-weight: 700;
  background: linear-gradient(135deg, #f5af19 0%, #f7c84a 50%, #f5af19 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 12px;
  animation: titleIn .6s ease-out;
}
@keyframes titleIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
.hero-desc {
  font-size: 14px; color: rgba(255,255,255,.3); margin: 0 0 36px;
  animation: titleIn .6s ease-out .15s both;
}

/* ===== Search Box ===== */
.search-box {
  position: relative; z-index: 1;
  max-width: 560px; margin: 0 auto;
  animation: titleIn .6s ease-out .25s both;
}
.search-row {
  display: flex; gap: 10px; align-items: center; margin-bottom: 14px;
}
.search-input { flex: 1; }

.filter-row {
  display: flex; gap: 10px; align-items: center; justify-content: center;
}

.spoiler-label {
  font-size: 12px; color: rgba(255,255,255,.25); margin-right: 4px;
  letter-spacing: 1px; text-transform: uppercase;
}

.dot-toggle {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 16px; border-radius: 20px; border: 1px solid rgba(255,255,255,.08);
  background: rgba(255,255,255,.02); color: rgba(255,255,255,.35);
  font-size: 13px; cursor: pointer; transition: all .25s;
  backdrop-filter: blur(8px);
}
.dot-toggle::before {
  content: ''; width: 6px; height: 6px; border-radius: 50%;
  background: rgba(255,255,255,.15); transition: all .25s;
}
.dot-toggle:hover {
  border-color: rgba(255,255,255,.18); color: rgba(255,255,255,.6);
  background: rgba(255,255,255,.04);
}
.dot-toggle.active {
  border-color: #f5af19; color: #f5af19; background: rgba(245,175,25,.1);
  box-shadow: 0 0 12px rgba(245,175,25,.08);
}
.dot-toggle.active::before {
  background: #f5af19; box-shadow: 0 0 8px rgba(245,175,25,.5);
}

.search-btn {
  height: 40px; display: flex; align-items: center; gap: 6px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; color: #fff; padding: 0 24px; border-radius: 10px;
  font-size: 14px; font-weight: 600; cursor: pointer;
  transition: all .25s; white-space: nowrap;
  box-shadow: 0 4px 16px rgba(245,175,25,.15);
}
.search-btn:hover { opacity: .9; transform: translateY(-1px); box-shadow: 0 6px 20px rgba(245,175,25,.25); }
.search-btn:active { transform: scale(.97); }

/* Dark input overrides */
.search-box :deep(.el-input__wrapper) {
  background: rgba(255,255,255,.03) !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.08) inset !important;
  border-radius: 10px !important;
  transition: all .25s !important;
  backdrop-filter: blur(8px);
}
.search-box :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255,255,255,.14) inset !important;
  background: rgba(255,255,255,.05) !important;
}
.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f5af19 inset, 0 0 0 4px rgba(245,175,25,.06) !important;
  background: rgba(255,255,255,.05) !important;
}
.search-box :deep(.el-input__inner) { color: rgba(255,255,255,.75) !important; font-size: 15px !important; }
.search-box :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.2) !important; }

/* ===== Results ===== */
.search-results {
  position: relative; z-index: 1;
  margin-top: 20px;
  animation: fadeUp .4s ease-out;
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.results-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 24px; padding-bottom: 14px;
  border-bottom: 1px solid rgba(255,255,255,.06);
}
.results-header-left {
  display: flex; align-items: center; gap: 10px;
}
.results-icon {
  font-size: 18px; color: #f5af19; opacity: .7;
}
.results-title { font-size: 18px; color: #f5af19; margin: 0; font-weight: 600; }
.results-count { font-size: 12px; color: rgba(255,255,255,.25); background: rgba(255,255,255,.03); padding: 4px 12px; border-radius: 10px; }

.review-list { display: flex; flex-direction: column; gap: 16px; }

.review-card-wrapper {
  opacity: 0;
  animation: cardIn .45s ease-out forwards;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Loading state */
.loading-state {
  text-align: center; padding: 80px 0; color: rgba(255,255,255,.3);
}
.loading-spinner {
  display: block; width: 36px; height: 36px; margin: 0 auto 16px;
  border: 2px solid rgba(255,255,255,.08);
  border-top-color: #f5af19; border-radius: 50%;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.loading-state p { font-size: 14px; margin: 0; }

/* Empty state */
.empty-result { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.empty-visual {
  display: inline-flex; align-items: center; justify-content: center;
  width: 96px; height: 96px; border-radius: 50%;
  background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.06);
  color: rgba(255,255,255,.15); margin-bottom: 20px;
}
.empty-title { font-size: 15px; color: rgba(255,255,255,.35); margin: 0 0 8px; font-weight: 500; }
.empty-hint { font-size: 13px !important; color: rgba(255,255,255,.15) !important; margin: 0 !important; }

.pager { margin-top: 28px; justify-content: center; }

/* ===== Pagination dark overrides ===== */
:deep(.el-pagination.is-background .btn-prev),
:deep(.el-pagination.is-background .btn-next),
:deep(.el-pagination.is-background .el-pager li) {
  background: rgba(255,255,255,.04) !important;
  color: rgba(255,255,255,.5) !important;
  border-radius: 8px !important;
  transition: all .2s;
}
:deep(.el-pagination.is-background .btn-prev:hover),
:deep(.el-pagination.is-background .btn-next:hover),
:deep(.el-pagination.is-background .el-pager li:hover) {
  background: rgba(255,255,255,.08) !important;
  color: rgba(255,255,255,.75) !important;
}
:deep(.el-pagination.is-background .el-pager li.is-active) {
  background: linear-gradient(135deg, #f5af19, #f12711) !important;
  color: #fff !important;
}
:deep(.el-pagination .el-input__wrapper) {
  background: rgba(255,255,255,.04) !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.08) inset !important;
}
:deep(.el-pagination .el-input__inner) { color: rgba(255,255,255,.7) !important; }
</style>
