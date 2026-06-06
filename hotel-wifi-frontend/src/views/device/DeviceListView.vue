<script setup lang="ts">
import { ref, reactive } from 'vue'
import { get, post, put, del } from '@/api'
import { ElMessage } from 'element-plus'

const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null as number | null,
  deviceName: '', host: '', apiPort: 8728, apiUser: 'admin', apiPassword: '',
  hotelId: 1, hotspotServer: '', addressPool: '',
})

const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/devices', { pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.records
    page.total = res.total
  } finally { loading.value = false }
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, deviceName: '', host: '', apiPort: 8728, apiUser: 'admin', apiPassword: '',
    hotelId: 1, hotspotServer: '', addressPool: '' })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  Object.assign(form, { ...row, apiPassword: '' })
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value && form.id) {
    await put(`/devices/${form.id}`, form)
    ElMessage.success('设备已更新')
  } else {
    await post('/devices', form)
    ElMessage.success('设备已添加')
  }
  dialogVisible.value = false
  
async function checkAgentStatus(id: number) {
  try {
    const res: any = await get(`/devices/relay/${id}/status`)
    ElMessage.success(res.online ? 'Agent在线，命令可达' : 'Agent离线，无法远程管理')
  } catch { ElMessage.error('检查失败') }
}

loadData()}

async function handleDelete(row: any) {
  await del(`/devices/${row.id}`)
  ElMessage.success('设备已删除')
  
async function checkAgentStatus(id: number) {
  try {
    const res: any = await get(`/devices/relay/${id}/status`)
    ElMessage.success(res.online ? 'Agent在线，命令可达' : 'Agent离线，无法远程管理')
  } catch { ElMessage.error('检查失败') }
}

loadData()}

async function testConnection(id: number) {
  try {
    const res: any = await post(`/devices/${id}/test`)
    ElMessage.success(res.connected ? '连接成功！设备在线' : '连接失败，请检查配置')
  } catch {
    ElMessage.error('连接测试失败')
  }
  
async function checkAgentStatus(id: number) {
  try {
    const res: any = await get(`/devices/relay/${id}/status`)
    ElMessage.success(res.online ? 'Agent在线，命令可达' : 'Agent离线，无法远程管理')
  } catch { ElMessage.error('检查失败') }
}

loadData()}

async function syncConfig(id: number) {
  try {
    await post(`/devices/${id}/sync`)
    ElMessage.success('配置同步完成')
  } catch {
    ElMessage.error('同步失败')
  }
}


async function checkAgentStatus(id: number) {
  try {
    const res: any = await get(`/devices/relay/${id}/status`)
    ElMessage.success(res.online ? 'Agent在线，命令可达' : 'Agent离线，无法远程管理')
  } catch { ElMessage.error('检查失败') }
}

loadData()</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header"><span>设备管理</span><el-button type="primary" @click="handleCreate">添加设备</el-button></div>
    </template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="deviceName" label="设备名称" min-width="140" />
      <el-table-column label="模式" width="90">
          <template #default="{ row }">
            <el-tag :type="row.host?.startsWith('ws://') ? 'warning' : 'primary'" size="small">
              {{ row.host?.startsWith('ws://') ? 'Agent' : '直连' }}
            </el-tag>
          </template>
        </el-table-column>
<el-table-column prop="host" label="IP/域名" width="150" />
      <el-table-column prop="apiPort" label="端口" width="80" />
      <el-table-column prop="hotelId" label="酒店ID" width="80" />
      <el-table-column prop="hotspotServer" label="Hotspot Server" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : row.status === 'ERROR' ? 'danger' : 'info'" size="small">
            {{ row.status === 'ONLINE' ? '在线' : row.status === 'ERROR' ? '异常' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="success" link @click="testConnection(row.id)">测试</el-button>
          <el-button size="small" type="warning" link @click="syncConfig(row.id)">同步</el-button>
          <el-button size="small" type="info" link @click="checkAgentStatus(row.id)">Agent</el-button>
          <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
      :total="page.total" layout="total,prev,pager,next" @change="loadData"
      style="margin-top:16px;justify-content:flex-end" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '添加设备'" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="设备名称"><el-input v-model="form.deviceName" /></el-form-item>
        <el-form-item label="IP/域名"><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="API端口"><el-input-number v-model="form.apiPort" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="API用户名"><el-input v-model="form.apiUser" /></el-form-item>
        <el-form-item label="API密码">
          <el-input v-model="form.apiPassword" type="password" :placeholder="isEdit ? '留空不修改' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="所属酒店ID"><el-input-number v-model="form.hotelId" :min="1" /></el-form-item>
        <el-form-item label="Hotspot Server"><el-input v-model="form.hotspotServer" /></el-form-item>
        <el-form-item label="IP地址池"><el-input v-model="form.addressPool" /></el-form-item>
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
