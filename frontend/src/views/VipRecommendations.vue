<script setup>
import { ref, onMounted } from 'vue'
import { getMyVip, getVipRecommendations } from '../api'

const loading = ref(false)
const active = ref(false)
const failed = ref(false)
const result = ref(null)
async function load() {
  if (loading.value) return
  loading.value = true
  failed.value = false
  result.value = null
  try {
    const membership = await getMyVip()
    active.value = membership.data?.vip === true
    if (active.value) result.value = (await getVipRecommendations()).data
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="recommendations" v-loading="loading">
    <header><div><span class="eyebrow">VIP 专属权益</span><h1>为你发现下一部好电影</h1>
      <p>结合收藏与影评评分，推荐尚未收藏、尚未评价的电影。</p></div>
      <el-button :loading="loading" @click="load">刷新推荐</el-button></header>
    <el-result v-if="failed" icon="warning" title="推荐加载失败" sub-title="请重试；会员到期后需续订才能继续使用。" />
    <section v-else-if="!loading && !active" class="locked">
      <h2>开通 VIP，解锁专属推荐</h2><p>你的收藏和评分越丰富，推荐越贴近你的喜好。</p>
      <router-link to="/vip"><el-button type="warning">查看会员套餐</el-button></router-link>
    </section>
    <template v-else-if="result">
      <p v-if="result.preferredGenres.length" class="basis">你的偏好类型：{{ result.preferredGenres.join(' · ') }}</p>
      <p v-else-if="result.personalized" class="basis">已结合你的低评分降低相关类型的推荐优先级，优先探索其他热门电影。</p>
      <p v-else class="basis">暂未形成明确偏好，先探索站内热门候选。收藏喜欢的电影、提交真实评分后可刷新推荐。</p>
      <div class="movie-grid">
        <router-link v-for="item in result.records" :key="item.movie.id" :to="`/movies/${item.movie.id}`" class="movie">
          <el-image :src="item.movie.cover || ''" fit="cover" class="poster"><template #error><div class="no-cover">暂无海报</div></template></el-image>
          <div class="details"><h3>{{ item.movie.title }}</h3><span>{{ item.movie.genre || '未分类' }} · {{ item.movie.rating ?? '暂无评分' }}</span>
          <p>{{ item.reason }}</p></div>
        </router-link>
      </div>
      <el-empty v-if="!result.records.length" description="暂时没有可推荐的新电影，去电影库看看吧" />
    </template>
  </div>
</template>

<style scoped>
.recommendations { max-width:1120px; margin:auto; padding:40px 24px; min-height:400px; }
header { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:30px; }
h1 { font-size:28px; margin:10px 0; } header p,.locked p { color:#aaa; }
.eyebrow,.basis { color:#ffd36a; }.basis { margin-bottom:20px; line-height:1.8; }
.locked { text-align:center; padding:60px 20px; }.locked p { margin:20px 0; }
.movie-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:20px; }
.movie { border:1px solid #ffffff18; border-radius:14px; overflow:hidden; background:#ffffff06; }
.movie:hover { border-color:#f5af1970; }.poster { width:100%; height:260px; }.no-cover { display:grid; place-items:center; height:100%; color:#888; }
.details { padding:16px; }.details h3 { margin:0 0 8px; }.details span { color:#aaa; font-size:12px; }.details p { color:#dfc68c; font-size:13px; line-height:1.6; margin-top:12px; }
@media(max-width:760px) { .movie-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } header { align-items:flex-start; flex-direction:column; } }
@media(max-width:440px) { .movie-grid { grid-template-columns:1fr; } }
</style>
