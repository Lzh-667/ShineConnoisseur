<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getChildComments, publishComment, likeComment, deleteComment } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  comment: { type: Object, required: true },
  reviewId: { type: [String, Number], required: true },
})
const emit = defineEmits(['deleted', 'reply-added'])

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const rootId = computed(() => props.comment.rootId === 0 ? props.comment.id : props.comment.rootId)
const replyToLabel = computed(() => props.comment.author?.nickname || props.comment.author?.username || '用户')
const isRoot = computed(() => props.comment.rootId === props.comment.id)

const showReplyBox = ref(false)
const replyContent = ref('')
const replyLoading = ref(false)
const childComments = ref([])
const childLoading = ref(false)
const childLoaded = ref(false)
const childHasMore = ref(false)

function formatTime(t) { return t ? new Date(t).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '' }

async function loadChildren(page = 1) {
  childLoading.value = true
  try {
    const res = await getChildComments(props.comment.id, page)
    const list = res.data?.list ?? []
    if (page === 1) childComments.value = list
    else childComments.value.push(...list)
    childHasMore.value = res.data?.hasMore ?? false
    childLoaded.value = true
  } finally { childLoading.value = false }
}

async function loadMoreChildren() {
  if (childHasMore.value) {
    const nextPage = Math.ceil((childComments.value.length + 1) / 10)
    await loadChildren(nextPage)
  }
}

function toggleChildren() {
  if (childLoaded.value) { childLoaded.value = false; childComments.value = []; childHasMore.value = false }
  else loadChildren(1)
}

async function handleReply() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  if (!replyContent.value.trim()) return
  replyLoading.value = true
  try {
    await publishComment(props.reviewId, { rootId: rootId.value, replyUserId: props.comment.author?.id, content: replyContent.value.trim() })
    replyContent.value = ''
    showReplyBox.value = false
    ElMessage.success('回复成功')
    if (isRoot.value) {
      childComments.value = []
      childHasMore.value = false
      childLoaded.value = false
      loadChildren(1)
    }
    emit('reply-added')
  } finally { replyLoading.value = false }
}

function onChildReplyAdded() {
  childComments.value = []
  childHasMore.value = false
  childLoaded.value = false
  loadChildren(1)
  emit('reply-added')
}

function onChildDeleted() {
  childComments.value = []
  childHasMore.value = false
  childLoaded.value = false
  loadChildren(1)
  emit('deleted')
}

async function handleLike() {
  if (!userStore.isLoggedIn) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  try {
    const res = await likeComment(props.comment.id)
    props.comment.isLike = res.data.like
    props.comment.likeCount = res.data.likeCount
  } catch {}
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除该评论吗？', '提示', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteComment(props.comment.id)
    ElMessage.success('已删除')
    emit('deleted')
  } catch {}
}

function goToUser(id) { router.push(`/users/${id}`) }
</script>

<template>
  <div class="comment-item">
    <div class="comment-main">
      <el-avatar :size="32" :src="comment.author?.avatar" class="comment-avatar" @click="goToUser(comment.author?.id)" />
      <div class="comment-body">
        <div class="comment-top">
          <span class="comment-author" @click="goToUser(comment.author?.id)">{{ comment.author?.nickname || comment.author?.username }}</span>
          <span v-if="comment.replyUser" class="reply-target">回复 <span class="link" @click="goToUser(comment.replyUser.id)">@{{ comment.replyUser.nickname || comment.replyUser.username }}</span></span>
          <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
        </div>
        <p class="comment-content">{{ comment.content }}</p>
        <div class="comment-actions">
          <button :class="{ active: comment.isLike }" @click="handleLike">❤ {{ comment.likeCount || '' }}</button>
          <button @click="showReplyBox = !showReplyBox">回复</button>
          <button v-if="comment.canEditAndDelete" class="danger" @click="handleDelete">删除</button>
        </div>

        <div v-if="showReplyBox" class="reply-input-box">
          <p class="reply-to-label">回复 <strong>@{{ replyToLabel }}</strong></p>
          <el-input v-model="replyContent" placeholder="写下你的回复..." size="small" @keyup.enter="handleReply" />
          <div class="reply-actions">
            <el-button size="small" @click="showReplyBox = false">取消</el-button>
            <el-button size="small" type="primary" :loading="replyLoading" @click="handleReply">回复</el-button>
          </div>
        </div>

        <div v-if="isRoot && comment.replyCount > 0" class="child-section">
          <button class="toggle-children" :class="{ loading: childLoading }" @click="toggleChildren">
            {{ childLoaded && childComments.length > 0 ? `收起回复 (${childComments.length})` : (childLoaded ? '收起回复' : `查看回复${ comment.replyCount ? ` (${comment.replyCount})` : ''}`) }}
          </button>
          <div v-if="childLoaded && childComments.length > 0" class="child-list">
            <CommentItem v-for="c in childComments" :key="c.id" :comment="c" :review-id="reviewId" @deleted="onChildDeleted" @reply-added="onChildReplyAdded" />
            <button v-if="childHasMore" class="load-more-btn" @click="loadMoreChildren">加载更多回复</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-item { margin-bottom: 4px; }
.comment-main { display: flex; gap: 10px; }
.comment-avatar { flex-shrink: 0; cursor: pointer; }
.comment-body { flex: 1; min-width: 0; }
.comment-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 4px; }
.comment-author { font-size: 13px; font-weight: 600; color: rgba(255,255,255,.85); cursor: pointer; }
.comment-author:hover { color: #f5af19; }
.reply-target { font-size: 12px; color: rgba(255,255,255,.4); }
.reply-target .link { color: rgba(255,255,255,.6); cursor: pointer; }
.reply-target .link:hover { color: #f5af19; }
.comment-time { font-size: 11px; color: rgba(255,255,255,.3); margin-left: auto; flex-shrink: 0; white-space: nowrap; }
.comment-content { font-size: 14px; color: rgba(255,255,255,.7); line-height: 1.6; margin: 4px 0; word-break: break-word; }
.comment-actions { display: flex; gap: 12px; margin-top: 4px; }
.comment-actions button { background: none; border: none; color: rgba(255,255,255,.4); font-size: 12px; cursor: pointer; padding: 2px 0; transition: color .2s; }
.comment-actions button:hover { color: rgba(255,255,255,.7); }
.comment-actions button.active { color: #f56c6c; }
.comment-actions button.danger:hover { color: #f56c6c; }
.reply-input-box { margin-top: 10px; }
.reply-to-label { font-size: 12px; color: rgba(255,255,255,.4); margin: 0 0 6px; }
.reply-to-label strong { color: rgba(255,255,255,.6); }
.reply-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
.child-section { margin-top: 8px; }
.toggle-children { background: none; border: none; color: rgba(255,255,255,.4); font-size: 12px; cursor: pointer; padding: 4px 0; }
.toggle-children:hover { color: #409eff; }
.toggle-children.loading { opacity: .5; }
.child-list { margin-top: 8px; padding-left: 12px; border-left: 1px solid rgba(255,255,255,.06); }
.load-more-btn { background: none; border: none; color: rgba(255,255,255,.35); font-size: 12px; cursor: pointer; padding: 4px 0; display: block; }
.load-more-btn:hover { color: #409eff; }
</style>
