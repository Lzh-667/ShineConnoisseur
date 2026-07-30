<script setup>
import { ref, onMounted } from 'vue'
import { getAdminUsers, toggleUserStatus, getAdminUserInfo } from '../../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref([])
const current = ref(1)
const total = ref(0)
const pageSize = 10

const detailVisible = ref(false)
const detailUser = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await getAdminUsers(current.value)
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally { loading.value = false }
}

function onPageChange(p) { current.value = p; load() }

async function handleToggleStatus(user) {
  const action = user.status === 1 ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${user.nickname || user.username}」吗？`, '提示', {
      confirmButtonText: action, cancelButtonText: '取消', type: 'warning'
    })
    await toggleUserStatus(user.id)
    ElMessage.success(`已${action}`)
    load()
  } catch {}
}

async function handleViewDetail(user) {
  try {
    const res = await getAdminUserInfo(user.id)
    detailUser.value = res.data
    detailVisible.value = true
  } catch {}
}

function statusTag(s) { return s === 1 ? 'success' : 'danger' }
function statusText(s) { return s === 1 ? '正常' : '已封禁' }
function formatTime(t) { return t ? new Date(t).toLocaleDateString('zh-CN') : '' }

onMounted(load)
</script>

<template>
  <div class="user-mgmt">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
      </div>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>
    <template v-else>
      <div class="table-wrap">
        <el-table :data="list" style="width: 100%" row-key="id">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="36" :src="row.avatar" />
                <div class="user-info">
                  <span class="user-name">{{ row.nickname || row.username }}</span>
                  <span class="user-sub">@{{ row.username }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" width="200" />
          <el-table-column prop="phone" label="手机号" width="140" />
          <el-table-column prop="reviewCount" label="影评数" width="80" align="center" />
          <el-table-column prop="followerCount" label="粉丝数" width="80" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status)" size="small" effect="dark">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="120">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" @click="handleViewDetail(row)">详情</el-button>
              <el-button text size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '封禁' : '解封' }}
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

    <el-dialog v-model="detailVisible" title="用户详情" width="500px" class="admin-dialog">
      <div v-if="detailUser" class="user-detail">
        <div class="detail-header">
          <el-avatar :size="64" :src="detailUser.avatar" />
          <div class="detail-info">
            <h3>{{ detailUser.nickname || detailUser.username }}</h3>
            <p>@{{ detailUser.username }}</p>
            <el-tag :type="statusTag(detailUser.status)" size="small" effect="dark">{{ statusText(detailUser.status) }}</el-tag>
          </div>
        </div>
        <div class="detail-grid">
          <div class="detail-item"><label>ID</label><span>{{ detailUser.id }}</span></div>
          <div class="detail-item"><label>邮箱</label><span>{{ detailUser.email || '-' }}</span></div>
          <div class="detail-item"><label>手机号</label><span>{{ detailUser.phone || '-' }}</span></div>
          <div class="detail-item"><label>影评数</label><span>{{ detailUser.reviewCount ?? 0 }}</span></div>
          <div class="detail-item"><label>粉丝</label><span>{{ detailUser.followerCount ?? 0 }}</span></div>
          <div class="detail-item"><label>关注</label><span>{{ detailUser.followingCount ?? 0 }}</span></div>
        </div>
        <div class="detail-bio">
          <label>简介</label>
          <p>{{ detailUser.bio || '暂无简介' }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-title { font-size: 22px; color: #f5af19; margin: 0; }
.page-desc { font-size: 13px; color: rgba(255,255,255,.25); margin: 4px 0 0; }

.loading-state { text-align: center; padding: 80px 0; color: rgba(255,255,255,.25); font-size: 14px; }

.table-wrap { background: rgba(255,255,255,.015); border: 1px solid rgba(255,255,255,.05); border-radius: 12px; overflow: hidden; }
.pager { justify-content: center; margin-top: 24px; }

.user-cell { display: flex; align-items: center; gap: 10px; }
.user-info { display: flex; flex-direction: column; }
.user-name { font-size: 14px; color: rgba(255,255,255,.8); }
.user-sub { font-size: 12px; color: rgba(255,255,255,.35); }

/* ---- Detail dialog ---- */
.user-detail { padding: 4px 0; }
.detail-header { display: flex; align-items: center; gap: 16px; margin-bottom: 28px; }
.detail-header h3 { margin: 0 0 4px; font-size: 18px; color: rgba(255,255,255,.85); }
.detail-header p { margin: 0 0 8px; font-size: 13px; color: rgba(255,255,255,.35); }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px; }
.detail-item { display: flex; flex-direction: column; gap: 2px; padding: 10px 14px; background: rgba(255,255,255,.02); border-radius: 8px; }
.detail-item label { font-size: 11px; color: rgba(255,255,255,.3); text-transform: uppercase; letter-spacing: .5px; }
.detail-item span { font-size: 14px; color: rgba(255,255,255,.7); }

.detail-bio label { font-size: 11px; color: rgba(255,255,255,.3); text-transform: uppercase; letter-spacing: .5px; }
.detail-bio p { margin: 6px 0 0; font-size: 14px; color: rgba(255,255,255,.6); line-height: 1.7; padding: 10px 14px; background: rgba(255,255,255,.02); border-radius: 8px; }
</style>
