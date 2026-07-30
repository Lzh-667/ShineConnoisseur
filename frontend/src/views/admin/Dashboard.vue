<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '../../api/admin'
import { User, Film, StarFilled, EditPen, Collection } from '@element-plus/icons-vue'

const data = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getDashboard()
    data.value = res.data
  } finally { loading.value = false }
}

function fmt(n) { return (n ?? 0).toLocaleString() }

const cards = [
  { key: 'userCount', label: '用户总数', icon: User, gradient: 'linear-gradient(135deg, #667eea, #764ba2)' },
  { key: 'movieCount', label: '电影总数', icon: Film, gradient: 'linear-gradient(135deg, #11998e, #38ef7d)' },
  { key: 'reviewCount', label: '影评总数', icon: EditPen, gradient: 'linear-gradient(135deg, #f5af19, #f12711)' },
  { key: 'todayReviewCount', label: '今日新增影评', icon: StarFilled, gradient: 'linear-gradient(135deg, #4facfe, #00f2fe)' },
  { key: 'weekReviewCount', label: '本周新增影评', icon: Collection, gradient: 'linear-gradient(135deg, #fa709a, #fee140)' },
]

onMounted(load)
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <div>
        <h2 class="page-title">控制台</h2>
        <p class="page-desc">数据概览</p>
      </div>
    </div>

    <div v-if="loading && !data" class="loading-state">
      <span class="loader"></span>
      <span>加载中...</span>
    </div>

    <div v-else-if="data" class="stats-grid">
      <div v-for="card in cards" :key="card.key" class="stat-card">
        <div class="card-icon" :style="{ background: card.gradient }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="card-body">
          <span class="stat-num">{{ fmt(data[card.key]) }}</span>
          <span class="stat-label">{{ card.label }}</span>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 32px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0; }
.page-desc { font-size: 13px; color: rgba(255,255,255,.25); margin: 4px 0 0; }

@keyframes spin { to { transform: rotate(360deg); } }

.stats-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 20px;
}
.stat-card {
  background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.05);
  border-radius: 16px; padding: 24px; display: flex; align-items: center; gap: 18px;
  transition: all .25s ease; cursor: default;
}
.stat-card:hover { transform: translateY(-3px); border-color: rgba(255,255,255,.1); background: rgba(255,255,255,.035); }

.card-icon {
  width: 52px; height: 52px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  font-size: 24px; color: #fff;
}
.card-body { display: flex; flex-direction: column; }
.stat-num { font-size: 28px; font-weight: 700; color: #fff; font-variant-numeric: tabular-nums; line-height: 1.1; }
.stat-label { font-size: 13px; color: rgba(255,255,255,.35); margin-top: 4px; }

.loading-state {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 100px 0; color: rgba(255,255,255,.25); font-size: 14px;
}
.loader {
  width: 20px; height: 20px; border: 2px solid rgba(255,255,255,.1);
  border-top-color: #f5af19; border-radius: 50%; animation: spin .8s linear infinite;
}
</style>
