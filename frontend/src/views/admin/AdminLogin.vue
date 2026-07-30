<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '../../stores/modules/admin'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const adminStore = useAdminStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username.trim()) { ElMessage.warning('请输入用户名'); return }
  if (!form.password) { ElMessage.warning('请输入密码'); return }
  loading.value = true
  try {
    await adminStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')
    router.push('/admin')
  } catch { /* 错误由拦截器处理 */ }
  finally { loading.value = false }
}
</script>

<template>
  <div class="admin-login-page">
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>

    <div class="login-card">
      <div class="card-header">
        <div class="brand-mark">
          <span class="brand-icon">✦</span>
        </div>
        <h1 class="brand-name">管理员登录</h1>
        <p class="brand-sub">光影鉴赏家 · 管理后台</p>
      </div>

      <div class="login-form">
        <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" @keyup.enter="handleLogin" />
        <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        <button class="login-btn" :disabled="loading" @click="handleLogin">
          <span v-if="loading" class="btn-loader"></span>
          <span v-else>登 录</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-login-page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #0b0b16; position: relative; overflow: hidden; padding: 24px;
}

.bg-orb {
  position: absolute; border-radius: 50%; pointer-events: none; filter: blur(100px); opacity: .1;
}
.orb-1 { width: 500px; height: 500px; background: #f5af19; top: -200px; right: -100px; animation: orbFloat 8s ease-in-out infinite; }
.orb-2 { width: 400px; height: 400px; background: #f12711; bottom: -160px; left: -100px; animation: orbFloat 10s ease-in-out infinite reverse; }
@keyframes orbFloat {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(30px, -30px); }
  66% { transform: translate(-20px, 20px); }
}

.login-card {
  position: relative; z-index: 1; width: 400px;
  padding: 48px 44px 44px;
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
.card-header { text-align: center; margin-bottom: 36px; }
.brand-mark {
  width: 56px; height: 56px; margin: 0 auto 16px;
  border-radius: 16px; background: linear-gradient(135deg, #f5af19, #f12711);
  display: flex; align-items: center; justify-content: center;
}
.brand-icon { font-size: 24px; color: #fff; }
.brand-name { font-size: 24px; font-weight: 800; color: #f5af19; margin: 0 0 6px; letter-spacing: 1px; }
.brand-sub { font-size: 13px; color: rgba(255,255,255,.25); margin: 0; }

/* Form */
.login-form { display: flex; flex-direction: column; gap: 18px; }

/* Inputs */
.login-form :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.1) inset !important;
  border-radius: 12px !important; padding: 4px 16px !important; height: 50px !important;
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
.login-form :deep(.el-input__inner:-webkit-autofill),
.login-form :deep(.el-input__inner:-webkit-autofill:hover),
.login-form :deep(.el-input__inner:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0 1000px #12121e inset !important;
  -webkit-text-fill-color: rgba(255,255,255,.8) !important;
  transition: background-color 5000s ease-in-out 0s !important;
}

/* Button */
.login-btn {
  width: 100%; height: 50px; margin-top: 8px;
  background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 14px; font-size: 16px; font-weight: 600; color: #fff;
  letter-spacing: 8px; cursor: pointer; transition: all .3s ease;
  display: flex; align-items: center; justify-content: center;
}
.login-btn:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(245,175,25,.25); }
.login-btn:active { transform: scale(.98); }
.login-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; box-shadow: none; }
.btn-loader {
  width: 22px; height: 22px; border: 2px solid rgba(255,255,255,.25);
  border-top-color: #fff; border-radius: 50%; animation: spin .6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 480px) {
  .login-card { padding: 40px 28px 36px; }
}
</style>
