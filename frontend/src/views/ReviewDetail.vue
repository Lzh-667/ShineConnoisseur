<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getReviewDetail, likeReview, getRootComments, publishComment, deleteReview, updateReview } from '../api'
import CommentItem from '../components/CommentItem.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const review = ref(null)
const reviewLoading = ref(false)
const comments = ref([])
const commentLoading = ref(false)
const commentHasMore = ref(false)
const newComment = ref('')
const commentSending = ref(false)
const notFound = ref(false)

const editVisible = ref(false)
const editForm = reactive({ rating: null, title: '', content: '', spoiler: 0 })
const editLoading = ref(false)

async function loadReview() {
  reviewLoading.value = true
  try {
    const res = await getReviewDetail(route.params.id)
    review.value = res.data
  } catch { notFound.value = true } finally { reviewLoading.value = false }
}

async function loadComments(page = 1) {
  commentLoading.value = true
  try {
    const res = await getRootComments(route.params.id, page)
    if (page === 1) comments.value = res.data?.list ?? []
    else comments.value.push(...(res.data?.list ?? []))
    commentHasMore.value = res.data?.hasMore ?? false
  } finally { commentLoading.value = false }
}

function loadMoreComments() {
  if (commentHasMore.value) {
    const nextPage = Math.floor(comments.value.length / 10) + 1
    loadComments(nextPage)
  }
}

async function handleLike() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  try {
    const res = await likeReview(review.value.id)
    review.value.isLike = res.data.like
    review.value.likeCount = res.data.likeCount
  } catch {}
}

async function handlePublish() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  if (!newComment.value.trim()) return
  commentSending.value = true
  try {
    await publishComment(review.value.id, { rootId: 0, content: newComment.value.trim() })
    newComment.value = ''
    review.value.commentCount = (review.value.commentCount ?? 0) + 1
    ElMessage.success('评论已发布')
    loadComments(1)
  } finally { commentSending.value = false }
}

function handleEdit() {
  editForm.rating = review.value.rating
  editForm.title = review.value.title
  editForm.content = review.value.content
  editForm.spoiler = review.value.spoiler ?? 0
  editVisible.value = true
}

async function handleEditSubmit() {
  if (!editForm.rating) { ElMessage.warning('请评分'); return }
  if (!editForm.title.trim()) { ElMessage.warning('请输入标题'); return }
  if (!editForm.content.trim()) { ElMessage.warning('请输入内容'); return }
  editLoading.value = true
  try {
    await updateReview(review.value.id, editForm)
    ElMessage.success('影评已修改')
    editVisible.value = false
    loadReview()
  } finally { editLoading.value = false }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除该影评吗？', '提示', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteReview(review.value.id)
    ElMessage.success('已删除')
    router.back()
  } catch {}
}

function handleCommentDeleted() {
  review.value.commentCount = Math.max(0, (review.value.commentCount ?? 1) - 1)
  loadComments(1)
}
function handleReplyAdded() {
  review.value.commentCount = (review.value.commentCount ?? 0) + 1
}
function formatTime(t) { return t ? new Date(t).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', year: 'numeric' }) : '' }

onMounted(() => { loadReview(); loadComments() })
</script>

<template>
  <div class="review-detail-page">
    <div v-if="reviewLoading" class="loading-text">加载中...</div>

    <div v-else-if="notFound" class="empty">影评不存在或已删除</div>

    <template v-else-if="review">
      <!-- review header -->
      <article class="review-full">
        <header class="review-full-header">
          <div class="author-row" @click="router.push(`/users/${review.userId}`)">
            <el-avatar :size="48" :src="review.avatar" />
            <div class="author-info">
              <span class="author-name">{{ review.nickName || review.userName }}</span>
              <span class="author-date">{{ formatTime(review.createTime) }}</span>
            </div>
          </div>
          <div class="rating-badge">★ {{ review.rating }}</div>
        </header>

        <h1 class="review-title">
          <span v-if="review.spoiler" class="spoiler-tag">剧透</span>
          {{ review.title }}
        </h1>

        <div v-if="review.movieTitle" class="movie-link" @click="router.push(`/movies/${review.movieId}`)">
          <span class="movie-link-icon">🎬</span>
          <span>来自：{{ review.movieTitle }}</span>
        </div>

        <p class="review-content">{{ review.content }}</p>

        <footer class="review-full-footer">
          <button class="action-btn" :class="{ active: review.isLike }" @click="handleLike">❤ {{ review.likeCount || '' }}</button>
          <span class="action-info">💬 {{ review.commentCount || 0 }} 条评论</span>
          <div v-if="review.canEditAndDelete" class="owner-actions">
            <el-button text size="small" @click="handleEdit">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete">删除</el-button>
          </div>
        </footer>
      </article>

      <el-dialog v-model="editVisible" width="480px" :close-on-click-modal="false" class="dialog-gradient-header">
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

      <!-- comment input -->
      <div v-if="userStore.isLoggedIn" class="comment-input-area">
        <el-input v-model="newComment" placeholder="写下你的评论..." type="textarea" :rows="3" resize="none" />
        <el-button class="publish-btn" type="primary" :loading="commentSending" :disabled="!newComment.trim()" @click="handlePublish">发布评论</el-button>
      </div>
      <div v-else class="login-hint">请先<a @click="router.push({ name: 'Login', query: { redirect: route.fullPath } })">登录</a>后评论</div>

      <!-- comments -->
      <div v-if="commentLoading && comments.length === 0" class="loading-text">加载评论中...</div>
      <div v-else-if="comments.length === 0" class="empty-comments">暂无评论，来写第一条吧</div>
      <div v-else class="comments-section">
        <CommentItem v-for="c in comments" :key="c.id" :comment="c" :review-id="review?.id" @deleted="handleCommentDeleted" @reply-added="handleReplyAdded" />
        <button v-if="commentHasMore" class="load-more-btn" :class="{ loading: commentLoading }" @click="loadMoreComments">加载更多评论</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.review-detail-page { max-width: 680px; margin: 0 auto; padding: 40px 24px 80px; }

/* full review */
.review-full { background: rgba(255,255,255,.03); border-radius: 12px; padding: 32px; margin-bottom: 32px; }
.review-full-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }

