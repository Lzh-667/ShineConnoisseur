<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Clock, CreditCard, Refresh, Wallet } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import {
  cancelPaymentOrder,
  createPaymentOrder,
  getMyVip,
  getPaymentOrder,
  getVipProducts,
  startAlipay,
  startWechatPay,
} from '../api'

const products = ref([])
const vip = ref(null)
const loading = ref(false)
const paying = ref(false)
const selectedProductId = ref(null)
const paymentMethod = ref(1)
const order = ref(null)
const paymentDialogVisible = ref(false)
const wechatQrCode = ref('')
let pollTimer = null

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
}

function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
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
    const [productRes, vipRes] = await Promise.all([getVipProducts(), getMyVip()])
    products.value = productRes.data?.records || []
    vip.value = vipRes.data
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
    : window.open('', '_blank', 'noopener,noreferrer')
  if (!payWindow) {
    ElMessage.warning('浏览器阻止了支付窗口，请允许本站打开新窗口后重试')
    return
  }
  payWindow.document.open()
  payWindow.document.write(html)
  payWindow.document.close()
}

async function beginPayment() {
  if (!selectedProduct.value) {
    ElMessage.warning('请先选择会员套餐')
    return
  }

  paying.value = true
  wechatQrCode.value = ''
  const alipayWindow = paymentMethod.value === 1 ? window.open('', '_blank') : null
  try {
    const createRes = await createPaymentOrder(
      selectedProduct.value.id,
      paymentMethod.value,
      createIdempotencyKey(),
    )
    order.value = createRes.data
    paymentDialogVisible.value = true

    if (paymentMethod.value === 1) {
      const payRes = await startAlipay(order.value.orderNo)
      openAlipayPage(payRes.data, alipayWindow)
      ElMessage.success('支付宝收银台已在新窗口打开')
    } else {
      const payRes = await startWechatPay(order.value.orderNo)
      wechatQrCode.value = await QRCode.toDataURL(payRes.data, {
        width: 240,
        margin: 2,
        color: { dark: '#161625', light: '#ffffff' },
      })
    }
    startPolling()
  } catch (error) {
    if (alipayWindow && !alipayWindow.closed) alipayWindow.close()
  } finally {
    paying.value = false
  }
}

async function refreshOrder(showMessage = false) {
  if (!order.value?.orderNo) return
  try {
    const res = await getPaymentOrder(order.value.orderNo)
    order.value = res.data
    if (order.value.status === 1) {
      stopPolling()
      const vipRes = await getMyVip()
      vip.value = vipRes.data
      ElMessage.success('支付成功，VIP 权益已生效')
    } else if ([2, 3].includes(order.value.status)) {
      stopPolling()
      if (showMessage) ElMessage.info(orderStatus[order.value.status].label)
    } else if (showMessage) {
      ElMessage.info('订单状态已刷新')
    }
  } catch {
    stopPolling()
  }
}

function startPolling() {
  stopPolling()
  refreshOrder()
  pollTimer = window.setInterval(() => refreshOrder(), 2500)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function cancelOrder() {
  if (!order.value?.orderNo) return
  try {
    await ElMessageBox.confirm('确定取消当前支付订单吗？', '取消订单', {
      type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '继续支付',
    })
    await cancelPaymentOrder(order.value.orderNo)
    await refreshOrder()
    ElMessage.success('取消请求已提交')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') return
  }
}

function handleDialogClosed() {
  stopPolling()
  wechatQrCode.value = ''
}

onMounted(loadPage)
onUnmounted(stopPolling)
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
        <el-radio-group v-model="paymentMethod">
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
        <el-button class="pay-button" :loading="paying" @click="beginPayment">
          {{ vip?.vip ? '续订会员' : '立即开通' }}
        </el-button>
      </div>
    </section>

    <p class="demo-note">演示提示：实际拉起收银台需要在服务端配置对应渠道的沙箱或商户参数。</p>

    <el-dialog
      v-model="paymentDialogVisible"
      title="支付订单"
      width="min(460px, 92vw)"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <div v-if="order" class="order-dialog">
        <div class="order-status-row">
          <span>订单状态</span>
          <el-tag :type="orderStatus[order.status]?.type || 'info'">
            {{ orderStatus[order.status]?.label || '未知状态' }}
          </el-tag>
        </div>
        <div v-if="order.paymentMethod === 2 && wechatQrCode && ![1, 2, 3].includes(order.status)" class="qr-box">
          <img :src="wechatQrCode" alt="微信支付二维码" />
          <strong>请使用微信扫码支付</strong>
          <span>页面将自动查询支付结果</span>
        </div>
        <div v-else-if="order.paymentMethod === 1 && ![1, 2, 3].includes(order.status)" class="alipay-tip">
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
            v-if="![1, 2, 3].includes(order.status)"
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
  .hero-card { padding: 28px 24px; flex-direction: column; align-items: stretch; }
  .vip-state { min-width: 0; }
  .section-title { align-items: start; flex-direction: column; gap: 8px; }
  .plan-grid { grid-template-columns: 1fr; }
  .checkout-card, .payment-methods { align-items: stretch; flex-direction: column; }
  .checkout-action { justify-content: space-between; }
}
</style>
