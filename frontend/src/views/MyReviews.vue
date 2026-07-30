<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyReviews, deleteReview, updateReview } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const current = ref(1)
const hasMore = ref(false)

const editVisible = ref(false)
const editId = ref(null)
const editForm = reactive({ rating: null, title: '', content: '', spoiler: 0 })
const editLoading = ref(false)

async function load(page = 1) {
  loading.value = true
  try {
    const res = await getMyReviews(page)
    if (page === 1) list.value = res.data?.list ?? []
    else list.value.push(...(res.data?.list ?? []))
    hasMore.value = res.data?.hasMore ?? false
    current.value = page
  } finally { loading.value = false }
}

function loadMore() { if (hasMore.value) load(current.value + 1) }

function handleEdit(r) {
  editId.value = r.id
  editForm.rating = r.rating
  editForm.title = r.title
  editForm.content = r.content
  editForm.spoiler = r.spoiler ?? 0
  editVisible.value = true
}

async function handleEditSubmit() {
  if (!editForm.rating) { ElMessage.warning('请评分'); return }
  if (!editForm.title.trim()) { ElMessage.warning('请输入标题'); return }
  if (!editForm.content.trim()) { ElMessage.warning('请输入内容'); return }
  editLoading.value = true
  try {
    await updateReview(editId.value, editForm)
    ElMessage.success('影评已修改')
    editVisible.value = false
    load()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally { editLoading.value = false }
}

async function handleDelete(r) {
  try {
    await ElMessageBox.confirm('确定删除该影评吗？', '提示', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteReview(r.id)
    ElMessage.success('已删除')
    load()
  } catch { /* cancelled */ }
}

function formatTime(t) { return t ? new Date(t).toLocaleDateString('zh-CN') : '' }

onMounted(load)
</script>

<template>
  <div class="list-page">
    <h2 class="page-title">我的影评</h2>
    <div v-if="loading" class="loading-text">加载中...</div>
    <div v-else-if="list.length === 0" class="empty">暂无影评</div>
    <template v-else>
      <div class="review-list">
        <div v-for="r in list" :key="r.id" class="review-item" @click="router.push(`/reviews/${r.id}`)">
          <div class="item-left">
            <span class="rating-badge">★ {{ r.rating }}</span>
            <div class="item-body">
              <span class="item-title">{{ r.title }}</span>
              <span class="item-meta">{{ formatTime(r.createTime) }} · ❤ {{ r.likeCount }} · 💬 {{ r.commentCount }}</span>
            </div>
          </div>
          <div class="item-actions">
            <el-button text size="small" @click.stop="handleEdit(r)">编辑</el-button>
            <el-button text type="danger" size="small" @click.stop="handleDelete(r)">删除</el-button>
          </div>
        </div>
      </div>
      <button v-if="hasMore" class="load-more-btn" @click="loadMore">加载更多</button>
    </template>

    <el-dialog v-model="editVisible" width="480px" :close-on-click-modal="false">
      <template #header>
        <div class="dialog-title">编辑影评</div>
      </template>
      <div class="edit-body">
        <div class="form-item">
          <label>评分</label>
          <el-rate v-model="editForm.rating" :max="10" show-score score-template="{value}分" />
        </div>
        <el-input v-model="editForm.title" placeholder="输入影评标题" maxlength="50" show-word-limit />
        <el-input v-model="editForm.content" type="textarea" placeholder="写下你对该电影的感想..." :rows="5" resize="none" />
        <div class="spoiler-row">
          <el-checkbox v-model="editForm.spoiler" :true-value="1" :false-value="0" />
          <span class="spoiler-label">含剧透内容</span>
        </div>
        <button class="edit-submit" :disabled="editLoading" @click="handleEditSubmit">
          {{ editLoading ? '保存中...' : '保存修改' }}
        </button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-page { max-width: 680px; margin: 0 auto; padding: 40px 24px 80px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0 0 24px; }

.review-list { display: flex; flex-direction: column; gap: 4px; }
.review-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; background: rgba(255,255,255,.02); border-radius: 10px;
  cursor: pointer; transition: background .2s;
}
.review-item:hover { background: rgba(255,255,255,.06); }
.item-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.rating-badge { font-size: 12px; font-weight: 600; color: #f5af19; background: rgba(245,175,25,.1); padding: 2px 8px; border-radius: 8px; flex-shrink: 0; }
.item-body { display: flex; flex-direction: column; min-width: 0; }
.item-title { font-size: 14px; color: rgba(255,255,255,.8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: 12px; color: rgba(255,255,255,.35); margin-top: 2px; }

.loading-text, .empty { text-align: center; padding: 80px 0; color: rgba(255,255,255,.3); }
.load-more-btn { display: block; width: 100%; background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.06); color: rgba(255,255,255,.5); font-size: 13px; cursor: pointer; padding: 10px; border-radius: 8px; margin-top: 12px; transition: all .2s; }
.load-more-btn:hover { background: rgba(255,255,255,.08); color: rgba(255,255,255,.7); }

.item-actions { display: flex; gap: 4px; flex-shrink: 0; }

:deep(.el-dialog) { background: #1e1e30; border: 1px solid rgba(255,255,255,.08); border-radius: 16px; }
:deep(.el-dialog__header) { margin: 0; padding: 28px 28px 0; }
.dialog-title { font-size: 18px; font-weight: 700; color: #f5af19; }

:deep(.el-dialog__body) { padding: 24px 28px 28px; }

.edit-body { display: flex; flex-direction: column; gap: 16px; }
.edit-body .form-item { display: flex; flex-direction: column; gap: 6px; }
.edit-body .form-item label { font-size: 13px; color: rgba(255,255,255,.5); }

.edit-body :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.12) inset !important;
  border-radius: 8px !important;
}
.edit-body :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px rgba(255,255,255,.2) inset !important; }
.edit-body :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.edit-body :deep(.el-input__inner) { color: rgba(255,255,255,.7) !important; }
.edit-body :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.25) !important; }
.edit-body :deep(.el-textarea__inner) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.12) inset !important;
  color: rgba(255,255,255,.7) !important; border-radius: 8px !important;
}
.edit-body :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.edit-body :deep(.el-textarea__inner::placeholder) { color: rgba(255,255,255,.25) !important; }

.spoiler-row { display: flex; align-items: center; gap: 6px; }
.spoiler-label { font-size: 13px; color: rgba(255,255,255,.4); }

.edit-submit {
  width: 100%; padding: 12px; background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 10px; font-size: 16px; font-weight: 600; color: #fff;
  cursor: pointer; transition: opacity .2s;
}
.edit-submit:hover { opacity: .85; }
.edit-submit:disabled { opacity: .5; cursor: not-allowed; }
</style>
