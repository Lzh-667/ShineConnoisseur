<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAdminMovies, publishMovie, updateMovie, toggleMovieStatus } from '../../api/admin'
import { uploadPoster } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref([])
const current = ref(1)
const total = ref(0)
const pageSize = 10

const dialogVisible = ref(false)
const dialogTitle = ref('')
const editId = ref(null)
const form = reactive({ title: '', originalTitle: '', cover: '', director: '', actors: '', genre: '', region: '', language: '', releaseDate: '', duration: null, summary: '' })
const formLoading = ref(false)
const posterUploading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await getAdminMovies(current.value)
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally { loading.value = false }
}

function onPageChange(p) { current.value = p; load() }

function avgRating(row) {
  if (!row.ratingCount) return '-'
  return (row.ratingSum / row.ratingCount).toFixed(1)
}

function coverUrl(c) {
  if (!c) return ''
  if (c.startsWith('http://') || c.startsWith('https://') || c.startsWith('/')) return c
  return '/' + c
}

function openPublish() {
  dialogTitle.value = '发布电影'
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  dialogTitle.value = '编辑电影'
  editId.value = row.id
  form.title = row.title
  form.originalTitle = row.originalTitle || ''
  form.cover = row.cover || ''
  form.director = row.director || ''
  form.actors = row.actors || ''
  form.genre = row.genre || ''
  form.region = row.region || ''
  form.language = row.language || ''
  form.releaseDate = row.releaseDate || ''
  form.duration = row.duration
  form.summary = row.summary || ''
  dialogVisible.value = true
}

function resetForm() {
  form.title = ''; form.originalTitle = ''; form.cover = ''; form.director = ''
  form.actors = ''; form.genre = ''; form.region = ''; form.language = ''
  form.releaseDate = ''; form.duration = null; form.summary = ''
}

async function handlePosterUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { ElMessage.warning('请选择图片文件'); return }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片大小不能超过 5MB'); return }
  posterUploading.value = true
  try {
    const res = await uploadPoster(file)
    form.cover = res.data
    ElMessage.success('海报上传成功')
  } finally { posterUploading.value = false }
}

async function handleSubmit() {
  if (!form.title.trim()) { ElMessage.warning('请输入电影名称'); return }
  formLoading.value = true
  try {
    if (editId.value) {
      await updateMovie(editId.value, form)
      ElMessage.success('电影已更新')
    } else {
      await publishMovie(form)
      ElMessage.success('电影已发布')
    }
    dialogVisible.value = false
    load()
  } finally { formLoading.value = false }
}

async function handleToggleStatus(row) {
  const action = row.status === 1 ? '下架' : '上架'
  try {
    await ElMessageBox.confirm(`确定${action}电影「${row.title}」吗？`, '提示', {
      confirmButtonText: action, cancelButtonText: '取消', type: 'warning'
    })
    await toggleMovieStatus(row.id)
    ElMessage.success(`已${action}`)
    load()
  } catch {}
}

function statusTag(s) { return s === 1 ? 'success' : 'danger' }
function statusText(s) { return s === 1 ? '已上架' : '已下架' }

onMounted(load)
</script>

