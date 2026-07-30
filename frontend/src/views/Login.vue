<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { sendLoginCode } from '../api'
import { ElMessage } from 'element-plus'
import { User, Lock, Iphone, Message } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const passwordFormRef = ref(null)
const codeFormRef = ref(null)
const loading = ref(false)
const codeSending = ref(false)
const countdown = ref(0)
const activeTab = ref('password')

const passwordForm = reactive({ username: '', password: '' })
const codeForm = reactive({ phone: '', code: '' })

const passwordRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{6,32}$/, message: '密码为6-32位数字、字母、下划线', trigger: 'blur' },
  ],
}
const codeRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function handlePasswordLogin() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.loginByPasswordAction(passwordForm)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } finally { loading.value = false }
}

async function handleCodeLogin() {
  const valid = await codeFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.loginByCodeAction(codeForm)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } finally { loading.value = false }
}

async function handleSendCode() {
  if (countdown.value > 0) return
  const phone = codeForm.phone
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  codeSending.value = true
  try {
    await sendLoginCode(phone)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally { codeSending.value = false }
}
</script>

<template>
  <div class="auth-page">
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>

    <div class="auth-card">
      <div class="card-header">
        <div class="brand-mark">
          <span class="brand-icon">✦</span>
        </div>
        <h1 class="brand-name">光影鉴赏家</h1>
        <p class="brand-desc">欢迎回来，继续你的电影之旅</p>
      </div>

      <div class="tab-row">
        <button class="tab-btn" :class="{ active: activeTab === 'password' }" @click="activeTab = 'password'">密码登录</button>
        <button class="tab-btn" :class="{ active: activeTab === 'code' }" @click="activeTab = 'code'">验证码登录</button>
      </div>

      <!-- 密码登录 -->
      <el-form
        v-show="activeTab === 'password'"
        ref="passwordFormRef" :model="passwordForm" :rules="passwordRules"
        label-width="0" size="large" class="login-form"
        @keyup.enter="handlePasswordLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="passwordForm.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="passwordForm.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <button class="submit-btn" :disabled="loading" @click="handlePasswordLogin">
          <span v-if="loading" class="btn-loader"></span>
          <span v-else>登 录</span>
        </button>
      </el-form>

      <!-- 验证码登录 -->
      <el-form
        v-show="activeTab === 'code'"
        ref="codeFormRef" :model="codeForm" :rules="codeRules"
        label-width="0" size="large" class="login-form"
        @keyup.enter="handleCodeLogin"
      >
        <el-form-item prop="phone">
          <el-input v-model="codeForm.phone" placeholder="手机号" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item prop="code">
          <div class="code-row">
            <el-input v-model="codeForm.code" placeholder="验证码" :prefix-icon="Message" />
            <button class="code-btn" :disabled="countdown > 0 || codeSending" @click="handleSendCode">
              {{ codeSending ? '发送中' : countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </button>
          </div>
        </el-form-item>
        <button class="submit-btn" :disabled="loading" @click="handleCodeLogin">
          <span v-if="loading" class="btn-loader"></span>
          <span v-else>登 录</span>
        </button>
      </el-form>

      <div class="card-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
        <span class="footer-divider">|</span>
        <router-link to="/reset-password">忘记密码</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #0b0b16; position: relative; overflow: hidden; padding: 24px;
}
.bg-orb {
  position: absolute; border-radius: 50%; pointer-events: none; filter: blur(100px); opacity: .12;
}
.orb-1 { width: 520px; height: 520px; background: #f5af19; top: -180px; right: -100px; animation: orbFloat 8s ease-in-out infinite; }
.orb-2 { width: 400px; height: 400px; background: #f12711; bottom: -140px; left: -80px; animation: orbFloat 10s ease-in-out infinite reverse; }
.orb-3 { width: 300px; height: 300px; background: #667eea; top: 50%; left: 50%; transform: translate(-50%, -50%); animation: orbFloat 12s ease-in-out infinite; }
@keyframes orbFloat {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(30px, -30px); }
  66% { transform: translate(-20px, 20px); }
}

.auth-card {
  position: relative; z-index: 1; width: 420px;
  padding: 48px 44px 40px;
  background: rgba(255,255,255,.02);
  backdrop-filter: blur(40px);
  -webkit-backdrop-filter: blur(40px);
  border: 1px solid rgba(255,255,255,.06);
  border-radius: 24px;
  animation: cardIn .6s ease-out;
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Header */
.card-header { text-align: center; margin-bottom: 32px; }
.brand-mark {
  width: 56px; height: 56px; margin: 0 auto 16px;
  border-radius: 16px; background: linear-gradient(135deg, #f5af19, #f12711);
  display: flex; align-items: center; justify-content: center;
}
.brand-icon { font-size: 24px; color: #fff; }
.brand-name { font-size: 24px; font-weight: 800; color: #f5af19; margin: 0 0 6px; letter-spacing: 1px; }
.brand-desc { font-size: 13px; color: rgba(255,255,255,.3); margin: 0; }

/* Tabs */
.tab-row { display: flex; gap: 4px; margin-bottom: 28px; background: rgba(255,255,255,.03); border-radius: 12px; padding: 4px; }
.tab-btn {
  flex: 1; padding: 10px 0; border: none; background: transparent;
  border-radius: 10px; color: rgba(255,255,255,.35); font-size: 14px; font-weight: 500;
  cursor: pointer; transition: all .25s;
}
.tab-btn.active { background: rgba(245,175,25,.12); color: #f5af19; font-weight: 600; }
.tab-btn:hover:not(.active) { color: rgba(255,255,255,.55); }

/* Form */
.login-form { display: flex; flex-direction: column; }
.login-form :deep(.el-form-item) { margin-bottom: 18px; }

/* Inputs */
.login-form :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.1) inset !important;
  border-radius: 12px !important; padding: 4px 16px !important; height: 48px !important;
  transition: all .3s ease !important;
}
.login-form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px rgba(255,255,255,.2) inset !important; }
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f5af19 inset, 0 0 0 4px rgba(245,175,25,.06) !important;
}
.login-form :deep(.el-input__inner) { font-size: 15px !important; color: rgba(255,255,255,.8) !important; }
.login-form :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.2) !important; }
.login-form :deep(.el-input__prefix) { color: rgba(255,255,255,.2); margin-right: 6px; }
.login-form :deep(.el-input__suffix) { color: rgba(255,255,255,.2) !important; }
.login-form :deep(.el-input__suffix:hover) { color: rgba(255,255,255,.45) !important; }

/* Autofill */
.login-form :deep(.el-input__inner:-webkit-autofill),
.login-form :deep(.el-input__inner:-webkit-autofill:hover),
.login-form :deep(.el-input__inner:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0 1000px #12121e inset !important;
  -webkit-text-fill-color: rgba(255,255,255,.8) !important;
  transition: background-color 5000s ease-in-out 0s !important;
}

/* Submit */
.submit-btn {
  width: 100%; height: 50px; margin-top: 4px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 14px; font-size: 16px; font-weight: 600; color: #fff;
  letter-spacing: 8px; cursor: pointer; transition: all .3s ease;
  display: flex; align-items: center; justify-content: center;
}
.submit-btn:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(245,175,25,.25); }
.submit-btn:active { transform: scale(.98); }
.submit-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; box-shadow: none; }
.btn-loader {
  width: 22px; height: 22px; border: 2px solid rgba(255,255,255,.25);
  border-top-color: #fff; border-radius: 50%; animation: spin .6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Code */
.code-row { display: flex; gap: 10px; }
.code-btn {
  flex-shrink: 0; height: 48px; min-width: 100px; padding: 0 12px;
  border-radius: 12px; font-size: 13px; font-weight: 500;
  background: rgba(245,175,25,.08); border: 1px solid rgba(245,175,25,.15); color: #f5af19;
  cursor: pointer; transition: all .2s; white-space: nowrap;
}
.code-btn:hover { background: rgba(245,175,25,.14); border-color: rgba(245,175,25,.3); }
.code-btn:disabled { opacity: .4; cursor: not-allowed; }

/* Footer */
.card-footer { text-align: center; color: rgba(255,255,255,.25); font-size: 13px; margin-top: 28px; }
.card-footer a { color: #f5af19; text-decoration: none; font-weight: 500; margin-left: 4px; }
.card-footer a:hover { color: #f7c04a; }
.footer-divider { color: rgba(255,255,255,.15); margin: 0 6px; }

/* Error */
.login-form :deep(.el-form-item.is-error .el-input__wrapper) { box-shadow: 0 0 0 1px rgba(245,108,108,.4) inset !important; }
.login-form :deep(.el-form-item__error) { color: rgba(245,108,108,.75) !important; font-size: 12px !important; padding-top: 4px !important; }

@media (max-width: 480px) {
  .auth-card { padding: 40px 28px 32px; }
}
</style>