.author-row { display: flex; align-items: center; gap: 12px; cursor: pointer; }
.author-info { display: flex; flex-direction: column; }
.author-name { font-size: 15px; font-weight: 600; color: rgba(255,255,255,.85); }
.author-row:hover .author-name { color: #f5af19; }
.author-date { font-size: 12px; color: rgba(255,255,255,.35); margin-top: 2px; }

.rating-badge { display: flex; align-items: center; gap: 4px; background: linear-gradient(135deg, #f5af19, #f12711); color: #fff; padding: 6px 16px; border-radius: 20px; font-size: 16px; font-weight: 700; flex-shrink: 0; }

.spoiler-tag {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  color: #ff6b6b;
  background: rgba(255,107,107,.15);
  padding: 2px 10px;
  border-radius: 8px;
  margin-right: 8px;
  vertical-align: middle;
}

.review-title { font-size: 24px; font-weight: 700; color: rgba(255,255,255,.9); margin: 0 0 12px; line-height: 1.4; }

.movie-link { display: inline-flex; align-items: center; gap: 5px; font-size: 13px; color: #f5af19; background: rgba(245,175,25,.08); padding: 4px 12px; border-radius: 12px; margin-bottom: 16px; cursor: pointer; transition: background .2s; }
.movie-link:hover { background: rgba(245,175,25,.15); }
.movie-link-icon { font-size: 12px; }

.review-content { font-size: 16px; color: rgba(255,255,255,.65); line-height: 2; white-space: pre-wrap; word-break: break-word; margin: 0; }

.review-full-footer { display: flex; gap: 24px; padding-top: 20px; margin-top: 20px; border-top: 1px solid rgba(255,255,255,.06); }
.action-btn { display: flex; align-items: center; gap: 4px; background: none; border: none; color: rgba(255,255,255,.4); font-size: 14px; cursor: pointer; padding: 6px 12px; border-radius: 8px; transition: all .2s; }
.action-btn:hover { background: rgba(255,255,255,.06); }
.action-btn.active { color: #f56c6c; }
.action-info { font-size: 14px; color: rgba(255,255,255,.35); align-self: center; }

/* comment input */
.comment-input-area { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; margin-bottom: 40px; }
.comment-input-area :deep(.el-textarea) { width: 100%; }
.comment-input-area :deep(.el-textarea__inner) {
  background: rgba(255,255,255,.04) !important;
  color: rgba(255,255,255,.7) !important;
  border-radius: 8px !important;
}
.comment-input-area :deep(.el-textarea__inner::placeholder) { color: rgba(255,255,255,.25) !important; }
.publish-btn {
  min-width: 100px;
  --el-button-bg-color: #f5af19;
  --el-button-border-color: #f5af19;
  --el-button-hover-bg-color: #f7c04a;
  --el-button-hover-border-color: #f7c04a;
}

.login-hint { text-align: center; padding: 24px; color: rgba(255,255,255,.3); font-size: 14px; }
.login-hint a { color: #409eff; cursor: pointer; }

/* comments */
.comments-section { margin-bottom: 24px; }
.loading-text, .empty, .empty-comments { text-align: center; padding: 60px 0; color: rgba(255,255,255,.3); }
.pager { justify-content: center; margin-top: 24px; }
.load-more-btn { display: block; width: 100%; background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.06); color: rgba(255,255,255,.5); font-size: 13px; cursor: pointer; padding: 10px; border-radius: 8px; margin-top: 12px; transition: all .2s; }
.load-more-btn:hover { background: rgba(255,255,255,.08); color: rgba(255,255,255,.7); }
.load-more-btn.loading { opacity: .5; pointer-events: none; }

.owner-actions { margin-left: auto; display: flex; gap: 4px; }

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
