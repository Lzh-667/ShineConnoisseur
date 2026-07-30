<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { sendRegisterCode, register as registerApi, getCurrentUser } from '../api'
import { setToken } from '../utils/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const codeSending = ref(false)
const countdown = ref(0)

const form = reactive({
  phone: '', code: '', username: '', password: '', confirmPassword: '', email: '',
})

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 30, message: '用户名长度 2-30 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{6,32}$/, message: '密码为6-32位数字、字母、下划线', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function handleSendCode() {
  if (countdown.value > 0) return
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  codeSending.value = true
  try {
    await sendRegisterCode(form.phone)
    ElMessage.success('验证码已发送')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally { codeSending.value = false }
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await registerApi(form)
    userStore.token = res.data
    setToken(res.data)
    try { const me = await getCurrentUser(); userStore.userInfo = me.data } catch {}
    ElMessage.success('注册成功，已自动登录')
    router.push(route.query.redirect || '/')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="auth-page">
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>

    <div class="auth-card auth-register-card">
      <div class="card-header">
        <div class="header-icon">🎬</div>
        <h1 class="header-title">光影鉴赏家</h1>
        <p class="header-desc">创建账号，开始你的电影之旅</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="phone">
          <div class="code-row">
            <el-input v-model="form.phone" placeholder="手机号" />
            <el-button class="code-btn" :loading="codeSending" :disabled="countdown > 0" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item prop="code">
          <el-input v-model="form.code" placeholder="短信验证码" />
        </el-form-item>
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（6-32位数字、字母、下划线）" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="邮箱（选填）" />
        </el-form-item>
        <el-form-item>
          <button class="submit-btn" :disabled="loading" @click="handleRegister">
            <span v-if="loading" class="btn-loading"></span>
            <span v-else>注 册</span>
          </button>
        </el-form-item>
      </el-form>

      <div class="card-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========== Page ========== */
.auth-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #0d0d1a;
  position: relative; overflow: hidden;
  padding: 24px;
}
.bg-orb {
  position: absolute; border-radius: 50%; pointer-events: none; filter: blur(80px); opacity: .15;
}
.orb-1 { width: 500px; height: 500px; background: #f5af19; top: -150px; right: -150px; }
.orb-2 { width: 400px; height: 400px; background: #f12711; bottom: -120px; left: -120px; }

/* ========== Card ========== */
.auth-card {
  position: relative; z-index: 1;
  width: 420px; padding: 40px 40px 32px;
  background: rgba(255,255,255,.03);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255,255,255,.08);
  border-radius: 20px;
  box-shadow: 0 8px 40px rgba(0,0,0,.4);
}

/* ========== Header ========== */
.card-header { text-align: center; margin-bottom: 28px; }
.header-icon { font-size: 36px; margin-bottom: 10px; }
.header-title { font-size: 22px; font-weight: 800; color: #f5af19; margin: 0 0 4px; letter-spacing: 1px; }
.header-desc { font-size: 13px; color: rgba(255,255,255,.35); margin: 0; }

/* ========== Submit Button ========== */
.submit-btn {
  width: 100%; height: 46px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 12px;
  font-size: 16px; font-weight: 600; color: #fff; letter-spacing: 6px;
  cursor: pointer; transition: all .3s ease;
  display: flex; align-items: center; justify-content: center;
}
.submit-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(245,175,25,.25); }
.submit-btn:active { transform: scale(.98); }
.submit-btn:disabled { opacity: .6; cursor: not-allowed; transform: none; box-shadow: none; }
.btn-loading {
  width: 20px; height: 20px; border: 2px solid rgba(255,255,255,.3);
  border-top-color: #fff; border-radius: 50%; animation: spin .6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== Code Row ========== */
.code-row { display: flex; gap: 12px; }
.code-btn {
  flex-shrink: 0; width: 110px; height: 40px;
  border-radius: 10px; font-size: 13px;
  background: rgba(245,175,25,.1); border: 1px solid rgba(245,175,25,.2); color: #f5af19;
}
.code-btn:hover { background: rgba(245,175,25,.15); border-color: rgba(245,175,25,.35); }
.code-btn:disabled { opacity: .4; }

/* ========== Footer ========== */
.card-footer { text-align: center; color: rgba(255,255,255,.3); font-size: 13px; margin-top: 20px; }
.card-footer a { color: #f5af19; text-decoration: none; font-weight: 500; margin-left: 4px; }
.card-footer a:hover { color: #f7c04a; }

@media (max-width: 480px) {
  .auth-card { padding: 32px 24px 24px; }
}
</style>

<style>
/* ====== Input Fields ====== */
.auth-register-card .el-form-item {
  margin-bottom: 20px;
}
.auth-register-card .el-input__wrapper {
  background: rgba(255,255,255,.04) !important;
  box-shadow: none !important;
  border-radius: 12px !important;
  padding: 4px 18px !important;
  height: 50px !important;
  border: 1px solid rgba(255,255,255,.06) !important;
  transition: all .3s ease !important;
}
.auth-register-card .el-input__wrapper:hover {
  background: rgba(255,255,255,.06) !important;
  border-color: rgba(255,255,255,.12) !important;
}
.auth-register-card .el-input__wrapper.is-focus {
  background: rgba(255,255,255,.06) !important;
  border-color: #f5af19 !important;
  box-shadow: 0 0 0 3px rgba(245,175,25,.1) !important;
}
.auth-register-card .el-input__inner {
  font-size: 15px !important;
  color: rgba(255,255,255,.85) !important;
}
.auth-register-card .el-input__inner::placeholder {
  color: rgba(255,255,255,.22) !important;
  font-size: 14px !important;
}
/* Password visibility toggle */
.auth-register-card .el-input__suffix { color: rgba(255,255,255,.25) !important; }
.auth-register-card .el-input__suffix:hover { color: rgba(255,255,255,.5) !important; }

/* ====== Autofill Override ====== */
.auth-register-card .el-input__inner:-webkit-autofill,
.auth-register-card .el-input__inner:-webkit-autofill:hover,
.auth-register-card .el-input__inner:-webkit-autofill:focus,
.auth-register-card .el-input__inner:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 1000px #1a1a2e inset !important;
  -webkit-text-fill-color: rgba(255,255,255,.85) !important;
  caret-color: rgba(255,255,255,.85) !important;
  transition: background-color 5000s ease-in-out 0s !important;
}

/* ====== Error state ====== */
.auth-register-card .el-form-item.is-error .el-input__wrapper {
  border-color: rgba(245,108,108,.4) !important;
}
.auth-register-card .el-form-item__error {
  color: rgba(245,108,108,.8) !important;
  font-size: 12px !important;
  padding-top: 4px !important;
}
</style>
