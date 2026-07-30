<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/modules/user'
import { getUserInfo, updateProfile, uploadAvatar } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const uploading = ref(false)

const form = reactive({ nickname: '', bio: '', avatar: '', gender: null })

const rules = {
  nickname: [{ max: 30, message: '昵称最长 30 个字符', trigger: 'blur' }],
  bio: [{ max: 200, message: '简介最长 200 个字符', trigger: 'blur' }],
}

async function load() {
  const id = userStore.userInfo?.id
  if (!id) { router.push({ name: 'Login', query: { redirect: route.fullPath } }); return }
  const res = await getUserInfo(id)
  const info = res.data
  form.nickname = info.nickname || ''
  form.bio = info.bio || ''
  form.avatar = info.avatar || ''
  form.gender = info.gender ?? null
}

async function handleUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) {
    ElMessage.warning('仅支持 jpg/png/gif/webp 格式')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadAvatar(file)
    form.avatar = res.data
    ElMessage.success('头像上传成功')
  } finally { uploading.value = false }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = {}
    if (form.nickname) data.nickname = form.nickname
    if (form.bio) data.bio = form.bio
    if (form.avatar) data.avatar = form.avatar
    if (form.gender !== null && form.gender !== undefined && form.gender !== '') data.gender = form.gender
    await updateProfile(data)
    userStore.userInfo = { ...userStore.userInfo, ...data }
    ElMessage.success('资料已更新')
    router.back()
  } finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">编辑资料</h2>
    <div class="card">
      <div class="avatar-section">
        <el-avatar :size="80" :src="form.avatar" class="avatar-img" />
        <div class="avatar-info">
          <label class="upload-label">
            {{ uploading ? '上传中...' : '更换头像' }}
            <input type="file" accept="image/*" hidden @change="handleUpload" :disabled="uploading" />
          </label>
          <span class="upload-hint">支持 JPG、PNG、GIF、WebP，最大 5MB</span>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" class="form-box">
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item prop="bio">
          <el-input v-model="form.bio" type="textarea" placeholder="个性签名" maxlength="200" show-word-limit :rows="3" />
        </el-form-item>
        <el-form-item prop="gender">
          <el-select v-model="form.gender" placeholder="性别" clearable class="full-width">
            <el-option :value="0" label="保密" />
            <el-option :value="1" label="男" />
            <el-option :value="2" label="女" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <button class="submit-btn" :disabled="loading" @click="handleSubmit">
            {{ loading ? '保存中...' : '保存' }}
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

.avatar-section { display: flex; align-items: center; gap: 18px; justify-content: center; margin-bottom: 28px; }
.avatar-img { border: 2px solid rgba(245,175,25,.15); }
.avatar-info { display: flex; flex-direction: column; gap: 4px; }
.upload-label {
  font-size: 14px; color: #f5af19; cursor: pointer; font-weight: 500; transition: color .2s;
}
.upload-label:hover { color: #f7c04a; }
.upload-hint { font-size: 11px; color: rgba(255,255,255,.2); }

.form-box { display: flex; flex-direction: column; gap: 4px; }
.full-width { width: 100%; }

/* Dark inputs */
.form-box :deep(.el-input__wrapper),
.form-box :deep(.el-select__wrapper) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.1) inset !important;
  border-radius: 10px !important; transition: all .25s !important;
}
.form-box :deep(.el-input__wrapper:hover),
.form-box :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255,255,255,.2) inset !important;
}
.form-box :deep(.el-input__wrapper.is-focus),
.form-box :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #f5af19 inset !important;
}
.form-box :deep(.el-input__inner) { color: rgba(255,255,255,.7) !important; }
.form-box :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.22) !important; }
.form-box :deep(.el-textarea__inner) {
  background: transparent !important;
  box-shadow: 0 0 0 1px rgba(255,255,255,.1) inset !important;
  color: rgba(255,255,255,.7) !important; border-radius: 10px !important; resize: none;
}
.form-box :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.form-box :deep(.el-textarea__inner::placeholder) { color: rgba(255,255,255,.22) !important; }

.submit-btn {
  width: 100%; padding: 13px; background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 12px; font-size: 16px; font-weight: 600; color: #fff;
  cursor: pointer; transition: all .2s;
}
.submit-btn:hover { opacity: .85; transform: translateY(-1px); }
.submit-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; }
</style>
