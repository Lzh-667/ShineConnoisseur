<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { chatStream, listSessions, deleteSession } from '../api/agent'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Plus, Delete, Promotion } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

defineOptions({ name: 'Chat' })

const router = useRouter()
const userStore = useUserStore()

// ============ 状态 ============
const messages = ref([]) // { role, content, tools[], sources[], error }
const currentThreadId = ref(null)
const input = ref('')
const streaming = ref(false)
const abortCtrl = ref(null)

const sessions = ref([])
const sessionsLoading = ref(false)
const sessionsCurrent = ref(1)
const sessionsHasMore = ref(false)
const sidebarOpen = ref(true)
const messageListEl = ref(null)

const HISTORY_PREFIX = 'agent:history:'
const MAX_CACHE_MESSAGES = 60

const suggestions = [
  { icon: '🔥', text: '最近有什么热门电影？' },
  { icon: '🎬', text: '推荐几部高分科幻电影' },
  { icon: '💬', text: '《星际穿越》的影评口碑怎么样？' },
  { icon: '❤️', text: '周末和女朋友约会，推荐适合一起看的电影' },
  { icon: '📋', text: '给我排一个三天的悬疑片观影计划' },
  { icon: '🆚', text: '对比一下《盗梦空间》和《星际穿越》' },
]

// ============ markdown 渲染 ============
marked.setOptions({ breaks: true, gfm: true })
function renderMarkdown(text) {
  if (!text) return ''
  // 兼容旧提示词/历史缓存：清理电影名后的 (id:数字)，不展示原始 id
  const cleaned = text.replace(/[(（]\s*id[:：]\s*\d+\s*[)）]/g, '')
  return DOMPurify.sanitize(marked.parse(cleaned))
}

// ============ 本地历史缓存 ============
function saveHistory(threadId) {
  const id = threadId || currentThreadId.value
  if (!id) return
  try {
    const key = HISTORY_PREFIX + id
    const list = messages.value.slice(-MAX_CACHE_MESSAGES).map(m => ({
      role: m.role, content: m.content, tools: m.tools, sources: m.sources, error: m.error,
    }))
    localStorage.setItem(key, JSON.stringify(list))
  } catch { /* 配额满等异常静默 */ }
}

function loadHistory(threadId) {
  try {
    const raw = localStorage.getItem(HISTORY_PREFIX + threadId)
    if (raw) {
      messages.value = JSON.parse(raw)
      return
    }
  } catch { /* 损坏数据忽略 */ }
  messages.value = []
}

function clearHistory(threadId) {
  try { localStorage.removeItem(HISTORY_PREFIX + threadId) } catch { /* */ }
}

// ============ 会话列表 ============
async function loadSessions(page = 1) {
  if (!userStore.isLoggedIn) return
  sessionsLoading.value = true
  try {
    const res = await listSessions(page)
    const { list, hasMore } = res.data
    if (page === 1) sessions.value = list ?? []
    else sessions.value.push(...(list ?? []))
    sessionsHasMore.value = hasMore ?? false
    sessionsCurrent.value = page
  } catch { /* 拦截器已提示 */ } finally {
    sessionsLoading.value = false
  }
}

function loadMoreSessions() {
  if (sessionsHasMore.value) loadSessions(sessionsCurrent.value + 1)
}

function newChat() {
  stopStreaming()
  currentThreadId.value = null
  messages.value = []
  nextTick(scrollToBottom)
}

function switchSession(s) {
  if (currentThreadId.value === s.threadId) return
  stopStreaming()
  currentThreadId.value = s.threadId
  loadHistory(s.threadId)
  nextTick(scrollToBottom)
}

async function removeSession(s) {
  try {
    await deleteSession(s.threadId)
    sessions.value = sessions.value.filter(x => x.threadId !== s.threadId)
    clearHistory(s.threadId)
    if (currentThreadId.value === s.threadId) newChat()
    ElMessage.success('会话已删除')
  } catch { /* 拦截器已提示 */ }
}

