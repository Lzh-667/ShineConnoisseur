<script setup>
defineOptions({ inheritAttrs: false })
defineProps({
  src: { type: String, default: '' },
  size: { type: [Number, String], default: 40 },
  vip: { type: Boolean, default: false },
})
</script>

<template>
  <span v-bind="$attrs" class="user-avatar" :class="{ vip }">
    <el-avatar :size="size" :src="src"><slot /></el-avatar>
  </span>
</template>

<style scoped>
.user-avatar { display:inline-flex; flex:0 0 auto; border-radius:50%; line-height:0; }
.user-avatar.vip { padding:2px; background:linear-gradient(135deg,#ffe08a,#f5af19 48%,#f12711); box-shadow:0 0 0 1px rgba(245,175,25,.18),0 0 14px rgba(245,175,25,.24); }
.user-avatar.vip :deep(.el-avatar) { border:2px solid #171726; }
</style>
