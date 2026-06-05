<script setup lang="ts">
import { ref, reactive } from 'vue'
import { get, post, put } from '@/api'
import { ElMessage } from 'element-plus'

const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const rechargeVisible = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null as number | null,
  username: '', password: '', realName: '', phone: '',
  hotelId: null as number | null, status: 1,
})

const rechargeForm = reactive({ id: 0, username: '', amount: 0 })
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/members', { pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.records
    page.total = res.total
  } finally { loading.value = false }
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, username: '', password: '', realName: '', phone: '', hotelId: null, status: 1 })
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  Object.assign(form, { ...row, password: '' })
  dialogVisible.value = true
}

async function handleSave() {
  if (isEdit.value && form.id) {
    await put(`/members/${form.id}`, form)
    ElMessage.success('会员已更新')
  } else {
    await post('/members', form)
    ElMessage.success('会员已创建')
  }
  dialogVisible.value = false
  loadData()
}

function handleRecharge(row: any) {
  rechargeForm.id = row.id
  rechargeForm.username = row.username
  rechargeForm.amount = 0
  rechargeVisible.value = true
}

async function doRecharge() {
  if (rechargeForm.amount <= 0) { ElMessage.warning('请输入充值金额'); return }
  await post(`/members/${rechargeForm.id}/recharge`, { amount: rechargeForm.amount })
  ElMessage.success(`已为 ${rechargeForm.username} 充值 ¥${rechargeForm.amount}`)
  rechargeVisible.value = false
  loadData()
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  await put(`/members/${row.id}/status?status=${newStatus}`)
  ElMessage.success(newStatus === 1 ? '会员已启用' : '会员已停用')
  loadData()
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header"><span>会员管理</span><el-button type="primary" @click="handleCreate">新增会员</el-button></div>
    </template>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="余额" width="120">
        <template #default="{ row }">¥{{ row.balance || 0 }}</template>
      </el-table-column>
      <el-table-column label="到期时间" width="170">
        <template #default="{ row }">{{ row.expireAt || '永久' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === -1 ? 'danger' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : row.status === -1 ? '黑名单' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="success" link @click="handleRecharge(row)">充值</el-button>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑会员' : '新增会员'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item v-if="!isEdit" label="密码"><el-input v-model="form.password" type="password" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="会员充值" width="380px">
      <el-form :model="rechargeForm" label-width="80px">
        <el-form-item label="会员">{{ rechargeForm.username }}</el-form-item>
        <el-form-item label="充值金额">
          <el-input-number v-model="rechargeForm.amount" :min="0.01" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="doRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.card-header { display:flex; justify-content:space-between; align-items:center; }
</style>
