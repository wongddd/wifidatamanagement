<script setup lang="ts">
import { ref, reactive } from 'vue'
import { get, post, put } from '@/api'
import { ElMessage } from 'element-plus'

const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null as number | null,
  packageName: '',
  billingType: 'TIME',
  durationSeconds: 0,
  trafficBytes: 0,
  price: 0,
  maxDevices: 1,
  uploadLimitBps: 0,
  downloadLimitBps: 0,
  status: 1,
})

const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/packages', { pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.records
    page.total = res.total
  } finally { loading.value = false }
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, packageName: '', billingType: 'TIME', durationSeconds: 0,
    trafficBytes: 0, price: 0, maxDevices: 1, uploadLimitBps: 0, downloadLimitBps: 0, status: 1 })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value && form.id) {
    await put(`/packages/${form.id}`, form)
    ElMessage.success('套餐已更新')
  } else {
    await post('/packages', form)
    ElMessage.success('套餐已创建')
  }
  dialogVisible.value = false
  loadData()
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  await put(`/packages/${row.id}/status?status=${newStatus}`)
  ElMessage.success(newStatus === 1 ? '套餐已启用' : '套餐已停用')
  loadData()
}

function formatDuration(seconds: number) {
  if (seconds === 0) return '不限'
  if (seconds >= 31536000) return (seconds / 31536000).toFixed(0) + '年'
  if (seconds >= 2592000) return (seconds / 2592000).toFixed(0) + '月'
  if (seconds >= 86400) return (seconds / 86400).toFixed(0) + '天'
  return seconds + '秒'
}

function formatBytes(bytes: number) {
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB'
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB'
  if (bytes === 0) return '不限'
  return bytes + ' B'
}

function formatBps(bps: number) {
  if (bps >= 1000000) return (bps / 1000000).toFixed(1) + ' Mbps'
  if (bps >= 1000) return (bps / 1000).toFixed(0) + ' Kbps'
  if (bps === 0) return '不限'
  return bps + ' bps'
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header"><span>套餐管理</span><el-button type="primary" @click="handleCreate">新增套餐</el-button></div>
    </template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="packageName" label="套餐名称" min-width="140" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.billingType === 'TRAFFIC' ? 'warning' : 'primary'" size="small">
            {{ row.billingType === 'TIME' ? '包时' : row.billingType === 'TRAFFIC' ? '包流量' : '混合' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时长" width="90">
        <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
      </el-table-column>
      <el-table-column label="流量配额" width="110">
        <template #default="{ row }">{{ formatBytes(row.trafficBytes) }}</template>
      </el-table-column>
      <el-table-column label="价格" width="100">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      <el-table-column label="同时在线" width="90">
        <template #default="{ row }">{{ row.maxDevices }}台</template>
      </el-table-column>
      <el-table-column label="下行限速" width="110">
        <template #default="{ row }">{{ formatBps(row.downloadLimitBps) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" link @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
      :total="page.total" layout="total,prev,pager,next" @change="loadData"
      style="margin-top:16px;justify-content:flex-end" />

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑套餐' : '新增套餐'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="套餐名称"><el-input v-model="form.packageName" /></el-form-item>
        <el-form-item label="计费类型">
          <el-radio-group v-model="form.billingType">
            <el-radio value="TIME">包时</el-radio>
            <el-radio value="TRAFFIC">包流量</el-radio>
            <el-radio value="HYBRID">混合</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.billingType !== 'TRAFFIC'" label="时长(秒)">
          <el-input-number v-model="form.durationSeconds" :min="0" style="width:100%" />
          <span style="color:#909399;font-size:12px">86400=1天, 2592000=30天, 31536000=365天</span>
        </el-form-item>
        <el-form-item v-if="form.billingType !== 'TIME'" label="流量(字节)">
          <el-input-number v-model="form.trafficBytes" :min="0" :step="1073741824" style="width:100%" />
          <span style="color:#909399;font-size:12px">1073741824=1GB, 0=不限</span>
        </el-form-item>
        <el-form-item label="价格(元)"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="最大设备数"><el-input-number v-model="form.maxDevices" :min="1" :max="10" /></el-form-item>
        <el-form-item label="下行限速(bps)"><el-input-number v-model="form.downloadLimitBps" :min="0" :step="1048576" style="width:100%" /></el-form-item>
        <el-form-item label="上行限速(bps)"><el-input-number v-model="form.uploadLimitBps" :min="0" :step="524288" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.card-header { display:flex; justify-content:space-between; align-items:center; }
</style>
