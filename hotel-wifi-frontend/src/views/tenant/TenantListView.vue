<script setup lang="ts">
import { ref } from 'vue'
import { get, post, put } from '@/api'

const tableData = ref([])
const loading = ref(false)
const page = ref({ pageNum: 1, pageSize: 10, total: 0 })
const dialogVisible = ref(false)
const form = ref({ tenantName: '', contactPerson: '', contactPhone: '', contactEmail: '', address: '' })

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/tenants', page.value)
    tableData.value = res.records
    page.value.total = res.total
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  form.value = { tenantName: '', contactPerson: '', contactPhone: '', contactEmail: '', address: '' }
  dialogVisible.value = true
}

async function handleSave() {
  await post('/tenants', form.value)
  dialogVisible.value = false
  loadData()
}

loadData()
</script>

<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>租户管理</span>
          <el-button type="primary" @click="handleCreate">新增租户</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tenantName" label="租户名称" />
        <el-table-column prop="contactPerson" label="联系人" />
        <el-table-column prop="contactPhone" label="联系电话" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default>
            <el-button size="small" type="primary" link>编辑</el-button>
            <el-button size="small" type="danger" link>停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        :total="page.total"
        layout="total, prev, pager, next"
        @change="loadData"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增租户" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="租户名称"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.contactEmail" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
