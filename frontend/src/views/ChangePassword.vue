<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { updatePassword } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({ oldPassword: '', newPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度 6-30 个字符', trigger: 'blur' },
  ],
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.oldPassword === form.newPassword) {
    ElMessage.warning('新密码不能与原密码相同')
    return
  }
  loading.value = true
  try {
    await updatePassword(form)
    ElMessage.success('密码修改成功')
    router.back()
  } finally { loading.value = false }
}
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">修改密码</h2>
    <div class="card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" class="form-box">
        <el-form-item prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" placeholder="原密码" show-password />
        </el-form-item>
        <el-form-item prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="新密码" show-password />
        </el-form-item>
        <el-form-item>
          <button class="submit-btn" :disabled="loading" @click="handleSubmit">
            {{ loading ? '修改中...' : '确认修改' }}
          </button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.page-container { max-width: 440px; margin: 0 auto; padding: 48px 24px 80px; }
.page-title { font-size: 22px; color: #f5af19; text-align: center; margin: 0 0 32px; }

.card {
  background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.05);
  border-radius: 16px; padding: 36px 32px;
}

.form-box { display: flex; flex-direction: column; gap: 4px; }

/* Dark inputs */
.form-box :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.1) inset !important;
  border-radius: 10px !important; transition: all .25s !important;
}
.form-box :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255,255,255,.2) inset !important;
}
.form-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f5af19 inset !important;
}
.form-box :deep(.el-input__inner) { color: rgba(255,255,255,.7) !important; }
.form-box :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.22) !important; }

.submit-btn {
  width: 100%; padding: 13px; background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 12px; font-size: 16px; font-weight: 600; color: #fff;
  cursor: pointer; transition: all .2s;
}
.submit-btn:hover { opacity: .85; transform: translateY(-1px); }
.submit-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; }
</style>
