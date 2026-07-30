<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyComments, deleteComment, getCommentTarget } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const current = ref(1)
const hasMore = ref(false)

async function load(page = 1) {
  loading.value = true
  try {
    const res = await getMyComments(page)
    if (page === 1) list.value = res.data?.list ?? []
    else list.value.push(...(res.data?.list ?? []))
    hasMore.value = res.data?.hasMore ?? false
    current.value = page
  } finally { loading.value = false }
}

function loadMore() { if (hasMore.value) load(current.value + 1) }

async function handleClick(c) {
  try {
    const res = await getCommentTarget(c.id)
    router.push(`/reviews/${res.data.reviewId}`)
  } catch { ElMessage.warning('无法获取影评信息') }
}

async function handleDelete(c) {
  try {
    await ElMessageBox.confirm('确定删除该评论吗？', '提示', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteComment(c.id)
    ElMessage.success('已删除')
    list.value = list.value.filter(item => item.id !== c.id)
    if (list.value.length === 0 && hasMore.value) load()
  } catch { /* cancelled */ }
}

function formatTime(t) { return t ? new Date(t).toLocaleDateString('zh-CN') : '' }

onMounted(load)
</script>

<template>
  <div class="list-page">
    <h2 class="page-title">我的评论</h2>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="list.length === 0" class="empty">暂无评论</div>
    <template v-else>
      <div class="comment-list">
        <div v-for="c in list" :key="c.id" class="comment-item" @click="handleClick(c)">
          <div class="item-body">
            <span class="item-content">{{ c.content }}</span>
            <span class="item-meta">{{ formatTime(c.createTime) }} · ❤ {{ c.likeCount }}</span>
          </div>
          <el-button text type="danger" size="small" @click.stop="handleDelete(c)">删除</el-button>
        </div>
      </div>
      <button v-if="hasMore" class="load-more-btn" @click="loadMore">加载更多</button>
    </template>
  </div>
</template>

<style scoped>
.list-page { max-width: 680px; margin: 0 auto; padding: 40px 24px 80px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0 0 24px; }

.comment-list { display: flex; flex-direction: column; gap: 4px; }
.comment-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; background: rgba(255,255,255,.02); border-radius: 10px;
  cursor: pointer; transition: background .2s;
}
.comment-item:hover { background: rgba(255,255,255,.06); }
.item-body { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.item-content { font-size: 14px; color: rgba(255,255,255,.8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: 12px; color: rgba(255,255,255,.35); margin-top: 2px; }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.load-more-btn { display: block; width: 100%; background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.06); color: rgba(255,255,255,.5); font-size: 13px; cursor: pointer; padding: 10px; border-radius: 8px; margin-top: 12px; transition: all .2s; }
.load-more-btn:hover { background: rgba(255,255,255,.08); color: rgba(255,255,255,.7); }
</style>
