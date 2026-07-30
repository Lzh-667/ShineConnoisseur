<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getMessages, markMessageRead, markAllMessagesRead, getCommentTarget } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const current = ref(1)
const total = ref(0)
const activeType = ref('')

const typeLabels = { 0: '关注', 1: '点赞影评', 2: '评论影评', 3: '点赞评论', 4: '回复评论' }
const types = [
  { value: '', label: '全部' },
  { value: '0', label: '关注' },
  { value: '1', label: '点赞影评' },
  { value: '2', label: '评论影评' },
  { value: '3', label: '点赞评论' },
  { value: '4', label: '回复评论' },
]

async function load() {
  loading.value = true
  try {
    const res = await getMessages(current.value, activeType.value || undefined)
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally { loading.value = false }
}

function onPageChange(page) { current.value = page; load() }
function onTypeChange() { current.value = 1; load() }

async function handleMarkRead(msg) {
  if (msg.status === 1) return
  try {
    await markMessageRead(msg.id)
    msg.status = 1
    userStore.decreaseUnread(1)
  } catch { /* already shown */ }
}

async function handleMarkAllRead() {
  try {
    await markAllMessagesRead()
    const unreadBefore = list.value.filter(m => m.status === 0).length
    list.value.forEach(m => m.status = 1)
    userStore.decreaseUnread(unreadBefore)
    ElMessage.success('已全部标记已读')
  } catch { /* already shown */ }
}

async function handleClick(msg) {
  if (msg.status === 0) handleMarkRead(msg)
  const { type, targetType, targetId, fromUser } = msg
  if (type === 0 || targetType === 0) {
    router.push(`/users/${fromUser.id}`)
  } else if (targetType === 1) {
    router.push(`/reviews/${targetId}`)
  } else if (targetType === 2) {
    try {
      const res = await getCommentTarget(targetId)
      router.push(`/reviews/${res.data.reviewId}`)
    } catch { ElMessage.warning('无法获取影评信息') }
  }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const pageSize = 10

onMounted(load)
</script>

<template>
  <div class="messages-page">
    <div class="page-title-row">
      <h2>消息中心</h2>
      <el-button v-if="list.length" text @click="handleMarkAllRead">全部已读</el-button>
    </div>

    <div class="type-tabs">
      <button v-for="t in types" :key="t.value" class="type-tab" :class="{ active: activeType === t.value }" @click="activeType = t.value; onTypeChange()">{{ t.label }}</button>
    </div>

    <div v-if="loading" class="loading-text">加载中...</div>

    <div v-else-if="list.length === 0" class="empty">暂无消息</div>

    <template v-else>
      <div class="msg-list">
        <div v-for="msg in list" :key="msg.id" class="msg-item" :class="{ unread: msg.status === 0 }" @click="handleClick(msg)">
          <el-avatar :size="40" :src="msg.fromUser?.avatar" />
          <div class="msg-body">
            <div class="msg-top">
              <span class="msg-user">{{ msg.fromUser?.nickname || msg.fromUser?.username }}</span>
              <span class="msg-type-tag">{{ typeLabels[msg.type] || msg.type }}</span>
            </div>
            <div class="msg-content">{{ msg.content }}</div>
            <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="total > pageSize"
        class="pager"
        background layout="prev, pager, next"
        :total="total" :page-size="pageSize"
        :current-page="current" @current-change="onPageChange"
      />
    </template>
  </div>
</template>

<style scoped>
.messages-page { max-width: 680px; margin: 0 auto; padding: 40px 24px 80px; }

.page-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title-row h2 { font-size: 22px; color: #f5af19; margin: 0; }

.type-tabs { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.type-tab {
  background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.08);
  color: rgba(255,255,255,.6); padding: 6px 16px; border-radius: 20px;
  font-size: 13px; cursor: pointer; transition: all .2s;
}
.type-tab:hover { background: rgba(255,255,255,.1); color: #fff; }
.type-tab.active { background: rgba(245,175,25,.15); border-color: rgba(245,175,25,.4); color: #f5af19; }

.msg-list { display: flex; flex-direction: column; gap: 4px; }

.msg-item {
  display: flex; gap: 12px; padding: 14px 16px; border-radius: 10px;
  cursor: pointer; transition: background .2s; background: rgba(255,255,255,.02);
  position: relative;
}
.msg-item:hover { background: rgba(255,255,255,.06); }
.msg-item.unread { background: rgba(245,175,25,.04); }
.msg-item.unread::before { content: ''; position: absolute; left: 8px; top: 50%; transform: translateY(-50%); width: 6px; height: 6px; background: #f5af19; border-radius: 50%; }

.msg-body { flex: 1; min-width: 0; }
.msg-top { display: flex; justify-content: space-between; align-items: center; }
.msg-user { font-size: 14px; font-weight: 600; color: rgba(255,255,255,.85); }
.msg-type-tag { font-size: 11px; color: rgba(255,255,255,.35); background: rgba(255,255,255,.05); padding: 1px 8px; border-radius: 10px; }
.msg-content { font-size: 13px; color: rgba(255,255,255,.55); margin: 4px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-time { font-size: 11px; color: rgba(255,255,255,.3); }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.pager { margin-top: 24px; justify-content: center; }
</style>
