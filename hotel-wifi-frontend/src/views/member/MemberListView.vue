<script setup lang="ts">
import { ref } from 'vue'
import { get } from '@/api'

const tableData = ref([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/members', { pageNum: 1, pageSize: 50 })
    tableData.value = res.records
  } finally {
    loading.value = false
  }
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header"><span>会员管理</span></div>
    </template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="realName" label="姓名" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="balance" label="余额(元)" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '正常' : row.status === -1 ? '黑名单' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default>
          <el-button size="small" type="primary" link>充值</el-button>
          <el-button size="small" link>详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
