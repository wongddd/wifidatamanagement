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
  hotelName: '', hotelCode: '', address: '', phone: '',
  roomCount: 100, maxOnline: 500, status: 1,
})

const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/hotels', { pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.records
    page.total = res.total
  } finally { loading.value = false }
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, hotelName: '', hotelCode: '', address: '', phone: '', roomCount: 100, maxOnline: 500, status: 1 })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value && form.id) {
    await put(`/hotels/${form.id}`, form)
    ElMessage.success('酒店已更新')
  } else {
    await post('/hotels', form)
    ElMessage.success('酒店已创建')
  }
  dialogVisible.value = false
  loadData()
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  await put(`/hotels/${row.id}/status?status=${newStatus}`)
  ElMessage.success(newStatus === 1 ? '酒店已启用' : '酒店已停用')
  loadData()
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header"><span>酒店管理</span><el-button type="primary" @click="handleCreate">新增酒店</el-button></div>
    </template>
    <div class="table-wrapper">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="hotelName" label="酒店名称" min-width="160" />
        <el-table-column prop="hotelCode" label="编码" width="100" />
        <el-table-column prop="roomCount" label="房间数" width="80" />
        <el-table-column prop="maxOnline" label="最大在线" width="90" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="address" label="地址" min-width="200" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" link @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
      :total="page.total" layout="total,prev,pager,next" @change="loadData"
      :small="true"
      style="margin-top:16px;justify-content:flex-end" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑酒店' : '新增酒店'" width="90%" style="max-width:500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="酒店名称"><el-input v-model="form.hotelName" /></el-form-item>
        <el-form-item label="酒店编码"><el-input v-model="form.hotelCode" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="房间数"><el-input-number v-model="form.roomCount" :min="1" /></el-form-item>
        <el-form-item label="最大在线"><el-input-number v-model="form.maxOnline" :min="10" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" type="textarea" /></el-form-item>
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
