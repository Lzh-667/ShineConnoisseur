<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Clock, CreditCard, Medal, Refresh, StarFilled, UserFilled, Wallet } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { useUserStore } from '../stores/modules/user'
import {
  cancelPaymentOrder,
  createPaymentOrder,
  getMyVip,
  getPaymentOrder,
  getVipProducts,
  getRecentPaymentOrders,
  getRefundQuota,
  requestPaymentRefund,
  startAlipay,
  startWechatPay,
} from '../api'

const products = ref([])
const userStore = useUserStore()
const vip = ref(null)
const loading = ref(false)
const paying = ref(false)
const selectedProductId = ref(null)
const paymentMethod = ref(1)
const order = ref(null)
const paymentDialogVisible = ref(false)
const wechatQrCode = ref('')
let pollTimer = null
let refundPollTimer = null
let orderGeneration = 0
let querySequence = 0
let activeQuery = null
let disposed = false
let purchaseIntent = null
const recentOrders = ref([])
const refundQuota = ref(null)
const refundSubmitting = ref(false)
const refundPending = computed(() => recentOrders.value.some(item => item.status === 7))

async function loadRecentOrders() {
  const [ordersRes, quotaRes] = await Promise.all([getRecentPaymentOrders(), getRefundQuota()])
  recentOrders.value = ordersRes.data || []
  refundQuota.value = quotaRes.data
}

async function applyRefund(item) {
  try {
    await ElMessageBox.confirm('将全额退款，并在退款成功后扣回此订单的会员时长。退款处理中暂不能购买或续费，是否继续？', '申请退款', {
      type: 'warning', confirmButtonText: '确认退款', cancelButtonText: '暂不退款',
    })
    refundSubmitting.value = true
    await requestPaymentRefund(item.orderNo)
    ElMessage.success('退款申请已受理，请稍后刷新查看结果')
    await loadPage()
    if (order.value?.orderNo === item.orderNo) await refreshOrder()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
  } finally {
    refundSubmitting.value = false
  }
}

const selectedProduct = computed(() =>
  products.value.find(item => item.id === selectedProductId.value) || null,
)

const orderStatus = {
  0: { label: '待发起', type: 'info' },
  1: { label: '支付成功', type: 'success' },
  2: { label: '已关闭', type: 'info' },
  3: { label: '已退款', type: 'warning' },
  4: { label: '支付信息生成中', type: 'warning' },
  5: { label: '等待支付', type: 'warning' },
  6: { label: '关单中', type: 'warning' },
  7: { label: '退款中', type: 'warning' },
  8: { label: '已支付待发权益', type: 'warning' },
}

function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function intentStorageKey() {
  return `vip-purchase:${userStore.userInfo?.id ?? 'anonymous'}`
}

function readIntent() {
  const owner = intentStorageKey()
  if (purchaseIntent?.owner === owner) return purchaseIntent
  purchaseIntent = null
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(owner))
    if (stored?.owner === owner && typeof stored.key === 'string' && stored.key.length <= 64
      && stored.key.length > 0 && [1, 2].includes(stored.method) && stored.productId) purchaseIntent = stored
  } catch { /* 存储不可用时仍保留当前页面内的重试幂等性。 */ }
  return purchaseIntent
}

function saveIntent(intent) {
  purchaseIntent = intent
  try { window.sessionStorage.setItem(intent.owner, JSON.stringify(intent)) } catch { /* 使用内存回退。 */ }
}

