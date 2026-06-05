<script setup lang="ts">
import { ref } from 'vue'
import { get } from '@/api'

const tableData = ref([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/hotels', { pageNum: 1, pageSize: 50 })
    tableData.value = res.records
  } finally {
    loading.value = false
  }
}

loadData()
</script>

<template>
  <el-card>
    <template #header><div class="card-header"><span>酒店管理</span></div></template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="hotelName" label="酒店名称" />
      <el-table-column prop="hotelCode" label="编码" width="120" />
      <el-table-column prop="roomCount" label="房间数" width="100" />
      <el-table-column prop="address" label="地址" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
