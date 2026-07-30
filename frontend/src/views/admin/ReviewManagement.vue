<script setup>
import { ref, onMounted } from 'vue'
import { getAdminReviews, toggleReviewStatus } from '../../api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref([])
const current = ref(1)
const total = ref(0)
const pageSize = 10

const detailVisible = ref(false)
const detailReview = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await getAdminReviews(current.value)
    list.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } finally { loading.value = false }
}

function onPageChange(p) { current.value = p; load() }

function viewDetail(row) {
  detailReview.value = row
  detailVisible.value = true
}

async function handleToggleStatus(row) {
  const action = row.status === 1 ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(`确定${action}该影评吗？`, '提示', {
      confirmButtonText: action, cancelButtonText: '取消', type: 'warning'
    })
    await toggleReviewStatus(row.id)
    ElMessage.success(`已${action}`)
    load()
  } catch {}
}

function statusType(s) {
  if (s === 1) return 'success'
  if (s === 2) return 'danger'
  return 'info'
}
function statusText(s) {
  if (s === 1) return '正常'
  if (s === 2) return '已封禁'
  return '已删除'
}
function truncate(t, n = 40) { return t?.length > n ? t.slice(0, n) + '...' : t }

onMounted(load)
</script>

<template>
  <div class="review-mgmt">
    <div class="page-header">
      <div>
        <h2 class="page-title">影评管理</h2>
      </div>
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>
    <template v-else>
      <div class="table-wrap">
        <el-table :data="list" style="width: 100%" row-key="id">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="标题 / 内容" min-width="300">
            <template #default="{ row }">
              <div class="review-cell">
                <span class="review-title">{{ row.title }}</span>
                <span class="review-excerpt">{{ truncate(row.content, 60) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="评分" width="80" align="center">
            <template #default="{ row }">
              <span class="rating">★ {{ row.rating }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="用户ID" width="80" align="center" />
          <el-table-column prop="movieId" label="电影ID" width="80" align="center" />
          <el-table-column prop="likeCount" label="点赞" width="70" align="center" />
          <el-table-column prop="commentCount" label="评论" width="70" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small" effect="dark">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" @click="viewDetail(row)">详情</el-button>
              <el-button
                v-if="row.status !== 0"
                text size="small"
                :type="row.status === 1 ? 'danger' : 'success'"
                @click="handleToggleStatus(row)"
              >
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

    <el-dialog v-model="detailVisible" title="影评详情" width="560px" class="admin-dialog">
      <div v-if="detailReview" class="review-detail">
        <div class="detail-grid">
          <div class="detail-item"><label>ID</label><span>{{ detailReview.id }}</span></div>
          <div class="detail-item"><label>评分</label><span class="rating">★ {{ detailReview.rating }}</span></div>
          <div class="detail-item"><label>用户ID</label><span>{{ detailReview.userId }}</span></div>
          <div class="detail-item"><label>电影ID</label><span>{{ detailReview.movieId }}</span></div>
          <div class="detail-item"><label>点赞</label><span>{{ detailReview.likeCount }}</span></div>
          <div class="detail-item"><label>评论</label><span>{{ detailReview.commentCount }}</span></div>
        </div>
        <div class="detail-block">
          <label>标题</label>
          <p>{{ detailReview.title }}</p>
        </div>
        <div class="detail-block">
          <label>内容</label>
          <p>{{ detailReview.content }}</p>
        </div>
        <div class="detail-status">
          <label>状态</label>
          <el-tag :type="statusType(detailReview.status)" size="small" effect="dark">{{ statusText(detailReview.status) }}</el-tag>
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

.review-cell { display: flex; flex-direction: column; min-width: 0; }
.review-title { font-size: 14px; color: rgba(255,255,255,.8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.review-excerpt { font-size: 12px; color: rgba(255,255,255,.35); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rating { color: #f5af19; font-weight: 600; }

/* ---- Detail dialog ---- */
.review-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.detail-item { display: flex; flex-direction: column; gap: 2px; padding: 10px 14px; background: rgba(255,255,255,.02); border-radius: 8px; }
.detail-item label { font-size: 11px; color: rgba(255,255,255,.3); text-transform: uppercase; letter-spacing: .5px; }
.detail-item span { font-size: 14px; color: rgba(255,255,255,.7); }

.detail-block { display: flex; flex-direction: column; gap: 6px; }
.detail-block label { font-size: 11px; color: rgba(255,255,255,.3); text-transform: uppercase; letter-spacing: .5px; }
.detail-block p { margin: 0; font-size: 14px; color: rgba(255,255,255,.65); line-height: 1.8; padding: 10px 14px; background: rgba(255,255,255,.02); border-radius: 8px; white-space: pre-wrap; word-break: break-word; }

.detail-status { display: flex; align-items: center; gap: 12px; }
.detail-status label { font-size: 11px; color: rgba(255,255,255,.3); text-transform: uppercase; letter-spacing: .5px; }
</style>
