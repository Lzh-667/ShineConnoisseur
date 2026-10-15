<script setup>
defineProps({ compact: { type: Boolean, default: false } })
</script>

<template>
  <span class="vip-badge" :class="{ compact }" title="光影 VIP 会员" aria-label="VIP 会员">
    <span class="vip-gem">◆</span><span v-if="!compact">VIP</span>
  </span>
</template>

<style scoped>
.vip-badge { display:inline-flex; align-items:center; gap:3px; flex-shrink:0; height:18px; padding:0 7px; border:1px solid rgba(255,211,106,.46); border-radius:9px; background:linear-gradient(135deg,rgba(245,175,25,.2),rgba(241,39,17,.15)); color:#ffd36a; box-shadow:inset 0 0 8px rgba(245,175,25,.08),0 0 8px rgba(245,175,25,.08); font-size:9px; font-weight:800; line-height:1; letter-spacing:.6px; vertical-align:middle; }
.vip-badge.compact { width:17px; padding:0; justify-content:center; }
.vip-gem { font-size:7px; transform:rotate(45deg); }
</style>