<template>
  <div class="movie-mgmt">
    <div class="page-header">
      <div>
        <h2 class="page-title">电影管理</h2>
      </div>
      <button class="add-btn" @click="openPublish">+ 发布电影</button>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>
    <template v-else>
      <div class="table-wrap">
        <el-table :data="list" style="width: 100%" row-key="id">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="电影" min-width="220">
            <template #default="{ row }">
              <div class="movie-cell">
                <img v-if="row.cover" :src="coverUrl(row.cover)" class="movie-cover" />
                <span v-else class="cover-placeholder">🎬</span>
                <div class="movie-info">
                  <span class="movie-title">{{ row.title }}</span>
                  <span v-if="row.originalTitle" class="movie-sub">{{ row.originalTitle }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="genre" label="类型" width="110" />
          <el-table-column prop="region" label="地区" width="80" />
          <el-table-column label="评分" width="100" align="center">
            <template #default="{ row }">
              <span class="rating">★ {{ avgRating(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="ratingCount" label="评分人数" width="100" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small" effect="dark">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" @click="openEdit(row)">编辑</el-button>
              <el-button text size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="total > pageSize"
          class="pager"
          background layout="prev, pager, next"
          :total="total" :page-size="pageSize"
          :current-page="current" @current-change="onPageChange"
        />
      </div>
    </template>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px" :close-on-click-modal="false" class="admin-dialog">
      <div class="movie-form">
        <el-input v-model="form.title" placeholder="电影名称" maxlength="100" />
        <el-input v-model="form.originalTitle" placeholder="原名（外文）" maxlength="100" />
        <div class="form-row">
          <el-input v-model="form.genre" placeholder="类型（逗号分隔，如：剧情,科幻）" />
          <el-input v-model="form.region" placeholder="地区" />
        </div>
        <div class="form-row">
          <el-input v-model="form.language" placeholder="语言" />
          <el-input v-model="form.duration" placeholder="片长（分钟）" type="number" />
        </div>
        <el-input v-model="form.releaseDate" placeholder="上映日期" type="date" />
        <el-input v-model="form.director" placeholder="导演（逗号分隔）" />
        <el-input v-model="form.actors" placeholder="演员（逗号分隔）" />
        <div class="cover-row">
          <el-input v-model="form.cover" placeholder="海报URL" />
          <label class="upload-poster-btn" :class="{ uploading: posterUploading }">
            {{ posterUploading ? '上传中' : '上传' }}
            <input type="file" accept="image/*" hidden @change="handlePosterUpload" :disabled="posterUploading" />
          </label>
        </div>
        <el-input v-model="form.summary" type="textarea" placeholder="剧情简介" :rows="4" resize="none" />
        <button class="form-submit" :disabled="formLoading" @click="handleSubmit">
          {{ formLoading ? '保存中...' : '保存' }}
        </button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0; }
.page-desc { font-size: 13px; color: rgba(255,255,255,.25); margin: 4px 0 0; }
.add-btn {
  padding: 10px 22px; background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 10px; color: #fff; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: opacity .2s;
}
.add-btn:hover { opacity: .85; }

.loading-state { text-align: center; padding: 80px 0; color: rgba(255,255,255,.25); font-size: 14px; }

.table-wrap { background: rgba(255,255,255,.015); border: 1px solid rgba(255,255,255,.05); border-radius: 12px; overflow: hidden; }
.pager { justify-content: center; margin-top: 24px; }

.movie-cell { display: flex; align-items: center; gap: 10px; }
.movie-cover { width: 40px; height: 56px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.cover-placeholder { width: 40px; height: 56px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.04); border-radius: 4px; font-size: 18px; flex-shrink: 0; }
.movie-info { display: flex; flex-direction: column; min-width: 0; }
.movie-title { font-size: 14px; color: rgba(255,255,255,.8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.movie-sub { font-size: 12px; color: rgba(255,255,255,.35); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rating { color: #f5af19; font-weight: 600; }

/* ---- Form ---- */
.movie-form { display: flex; flex-direction: column; gap: 14px; }
.cover-row { display: flex; gap: 10px; }
.cover-row > .el-input { flex: 1; }
.upload-poster-btn {
  flex-shrink: 0; display: flex; align-items: center; padding: 0 16px;
  background: rgba(245,175,25,.1); border: 1px solid rgba(245,175,25,.2);
  border-radius: 8px; color: #f5af19; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all .2s; height: 40px;
}
.upload-poster-btn:hover { background: rgba(245,175,25,.15); border-color: rgba(245,175,25,.35); }
.upload-poster-btn.uploading { opacity: .5; pointer-events: none; }

.form-row { display: flex; gap: 12px; }
.form-row > * { flex: 1; }
.movie-form :deep(.el-input__wrapper) {
  background: transparent !important; box-shadow: 0 0 0 1px rgba(255,255,255,.12) inset !important;
  border-radius: 8px !important;
}
.movie-form :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px rgba(255,255,255,.2) inset !important; }
.movie-form :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.movie-form :deep(.el-input__inner) { color: rgba(255,255,255,.7) !important; }
.movie-form :deep(.el-input__inner::placeholder) { color: rgba(255,255,255,.25) !important; }
.movie-form :deep(.el-textarea__inner) {
  background: transparent !important; box-shadow: 0 0 0 1px rgba(255,255,255,.12) inset !important;
  color: rgba(255,255,255,.7) !important; border-radius: 8px !important; resize: none;
}
.movie-form :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px #f5af19 inset !important; }
.movie-form :deep(.el-textarea__inner::placeholder) { color: rgba(255,255,255,.25) !important; }

.form-submit {
  width: 100%; padding: 12px; background: linear-gradient(135deg, #f5af19, #f12711);
  border: none; border-radius: 10px; font-size: 16px; font-weight: 600; color: #fff;
  cursor: pointer; transition: opacity .2s; margin-top: 4px;
}
.form-submit:hover { opacity: .85; }
.form-submit:disabled { opacity: .5; cursor: not-allowed; }
</style>