function formatSessionTime(ts) {
  if (!ts) return ''
  const d = new Date(ts * 1000)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  return sameDay
    ? d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

// ============ 滚动 ============
function nearBottom() {
  const el = messageListEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ============ 对话 ============
function stopStreaming() {
  if (abortCtrl.value) {
    abortCtrl.value.abort()
    abortCtrl.value = null
  }
}

async function send(messageText) {
  const text = (messageText ?? input.value).trim()
  if (!text || streaming.value) return
  input.value = ''

  const threadId = currentThreadId.value
  messages.value.push({ role: 'user', content: text })
  const aiMsg = { role: 'assistant', content: '', tools: [], sources: [], error: '' }
  messages.value.push(aiMsg)
  streaming.value = true
  const ctrl = new AbortController()
  abortCtrl.value = ctrl
  scrollToBottom()

  try {
    await chatStream({
      threadId,
      message: text,
      onEvent(event, data) {
        if (event === 'message') {
          aiMsg.content += data.delta || ''
          if (nearBottom()) scrollToBottom()
        } else if (event === 'tool') {
          aiMsg.tools.push({ name: data.name || '工具', summary: data.summary || '' })
        } else if (event === 'source') {
          aiMsg.sources.push(data)
        } else if (event === 'done') {
          if (data.threadId) currentThreadId.value = data.threadId
        } else if (event === 'error') {
          aiMsg.error = data.message || 'AI 服务异常'
        }
      },
      signal: ctrl.signal,
    })
  } catch (e) {
    if (e.name !== 'AbortError') aiMsg.error = e.message || '请求失败，请稍后重试'
  } finally {
    streaming.value = false
    abortCtrl.value = null
    if (!aiMsg.content && !aiMsg.error) aiMsg.error = 'AI 未返回内容，请重试'
    // 流式中途切换/新建会话时不落库，避免串写其他会话的缓存
    if (currentThreadId.value === threadId) saveHistory()
    // 新会话完成后刷新侧边栏
    if (userStore.isLoggedIn && !threadId && currentThreadId.value) loadSessions(1)
    scrollToBottom()
  }
}

function handleKeydown(e) {
  if (e.isComposing) return // 中文输入法选词回车不发送
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function openSource(s) {
  if (s.type === 'movie') router.push(`/movies/${s.id}`)
  else if (s.type === 'review') router.push(`/reviews/${s.id}`)
}

// ============ 工具 chip 折叠 ============
const expandedTools = ref(new Set())
function toggleTools(index) {
  const next = new Set(expandedTools.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  expandedTools.value = next
}

const isLoggedIn = computed(() => userStore.isLoggedIn)
const canSend = computed(() => input.value.trim() && !streaming.value)

onMounted(() => {
  sidebarOpen.value = window.innerWidth > 768
  loadSessions(1)
  userStore.init()
})
</script>

<template>
  <div class="chat-page">
    <!-- 会话侧边栏 -->
    <aside v-if="sidebarOpen" class="chat-sidebar">
      <button class="sidebar-close-btn" @click="sidebarOpen = false">✕</button>
      <button class="new-chat-btn" @click="newChat">
        <el-icon><Plus /></el-icon>
        <span>新对话</span>
      </button>

      <div v-if="!isLoggedIn" class="sidebar-guest">
        <p>登录后可在左侧查看会话历史</p>
        <el-button size="small" round @click="router.push('/login')">去登录</el-button>
      </div>

      <template v-else>
        <div v-if="sessions.length === 0 && !sessionsLoading" class="sidebar-empty">
          暂无历史会话
        </div>
        <div class="session-list">
          <div
            v-for="s in sessions"
            :key="s.threadId"
            class="session-item"
            :class="{ active: s.threadId === currentThreadId }"
            @click="switchSession(s)"
          >
            <el-icon class="session-icon"><ChatDotRound /></el-icon>
            <div class="session-body">
              <span class="session-title">{{ s.title || '新对话' }}</span>
              <span class="session-time">{{ formatSessionTime(s.updatedAt) }}</span>
            </div>
            <button class="session-del" title="删除会话" @click.stop="removeSession(s)">
              <el-icon :size="14"><Delete /></el-icon>
            </button>
          </div>
          <button v-if="sessionsHasMore" class="session-more" @click="loadMoreSessions">
            {{ sessionsLoading ? '加载中...' : '加载更多' }}
          </button>
        </div>
      </template>
    </aside>

    <!-- 主区 -->
    <section class="chat-main">
      <button v-if="!sidebarOpen" class="sidebar-open-btn" @click="sidebarOpen = true">
        <el-icon><ChatDotRound /></el-icon> 会话
      </button>

      <!-- 消息区 -->
      <div ref="messageListEl" class="message-list">
        <div v-if="messages.length === 0" class="chat-welcome">
          <div class="welcome-logo">
            <span class="logo-dot"></span>
          </div>
          <h2 class="welcome-title">光影鉴赏家 AI 助手</h2>
          <p class="welcome-sub">发现好电影 · 看懂好影评 · 排好观影计划</p>
          <div class="suggestion-grid">
            <button
              v-for="(s, i) in suggestions"
              :key="i"
              class="suggestion-card"
              @click="send(s.text)"
            >
              <span class="suggestion-icon">{{ s.icon }}</span>
              <span class="suggestion-text">{{ s.text }}</span>
            </button>
          </div>
        </div>

        <template v-else>
          <div class="history-notice" v-if="currentThreadId">
            继续会话：{{ sessions.find(s => s.threadId === currentThreadId)?.title || '历史对话' }}
          </div>

          <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
            <div v-if="m.role === 'assistant'" class="msg-avatar ai-avatar">影</div>
            <div class="msg-bubble">
              <template v-if="m.role === 'user'">
                <div class="msg-text">{{ m.content }}</div>
              </template>
              <template v-else>
                <div v-if="m.content" class="md-body" v-html="renderMarkdown(m.content)"></div>
                <div v-if="streaming && i === messages.length - 1 && !m.content" class="typing">
                  <span></span><span></span><span></span>
                </div>

                <!-- 工具调用 -->
                <div v-if="m.tools && m.tools.length" class="tool-block">
                  <button class="tool-toggle" @click="toggleTools(i)">
                    <span class="tool-toggle-dot"></span>
                    工具调用 {{ m.tools.length }} 次
                    <span class="tool-toggle-arrow">{{ expandedTools.has(i) ? '▾' : '▸' }}</span>
                  </button>
                  <div v-if="expandedTools.has(i)" class="tool-list">
                    <div v-for="(t, ti) in m.tools" :key="ti" class="tool-item">
                      <span class="tool-name">{{ t.name }}</span>
                      <span v-if="t.summary" class="tool-summary">{{ t.summary }}</span>
                    </div>
                  </div>
                </div>

                <!-- 引用来源 -->
                <div v-if="m.sources && m.sources.length" class="source-block">
                  <button
                    v-for="(s, si) in m.sources"
                    :key="si"
                    class="source-chip"
                    @click="openSource(s)"
                  >
                    {{ s.type === 'movie' ? '🎬' : '💬' }} {{ s.title }}
                    <el-icon class="source-go"><Promotion /></el-icon>
                  </button>
                </div>

                <div v-if="m.error" class="msg-error">{{ m.error }}</div>
              </template>
            </div>
            <div v-if="m.role === 'user'" class="msg-avatar user-avatar">
              <el-avatar :size="34" :src="userStore.userInfo?.avatar">
                {{ (userStore.userInfo?.nickname || '我')[0] }}
              </el-avatar>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区 -->
      <div class="chat-input-area">
        <div class="chat-input-box">
          <textarea
            v-model="input"
            class="chat-textarea"
            rows="1"
            :placeholder="streaming ? 'AI 正在回复...' : '问问光影鉴赏家，如：推荐一部适合周末看的电影'"
            @keydown="handleKeydown"
          ></textarea>
          <button
            v-if="streaming"
            class="stop-btn"
            title="停止生成"
            @click="stopStreaming"
          >■</button>
          <button
            v-else
            class="send-btn"
            :disabled="!canSend"
            title="发送"
            @click="send()"
          >
            <el-icon :size="18"><Promotion /></el-icon>
          </button>
        </div>
        <p class="chat-footnote">AI 生成内容仅供参考，站内数据以页面展示为准</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 64px);
  max-width: 1280px;
  margin: 0 auto;
  background: var(--bg-primary);
}

/* ============ 侧边栏 ============ */
.chat-sidebar {
  width: 250px;
  flex-shrink: 0;
  border-right: 1px solid var(--border-light);
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.new-chat-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  background: linear-gradient(135deg, rgba(245,175,25,.9), rgba(241,39,17,.85));
  border: none; color: #fff; font-size: 14px; font-weight: 600;
  padding: 10px; border-radius: var(--radius-md); cursor: pointer;
  transition: all .2s;
}
.new-chat-btn:hover { opacity: .9; transform: translateY(-1px); }

.sidebar-guest {
  text-align: center; padding: 24px 8px;
  color: var(--text-tertiary); font-size: 13px;
}
.sidebar-guest p { margin-bottom: 12px; }

.sidebar-empty { text-align: center; color: var(--text-muted); font-size: 13px; padding: 24px 0; }

.session-list { display: flex; flex-direction: column; gap: 4px; }

.session-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px; border-radius: var(--radius-md); cursor: pointer;
  transition: background .2s; position: relative;
}
.session-item:hover { background: rgba(255,255,255,.05); }
.session-item.active { background: rgba(245,175,25,.1); }
.session-icon { color: var(--text-tertiary); flex-shrink: 0; }
.session-item.active .session-icon { color: var(--color-gold); }

.session-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.session-title {
  font-size: 13px; color: var(--text-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.session-item.active .session-title { color: var(--color-gold); }
.session-time { font-size: 11px; color: var(--text-muted); }

.session-del {
  background: none; border: none; color: var(--text-muted); cursor: pointer;
  padding: 4px; border-radius: 6px; opacity: 0; transition: all .2s; flex-shrink: 0;
}
.session-item:hover .session-del { opacity: 1; }
.session-del:hover { color: var(--color-danger); background: rgba(245,108,108,.1); }

.session-more {
  background: none; border: none; color: var(--text-tertiary); cursor: pointer;
  font-size: 12px; padding: 8px; border-radius: 8px; transition: all .2s;
}
.session-more:hover { color: var(--color-gold); background: rgba(255,255,255,.04); }

/* ============ 主区 ============ */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; position: relative; }

.sidebar-open-btn {
  position: absolute; top: 12px; left: 12px; z-index: 10;
  display: none; align-items: center; gap: 6px;
  background: rgba(255,255,255,.06); border: 1px solid var(--border-visible);
  color: var(--text-secondary); font-size: 13px; padding: 6px 12px;
  border-radius: var(--radius-pill); cursor: pointer;
}

.sidebar-close-btn {
  display: none; position: absolute; top: 10px; right: 10px;
  background: none; border: none; color: var(--text-tertiary);
  font-size: 14px; cursor: pointer; padding: 4px;
}

.message-list {
  flex: 1; overflow-y: auto; padding: 24px 16px 12px;
  scroll-behavior: smooth;
}

/* ============ 欢迎态 ============ */
.chat-welcome { display: flex; flex-direction: column; align-items: center; padding: 48px 24px; }

.welcome-logo {
  width: 64px; height: 64px; border-radius: 20px; margin-bottom: 20px;
  background: rgba(245,175,25,.1); border: 1px solid rgba(245,175,25,.3);
  display: flex; align-items: center; justify-content: center;
}
.logo-dot {
  width: 14px; height: 14px; border-radius: 50%;
  background: var(--gradient-gold); box-shadow: 0 0 24px rgba(245,175,25,.6);
}
.welcome-title { font-size: 24px; color: var(--color-gold); margin: 0 0 8px; }
.welcome-sub { font-size: 14px; color: var(--text-tertiary); margin: 0 0 32px; }

.suggestion-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px;
  width: 100%; max-width: 640px;
}
.suggestion-card {
  display: flex; align-items: center; gap: 10px; text-align: left;
  background: rgba(255,255,255,.03); border: 1px solid var(--border-light);
  color: var(--text-secondary); font-size: 13px;
  padding: 14px 16px; border-radius: var(--radius-lg); cursor: pointer;
  transition: all .2s;
}
.suggestion-card:hover {
  border-color: rgba(245,175,25,.4); color: var(--color-gold);
  background: rgba(245,175,25,.06); transform: translateY(-2px);
}
.suggestion-icon { font-size: 18px; }
.suggestion-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ============ 消息 ============ */
.history-notice {
  text-align: center; color: var(--text-muted); font-size: 12px;
  margin-bottom: 16px; position: relative;
}
.history-notice::before, .history-notice::after {
  content: ''; display: inline-block; width: 60px; height: 1px;
  background: var(--border-visible); vertical-align: middle; margin: 0 12px;
}

.msg-row { display: flex; gap: 10px; margin-bottom: 20px; }
.msg-row.user { flex-direction: row-reverse; }

.msg-avatar {
  width: 34px; height: 34px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700;
}
.ai-avatar {
  background: var(--gradient-gold); color: #fff;
  box-shadow: 0 2px 10px rgba(245,175,25,.3);
}
.user-avatar :deep(.el-avatar) { background: rgba(255,255,255,.1); color: var(--text-secondary); }

.msg-bubble {
  max-width: 72%; padding: 12px 16px; border-radius: var(--radius-lg);
  font-size: 14px; line-height: 1.7; min-width: 0;
}
.msg-row.user .msg-bubble {
  background: rgba(245,175,25,.12); border: 1px solid rgba(245,175,25,.2);
}
.msg-row.assistant .msg-bubble {
  background: rgba(255,255,255,.03); border: 1px solid var(--border-light);
}
.msg-text { color: var(--text-primary); white-space: pre-wrap; word-break: break-word; }

/* typing 动画 */
.typing { display: inline-flex; gap: 4px; padding: 4px 0; }
.typing span {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--color-gold); animation: blink 1.2s infinite;
}
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%, 80%, 100% { opacity: .2; } 40% { opacity: 1; } }