function finishIntent(orderNo) {
  const intent = readIntent()
  if (intent?.orderNo !== orderNo) return
  try { window.sessionStorage.removeItem(intent.owner) } catch { /* 不影响订单终态显示。 */ }
  purchaseIntent = null
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

async function loadPage() {
  loading.value = true
  try {
    const [productRes, vipRes] = await Promise.all([getVipProducts(), getMyVip(), loadRecentOrders()])
    products.value = productRes.data?.records || []
    vip.value = vipRes.data
    if (userStore.userInfo) userStore.userInfo.vip = vip.value?.vip === true
    if (!selectedProductId.value && products.value.length) {
      selectedProductId.value = products.value[0].id
    }
  } finally {
    loading.value = false
  }
}

function openAlipayPage(html, targetWindow) {
  const payWindow = targetWindow && !targetWindow.closed
    ? targetWindow
    : window.open('', '_blank')
  if (!payWindow) {
    ElMessage.warning('浏览器阻止了支付窗口，请允许本站打开新窗口后重试')
    return false
  }
  payWindow.opener = null
  payWindow.document.open()
  payWindow.document.write(html)
  payWindow.document.close()
  return true
}

async function beginPayment() {
  if (paying.value || disposed) return
  if (!userStore.userInfo?.id) {
    ElMessage.warning('用户信息尚未就绪，请登录后重试')
    return
  }
  const savedIntent = readIntent()
  if (!savedIntent && !selectedProduct.value) {
    ElMessage.warning('请先选择会员套餐')
    return
  }

  paying.value = true
  stopPolling()
  const generation = ++orderGeneration
  const intent = savedIntent || {
    owner: intentStorageKey(), productId: selectedProduct.value.id,
    method: paymentMethod.value, key: createIdempotencyKey(),
  }
  if (savedIntent) ElMessage.info('正在恢复上一次购买，沿用原套餐和支付方式；如需更换，请先确认或取消原订单')
  saveIntent(intent)
  const isCurrent = () => !disposed && generation === orderGeneration && intent.owner === intentStorageKey()
  wechatQrCode.value = ''
  const alipayWindow = intent.method === 1 ? window.open('', '_blank') : null
  try {
    const createRes = intent.orderNo ? await getPaymentOrder(intent.orderNo) : await createPaymentOrder(
      intent.productId,
      intent.method,
      intent.key,
    )
    if (!isCurrent()) { alipayWindow?.close(); return }
    order.value = createRes.data
    saveIntent({ ...intent, orderNo: order.value.orderNo })
    paymentDialogVisible.value = true
    if ([1, 2, 3, 6, 7, 8].includes(order.value.status)) {
      alipayWindow?.close()
      if (order.value.status !== 6) finishIntent(order.value.orderNo)
      startPolling()
      return
    }

    if (order.value.paymentMethod === 1) {
      const payRes = await startAlipay(order.value.orderNo)
      if (!isCurrent()) { alipayWindow?.close(); return }
      if (openAlipayPage(payRes.data, alipayWindow)) ElMessage.success('支付宝收银台已在新窗口打开')
    } else {
      const payRes = await startWechatPay(order.value.orderNo)
      if (!isCurrent()) return
      const qrCode = await QRCode.toDataURL(payRes.data, {
        width: 240,
        margin: 2,
        color: { dark: '#161625', light: '#ffffff' },
      })
      if (!isCurrent()) return
      wechatQrCode.value = qrCode
    }
    startPolling()
  } catch (error) {
    if (alipayWindow && !alipayWindow.closed) alipayWindow.close()
    // 下单结果不明时保留原幂等键；已知订单仍查询状态，不新建订单。
    if (isCurrent() && order.value?.orderNo && order.value.orderNo === purchaseIntent?.orderNo) startPolling()
  } finally {
    paying.value = false
  }
}

async function resumeOrder(item) {
  if (paying.value || disposed || !userStore.userInfo?.id) return
  saveIntent({ owner: intentStorageKey(), productId: item.productId, method: item.paymentMethod,
    key: item.requestId || createIdempotencyKey(), orderNo: item.orderNo })
  await beginPayment()
}

async function startNewPurchase() {
  if (paying.value) return
  const owner = intentStorageKey()
  try {
    await ElMessageBox.confirm('上一次请求可能已经创建订单。请先在最近订单中确认或取消；继续将开始新的购买，不会取消旧订单。是否继续？', '开始新购买', {
      type: 'warning', confirmButtonText: '确认开始新购买', cancelButtonText: '返回检查',
    })
    if (paying.value || disposed || owner !== intentStorageKey()) return
    try { window.sessionStorage.removeItem(intentStorageKey()) } catch { /* 清理内存中的购买意图。 */ }
    purchaseIntent = null
    await beginPayment()
  } catch { /* 用户取消时保留原购买意图。 */ }
}

async function refreshOrder(showMessage = false) {
  if (!order.value?.orderNo) return
  const orderNo = order.value.orderNo
  const generation = orderGeneration
  const owner = intentStorageKey()
  // 慢请求未返回时不叠加同一订单查询，避免所有响应都被更新的轮询淘汰。
  if (activeQuery?.orderNo === orderNo && activeQuery.generation === generation && activeQuery.owner === owner) return
  const sequence = ++querySequence
  const request = { orderNo, generation, owner }
  activeQuery = request
  const isCurrent = () => !disposed && generation === orderGeneration && sequence === querySequence
    && order.value?.orderNo === orderNo && owner === intentStorageKey()
  try {
    const res = await getPaymentOrder(orderNo)
    if (!isCurrent()) return
    order.value = res.data
    if ([1, 2, 3, 7, 8].includes(order.value.status)) finishIntent(orderNo)
    if (order.value.status === 1) {
      stopPolling()
      await loadRecentOrders()
      const vipRes = await getMyVip()
      if (!isCurrent()) return
      vip.value = vipRes.data
      if (userStore.userInfo) userStore.userInfo.vip = vip.value?.vip === true
      ElMessage.success('支付成功，VIP 权益已生效')
    } else if ([2, 3].includes(order.value.status)) {
      stopPolling()
      if (order.value.status === 3) await loadPage()
      if (showMessage) ElMessage.info(orderStatus[order.value.status].label)
    } else if (showMessage) {
      ElMessage.info('订单状态已刷新')
    }
  } catch {
    // 短暂网络失败不停止轮询；旧请求也不能停止新订单的轮询。
  } finally {
    if (activeQuery === request) activeQuery = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => refreshOrder(), 2500)
  refreshOrder()
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function cancelOrder() {
  if (!order.value?.orderNo) return
  const orderNo = order.value.orderNo
  const generation = orderGeneration
  try {
    await ElMessageBox.confirm('确定取消当前支付订单吗？', '取消订单', {
      type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '继续支付',
    })
    if (generation !== orderGeneration || order.value?.orderNo !== orderNo || disposed) return
    await cancelPaymentOrder(orderNo)
    if (generation !== orderGeneration || order.value?.orderNo !== orderNo || disposed) return
    await refreshOrder()
    ElMessage.success('取消请求已提交')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') return
  }
}

function handleDialogClosed() {
  orderGeneration++
  stopPolling()
  wechatQrCode.value = ''
}

onMounted(() => {
  loadPage().catch(() => { /* 请求层已提示错误，可手动刷新。 */ })
  refundPollTimer = window.setInterval(async () => {
    if (refundPending.value || recentOrders.value.some(item => item.status === 8)) {
      try { await loadPage() } catch { /* 请求层统一提示，下次继续查询 */ }
    }
  }, 10000)
})
onUnmounted(() => {
  disposed = true
  orderGeneration++
  stopPolling()
  window.clearInterval(refundPollTimer)
})
</script>

<template>
  <div class="vip-page">
    <section class="hero-card">
      <div>
        <span class="eyebrow">SHINE CONNOISSEUR</span>
        <h1>光影 VIP 会员</h1>
        <p>选择适合你的会员计划，完成支付后权益将自动发放并支持到期续订。</p>
      </div>
      <div class="vip-state" :class="{ active: vip?.vip }">
        <el-icon :size="28"><Check v-if="vip?.vip" /><Clock v-else /></el-icon>
        <div>
          <strong>{{ vip?.vip ? 'VIP 已生效' : '尚未开通 VIP' }}</strong>
          <span v-if="vip?.vip">有效期至 {{ formatDate(vip.expireTime) }}</span>
          <span v-else>开通后即可点亮会员身份</span>
        </div>
      </div>
    </section>

    <section class="identity-benefits">
      <router-link to="/vip/recommendations" class="benefit-card">
        <span class="benefit-icon"><el-icon><StarFilled /></el-icon></span>
        <div><strong>个性化电影推荐 →</strong><p>结合收藏与评分发现新电影，附专属推荐理由。</p></div>
      </router-link>
      <div class="benefit-card">
        <span class="benefit-icon"><el-icon><Medal /></el-icon></span>
        <div><strong>专属 VIP 徽章</strong><p>在影评、评论、关注与消息中展示会员身份。</p></div>
      </div>
      <div class="benefit-card">
        <span class="benefit-icon"><el-icon><StarFilled /></el-icon></span>
        <div><strong>金色会员昵称</strong><p>社区昵称使用专属金色高亮，更醒目地表达身份。</p></div>
      </div>
      <div class="benefit-card">
        <span class="benefit-icon"><el-icon><UserFilled /></el-icon></span>
        <div><strong>光影渐变头像环</strong><p>个人主页、导航栏及互动场景统一点亮头像。</p></div>
      </div>
    </section>

    <section class="plans-section" v-loading="loading">
      <div class="section-title">
        <div>
          <span>MEMBERSHIP</span>
          <h2>{{ vip?.vip ? '续订会员' : '选择套餐' }}</h2>
        </div>
        <p>重复购买会从当前有效期顺延，不会损失剩余天数。</p>
      </div>

      <div v-if="products.length" class="plan-grid">
        <button
          v-for="(product, index) in products"
          :key="product.id"
          class="plan-card"
          :class="{ selected: selectedProductId === product.id, recommended: index === 1 }"
          :disabled="paying"
          @click="selectedProductId = product.id"
        >
          <span v-if="index === 1" class="recommend-badge">推荐</span>
          <span class="plan-name">{{ product.name }}</span>
          <span class="plan-price"><small>¥</small>{{ product.price }}</span>
          <span class="plan-duration">{{ product.durationDays }} 天会员权益</span>
          <span class="select-indicator"><el-icon><Check /></el-icon></span>
        </button>
      </div>
      <el-empty v-else-if="!loading" description="暂无可购买的会员套餐" />
    </section>

    <section v-if="products.length" class="checkout-card">
      <div class="payment-methods">
        <span class="checkout-label">支付方式</span>
        <el-radio-group v-model="paymentMethod" :disabled="paying">
          <el-radio-button :value="1">
            <el-icon><CreditCard /></el-icon>支付宝
          </el-radio-button>
          <el-radio-button :value="2">
            <el-icon><Wallet /></el-icon>微信支付
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="checkout-action">
        <div>
          <span>应付金额</span>
          <strong>¥{{ selectedProduct?.price || '0.00' }}</strong>
        </div>
        <el-button class="pay-button" :loading="paying" :disabled="refundPending" @click="beginPayment">
          {{ vip?.vip ? '续订会员' : '立即开通' }}
        </el-button>
        <el-button link :disabled="paying || refundPending" @click="startNewPurchase">开始新购买</el-button>
      </div>
    </section>

    <section class="plans-section">
      <div class="section-title">
        <h2>最近订单</h2>
        <el-button :icon="Refresh" @click="loadPage">刷新订单与权益</el-button>
      </div>
      <p class="demo-note">仅最近一笔已支付订单可在支付成功后24小时内申请全额退款，使用过权益也可申请。退款成功后扣回对应时长，上一笔订单不会因此重新获得退款资格。</p>
      <el-alert v-if="refundPending" title="退款处理中，暂不能购买或续费；已受理申请不受24小时期限影响。" type="warning" :closable="false" />
      <el-alert v-if="refundQuota" :type="refundQuota.remaining > 0 ? 'info' : 'warning'" :closable="false"
        :title="`${refundQuota.year}年度剩余退款次数：${refundQuota.remaining} / ${refundQuota.limit}（北京时间自然年，处理中也占额度，系统自动退款不计入）`" />
      <el-table :data="recentOrders" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column prop="amount" label="金额（元）" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ orderStatus[row.status]?.label || '未知' }}</template>
        </el-table-column>
        <el-table-column label="退款申请截止" min-width="170">
          <template #default="{ row }">{{ formatDate(row.refundDeadline) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <el-button link :disabled="paying" @click="resumeOrder(row)">{{ [0, 4, 5].includes(row.status) ? '继续支付' : '查看订单' }}</el-button>
            <el-button v-if="row.refundEligible && refundQuota?.remaining > 0" type="danger" link :loading="refundSubmitting" @click="applyRefund(row)">全额退款</el-button>
            <span v-else>{{ row.status === 7 ? '等待渠道确认' : row.status === 3 ? '已退款' : '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <p class="demo-note">演示提示：实际拉起收银台需要在服务端配置对应渠道的沙箱或商户参数。</p>

    <el-dialog
      v-model="paymentDialogVisible"
      title="支付订单"
      width="min(460px, 92vw)"
      :close-on-click-modal="false"
      @close="handleDialogClosed"
    >
      <div v-if="order" class="order-dialog">
        <div class="order-status-row">
          <span>订单状态</span>
          <el-tag :type="orderStatus[order.status]?.type || 'info'">
            {{ orderStatus[order.status]?.label || '未知状态' }}
          </el-tag>
        </div>
        <div v-if="order.paymentMethod === 2 && wechatQrCode && [0, 4, 5].includes(order.status)" class="qr-box">
          <img :src="wechatQrCode" alt="微信支付二维码" />
          <strong>请使用微信扫码支付</strong>
          <span>页面将自动查询支付结果</span>
        </div>
        <div v-else-if="order.paymentMethod === 1 && [0, 4, 5].includes(order.status)" class="alipay-tip">
          <el-icon :size="36"><CreditCard /></el-icon>
          <strong>请在新窗口完成支付宝支付</strong>
          <span>支付完成后返回本页，订单状态会自动更新。</span>
        </div>
        <dl class="order-details">
          <div><dt>订单号</dt><dd>{{ order.orderNo }}</dd></div>
          <div><dt>支付金额</dt><dd>¥{{ order.amount }}</dd></div>
          <div><dt>失效时间</dt><dd>{{ formatDate(order.expireTime) }}</dd></div>
        </dl>
        <div class="dialog-actions">
          <el-button :icon="Refresh" @click="refreshOrder(true)">刷新状态</el-button>
          <el-button
            v-if="![1, 2, 3, 7, 8].includes(order.status)"
            type="danger"
            plain
            @click="cancelOrder"
          >取消订单</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.vip-page { max-width: 1080px; margin: 0 auto; padding: 48px 28px 72px; }
.hero-card { min-height: 220px; padding: 42px; border-radius: 24px; display: flex; align-items: center; justify-content: space-between; gap: 32px; overflow: hidden; position: relative; background: radial-gradient(circle at 85% 20%, rgba(245,175,25,.18), transparent 34%), linear-gradient(135deg, #202036, #121220); border: 1px solid rgba(245,175,25,.18); box-shadow: 0 24px 70px rgba(0,0,0,.25); }
.hero-card::after { content: ''; position: absolute; width: 260px; height: 260px; border: 1px solid rgba(245,175,25,.12); border-radius: 50%; right: -110px; bottom: -170px; }
.eyebrow, .section-title span { color: rgba(245,175,25,.6); letter-spacing: 3px; font-size: 11px; font-weight: 700; }
.hero-card h1 { margin: 10px 0 8px; font-size: clamp(30px, 5vw, 46px); line-height: 1.15; color: #fff; }
.hero-card p { color: rgba(255,255,255,.46); max-width: 560px; }
.vip-state { min-width: 250px; padding: 20px; border-radius: 16px; display: flex; align-items: center; gap: 14px; background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.07); color: rgba(255,255,255,.35); }
.vip-state.active { color: #f5af19; background: rgba(245,175,25,.08); border-color: rgba(245,175,25,.24); }
.vip-state div { display: flex; flex-direction: column; }
.vip-state strong { color: rgba(255,255,255,.86); }
.vip-state span { font-size: 12px; margin-top: 3px; color: rgba(255,255,255,.38); }
.identity-benefits { display:grid; grid-template-columns:repeat(3,1fr); gap:14px; margin-bottom:32px; }
.benefit-card { display:flex; align-items:flex-start; gap:12px; padding:18px; border:1px solid rgba(245,175,25,.14); border-radius:14px; background:linear-gradient(145deg,rgba(245,175,25,.07),rgba(255,255,255,.025)); }
.benefit-icon { display:inline-flex; align-items:center; justify-content:center; width:36px; height:36px; flex:0 0 auto; border-radius:11px; background:rgba(245,175,25,.12); color:#ffd36a; font-size:19px; }
.benefit-card strong { color:rgba(255,255,255,.86); font-size:14px; }
.benefit-card p { margin:5px 0 0; color:rgba(255,255,255,.4); font-size:12px; line-height:1.6; }
.plans-section { min-height: 260px; margin-top: 46px; }
.section-title { display: flex; align-items: end; justify-content: space-between; margin-bottom: 20px; }
.section-title h2 { color: rgba(255,255,255,.9); font-size: 25px; margin-top: 3px; }
.section-title p { color: rgba(255,255,255,.35); font-size: 13px; }
.plan-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.plan-card { border: 1px solid rgba(255,255,255,.07); color: inherit; background: rgba(255,255,255,.025); padding: 28px 24px 24px; border-radius: 18px; text-align: left; cursor: pointer; position: relative; transition: transform .2s, border-color .2s, background .2s; }
.plan-card:hover { transform: translateY(-3px); border-color: rgba(245,175,25,.28); }
.plan-card.selected { border-color: #f5af19; background: linear-gradient(145deg, rgba(245,175,25,.12), rgba(255,255,255,.025)); box-shadow: 0 12px 40px rgba(245,175,25,.08); }
.recommend-badge { position: absolute; top: 0; right: 20px; padding: 4px 11px; border-radius: 0 0 8px 8px; background: #f5af19; color: #171724; font-size: 11px; font-weight: 700; }
.plan-name, .plan-duration { display: block; color: rgba(255,255,255,.5); font-size: 14px; }
.plan-price { display: block; margin: 14px 0 10px; font-size: 34px; line-height: 1; color: #fff; font-weight: 750; }
.plan-price small { color: #f5af19; font-size: 16px; margin-right: 4px; }
.plan-duration { font-size: 12px; color: rgba(255,255,255,.3); }
.select-indicator { position: absolute; width: 24px; height: 24px; right: 18px; bottom: 18px; border-radius: 50%; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.12); color: transparent; }
.selected .select-indicator { color: #181824; background: #f5af19; border-color: #f5af19; }
.checkout-card { margin-top: 24px; padding: 24px 28px; background: rgba(255,255,255,.025); border: 1px solid rgba(255,255,255,.07); border-radius: 18px; display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.payment-methods, .checkout-action, .checkout-action > div { display: flex; align-items: center; gap: 16px; }
.checkout-label, .checkout-action span { font-size: 13px; color: rgba(255,255,255,.4); }
.payment-methods :deep(.el-radio-button__inner) { display: flex; align-items: center; gap: 6px; background: rgba(255,255,255,.035); border-color: rgba(255,255,255,.08); color: rgba(255,255,255,.55); box-shadow: none; }
.payment-methods :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { color: #181824; background: #f5af19; border-color: #f5af19; box-shadow: -1px 0 0 0 #f5af19; }
.checkout-action strong { font-size: 25px; color: #fff; }
.pay-button { height: 42px; padding: 0 26px; border: none; color: #181824; font-weight: 700; background: linear-gradient(135deg, #f7c64d, #f5af19); }
.pay-button:hover { color: #181824; background: linear-gradient(135deg, #ffd56c, #f7bd36); }
.demo-note { margin-top: 14px; text-align: center; color: rgba(255,255,255,.25); font-size: 12px; }
.order-dialog { display: flex; flex-direction: column; gap: 18px; }
.order-status-row { display: flex; justify-content: space-between; align-items: center; color: rgba(255,255,255,.48); }
.qr-box, .alipay-tip { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px; background: rgba(255,255,255,.025); border-radius: 14px; color: #f5af19; }
.qr-box img { width: 220px; border-radius: 8px; }
.qr-box strong, .alipay-tip strong { color: rgba(255,255,255,.82); }
.qr-box span, .alipay-tip span { color: rgba(255,255,255,.34); font-size: 12px; text-align: center; }
.order-details { display: flex; flex-direction: column; gap: 9px; }
.order-details div { display: flex; justify-content: space-between; gap: 24px; font-size: 13px; }
.order-details dt { color: rgba(255,255,255,.34); }
.order-details dd { color: rgba(255,255,255,.67); text-align: right; word-break: break-all; }
.dialog-actions { display: flex; justify-content: flex-end; }
@media (max-width: 760px) {
  .vip-page { padding: 24px 16px 56px; }
  .identity-benefits { grid-template-columns:1fr; }
  .hero-card { padding: 28px 24px; flex-direction: column; align-items: stretch; }
  .vip-state { min-width: 0; }
  .section-title { align-items: start; flex-direction: column; gap: 8px; }
  .plan-grid { grid-template-columns: 1fr; }
  .checkout-card, .payment-methods { align-items: stretch; flex-direction: column; }
  .checkout-action { justify-content: space-between; }
}
</style>
