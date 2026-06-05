<script setup lang="ts">
import { ref } from 'vue'
import { get, post } from '@/api'

const activeSessions = ref([])
const loading = ref(false)

async function loadActiveSessions() {
  loading.value = true
  try {
    activeSessions.value = await get('/billing/sessions/active')
  } finally {
    loading.value = false
  }
}

async function kickSession(id: number) {
  await post(`/billing/sessions/${id}/kick`)
  loadActiveSessions()
}

loadActiveSessions()
</script>

<template>
  <el-row :gutter="20">
    <el-col :span="24">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>实时在线用户</span>
            <el-button type="primary" @click="loadActiveSessions">刷新</el-button>
          </div>
        </template>
        <el-table :data="activeSessions" v-loading="loading" border stripe>
          <el-table-column prop="id" label="会话ID" width="80" />
          <el-table-column prop="memberId" label="会员ID" width="80" />
          <el-table-column prop="macAddress" label="MAC地址" width="150" />
          <el-table-column prop="ipAddress" label="IP地址" width="140" />
          <el-table-column prop="loginAt" label="上线时间" width="180" />
          <el-table-column label="下载流量" width="120">
            <template #default="{ row }">
              {{ (row.totalBytesIn / 1048576).toFixed(2) }} MB
            </template>
          </el-table-column>
          <el-table-column label="上传流量" width="120">
            <template #default="{ row }">
              {{ (row.totalBytesOut / 1048576).toFixed(2) }} MB
            </template>
          </el-table-column>
          <el-table-column prop="totalCost" label="已扣费(元)" width="100" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-popconfirm title="确定强制踢下线?" @confirm="kickSession(row.id)">
                <template #reference>
                  <el-button size="small" type="danger" link>踢下线</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
  </el-row>
</template>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