/* markdown 正文 */
.md-body { color: var(--text-primary); word-break: break-word; }
.md-body :deep(p) { margin: 0 0 10px; }
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3), .md-body :deep(h4) {
  color: var(--color-gold); margin: 16px 0 8px; line-height: 1.4;
}
.md-body :deep(h1) { font-size: 18px; }
.md-body :deep(h2) { font-size: 16px; }
.md-body :deep(h3), .md-body :deep(h4) { font-size: 15px; }
.md-body :deep(ul), .md-body :deep(ol) { margin: 0 0 10px; padding-left: 22px; }
.md-body :deep(li) { margin: 4px 0; }
.md-body :deep(strong) { color: var(--color-gold); }
.md-body :deep(code) {
  background: rgba(255,255,255,.08); border-radius: 4px;
  padding: 1px 6px; font-size: 13px; color: #ffd7a0;
}
.md-body :deep(pre) {
  background: rgba(0,0,0,.3); border: 1px solid var(--border-visible);
  border-radius: var(--radius-md); padding: 12px; overflow-x: auto; margin: 0 0 10px;
}
.md-body :deep(pre code) { background: none; padding: 0; }
.md-body :deep(blockquote) {
  border-left: 3px solid var(--color-gold); margin: 0 0 10px;
  padding: 4px 12px; color: var(--text-tertiary); background: rgba(245,175,25,.05);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.md-body :deep(table) {
  border-collapse: collapse; margin: 0 0 12px; width: 100%;
  font-size: 13px;
}
.md-body :deep(th) {
  background: rgba(245,175,25,.12); color: var(--color-gold);
  border: 1px solid var(--border-visible); padding: 6px 10px; text-align: left;
}
.md-body :deep(td) {
  border: 1px solid var(--border-visible); padding: 6px 10px;
  color: var(--text-secondary);
}
.md-body :deep(hr) { border: none; border-top: 1px solid var(--border-visible); margin: 12px 0; }

/* 工具调用 */
.tool-block { margin-top: 10px; }
.tool-toggle {
  display: flex; align-items: center; gap: 6px;
  background: none; border: none; color: var(--text-tertiary);
  font-size: 12px; cursor: pointer; padding: 4px 0;
}
.tool-toggle:hover { color: var(--text-secondary); }
.tool-toggle-dot {
  width: 6px; height: 6px; border-radius: 50%; background: var(--color-success);
}
.tool-toggle-arrow { font-size: 10px; }
.tool-list { margin-top: 6px; display: flex; flex-direction: column; gap: 4px; }
.tool-item {
  display: flex; flex-direction: column; gap: 2px;
  background: rgba(255,255,255,.03); border: 1px solid var(--border-light);
  border-radius: var(--radius-sm); padding: 6px 10px;
}
.tool-name { font-size: 12px; color: var(--color-gold); font-family: monospace; }
.tool-summary {
  font-size: 12px; color: var(--text-tertiary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 引用来源 */
.source-block { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 6px; }
.source-chip {
  display: inline-flex; align-items: center; gap: 6px;
  background: rgba(245,175,25,.08); border: 1px solid rgba(245,175,25,.25);
  color: var(--color-gold); font-size: 12px;
  padding: 4px 10px; border-radius: var(--radius-pill); cursor: pointer;
  transition: all .2s; max-width: 100%;
}
.source-chip:hover { background: rgba(245,175,25,.18); }
.source-chip .source-go { font-size: 12px; }

/* 错误 */
.msg-error {
  margin-top: 8px; color: var(--color-danger); font-size: 13px;
  background: rgba(245,108,108,.08); border-radius: var(--radius-sm);
  padding: 6px 10px;
}

/* ============ 输入区 ============ */
.chat-input-area { padding: 8px 16px 10px; }

.chat-input-box {
  display: flex; align-items: flex-end; gap: 10px;
  background: rgba(255,255,255,.04); border: 1px solid var(--border-visible);
  border-radius: var(--radius-lg); padding: 10px 12px;
  transition: border-color .2s;
}
.chat-input-box:focus-within { border-color: rgba(245,175,25,.5); }

.chat-textarea {
  flex: 1; background: none; border: none; outline: none; resize: none;
  color: var(--text-primary); font-size: 14px; line-height: 1.6;
  max-height: 120px; font-family: inherit; padding: 4px 0;
}
.chat-textarea::placeholder { color: var(--text-muted); }

.send-btn, .stop-btn {
  width: 38px; height: 38px; flex-shrink: 0;
  border-radius: 10px; border: none; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all .2s;
}
.send-btn {
  background: var(--gradient-gold); color: #fff;
}
.send-btn:hover:not(:disabled) { opacity: .9; transform: translateY(-1px); }
.send-btn:disabled { background: rgba(255,255,255,.08); color: var(--text-disabled); cursor: not-allowed; }
.stop-btn {
  background: rgba(245,108,108,.15); color: var(--color-danger);
  font-size: 14px;
}
.stop-btn:hover { background: rgba(245,108,108,.25); }

.chat-footnote {
  text-align: center; font-size: 11px; color: var(--text-muted);
  margin: 6px 0 2px;
}

/* ============ 窄屏 ============ */
@media (max-width: 768px) {
  .chat-sidebar {
    position: fixed; top: 64px; left: 0; bottom: 0; z-index: 60;
    background: var(--bg-deepest); box-shadow: var(--shadow-lg);
    width: 260px;
  }
  .sidebar-close-btn { display: block; }
  .sidebar-open-btn { display: flex; }
  .msg-bubble { max-width: 85%; }
  .suggestion-grid { grid-template-columns: 1fr; }
}
</style>
