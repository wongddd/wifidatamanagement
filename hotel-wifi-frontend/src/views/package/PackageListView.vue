<script setup lang="ts">
import { ref } from 'vue'
import { get } from '@/api'

const tableData = ref([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/packages', { pageNum: 1, pageSize: 50 })
    tableData.value = res.records
  } finally {
    loading.value = false
  }
}

function formatBillingType(type: string) {
  const map: Record<string, string> = { TIME: '包时', TRAFFIC: '包流量', HYBRID: '混合' }
  return map[type] || type
}

function formatBytes(bytes: number) {
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + 'GB'
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + 'MB'
  if (bytes === 0) return '不限'
  return bytes + 'B'
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header"><span>套餐管理</span><el-button type="primary">新增套餐</el-button></div>
    </template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="packageName" label="套餐名称" />
      <el-table-column label="计费类型" width="100">
        <template #default="{ row }">{{ formatBillingType(row.billingType) }}</template>
      </el-table-column>
      <el-table-column prop="price" label="价格(元)" width="100" />
      <el-table-column label="流量配额" width="120">
        <template #default="{ row }">{{ formatBytes(row.trafficBytes) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default>
          <el-button size="small" type="primary" link>编辑</el-button>
          <el-button size="small" link>启用/停用</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
