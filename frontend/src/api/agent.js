import request from '../utils/request'
import { getToken } from '../utils/auth'

const BASE = import.meta.env.VITE_API_BASE_URL || '/api'

/**
 * SSE 流式对话。
 * onEvent(event, data) 回调：message / tool / source / done / error。
 * 限流、参数错误时 agent 返回普通 JSON（非 event-stream），此处统一抛 Error(errorMsg)。
 */
export async function chatStream({ threadId, message, extra, onEvent, signal }) {
  const token = getToken()
  let resp
  try {
    resp = await fetch(`${BASE}/agent/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { authorization: token } : {}),
      },
      body: JSON.stringify({ threadId: threadId || null, message, extra: extra || null }),
      signal,
    })
  } catch (e) {
    if (e.name === 'AbortError') throw e
    throw new Error('网络连接异常，请稍后重试')
  }

  const contentType = resp.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    let msg = `AI 服务异常（HTTP ${resp.status}）`
    try {
      const body = await resp.json()
      msg = body.errorMsg || msg
    } catch { /* 非 JSON 响应 */ }
    throw new Error(msg)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  // 标准 SSE 逐行解析：兼容 \n 与 \r\n 行分隔（sse-starlette 用 \r\n）
  let event = 'message'
  const dataLines = []

  const flush = () => {
    if (dataLines.length === 0) return
    const data = dataLines.join('\n')
    dataLines.length = 0
    try {
      onEvent(event, JSON.parse(data))
    } catch {
      onEvent(event, data)
    }
    event = 'message'
  }

  let done = false
  while (!done) {
    const { done: isDone, value } = await reader.read()
    done = isDone
    if (value) buffer += decoder.decode(value, { stream: !done })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const rawLine of lines) {
      const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
      if (!line) { flush(); continue }
      if (line.startsWith(':')) continue // 心跳注释
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
  }
  flush()
}

/** 会话列表（需登录） */
export function listSessions(current = 1) {
  return request({ url: '/agent/sessions', method: 'get', params: { current } })
}

/** 删除会话（需登录） */
export function deleteSession(threadId) {
  return request({ url: `/agent/sessions/${threadId}`, method: 'delete' })
}
