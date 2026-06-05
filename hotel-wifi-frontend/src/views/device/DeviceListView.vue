<script setup lang="ts">
import { ref } from 'vue'
import { get, post } from '@/api'
import { ElMessage } from 'element-plus'

const tableData = ref([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/devices', { pageNum: 1, pageSize: 50 })
    tableData.value = res.records
  } finally {
    loading.value = false
  }
}

async function testConnection(id: number) {
  const res: any = await post(`/devices/${id}/test`)
  ElMessage.success(res.connected ? '连接成功' : '连接失败')
  loadData()
}

async function syncConfig(id: number) {
  await post(`/devices/${id}/sync`)
  ElMessage.success('同步完成')
  loadData()
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>设备管理</span>
        <el-button type="primary">添加设备</el-button>
      </div>
    </template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="deviceName" label="设备名称" />
      <el-table-column prop="host" label="IP/域名" width="160" />
      <el-table-column prop="apiPort" label="端口" width="80" />
      <el-table-column prop="hotelId" label="所属酒店" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ONLINE' ? 'success' : row.status === 'ERROR' ? 'danger' : 'info'">
            {{ row.status === 'ONLINE' ? '在线' : row.status === 'ERROR' ? '异常' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="testConnection(row.id)">测试连接</el-button>
          <el-button size="small" @click="syncConfig(row.id)">同步配置</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
