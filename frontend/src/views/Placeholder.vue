<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="not-found-page">
    <div class="not-found-content">
      <div class="not-found-code">404</div>
      <h2>页面未找到</h2>
      <p>你访问的页面不存在或已被移除</p>
      <el-button class="not-found-btn" round @click="router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  display: flex; justify-content: center; align-items: center;
  min-height: calc(100vh - 64px);
}
.not-found-content { text-align: center; }
.not-found-code {
  font-size: 120px; font-weight: 800; line-height: 1;
  background: linear-gradient(135deg, #f5af19, #f12711);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
}
.not-found-page h2 { font-size: 24px; color: rgba(255,255,255,.8); margin: 0 0 8px; }
.not-found-page p { font-size: 14px; color: rgba(255,255,255,.3); margin: 0 0 32px; }
.not-found-btn {
  --el-button-bg-color: #f5af19;
  --el-button-border-color: #f5af19;
  --el-button-hover-bg-color: #f7c04a;
  --el-button-hover-border-color: #f7c04a;
  font-weight: 600;
}
</style>
