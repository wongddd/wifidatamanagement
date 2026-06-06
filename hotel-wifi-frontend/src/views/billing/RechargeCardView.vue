<script setup lang="ts">
import { ref, reactive } from 'vue'
import { get, post, put } from '@/api'
import { ElMessage } from 'element-plus'

const tableData = ref([])
const loading = ref(false)
const generateVisible = ref(false)
const redeemVisible = ref(false)
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const generateForm = reactive({
  count: 100, amount: 10, packageId: null as number | null, expireAt: null as string | null,
})

const redeemForm = reactive({ cardNo: '', password: '', memberId: 0 })

const generatedCards = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res: any = await get('/recharge-cards', { pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.records
    page.total = res.total
  } finally { loading.value = false }
}

async function handleGenerate() {
  if (generateForm.count < 1 || generateForm.count > 10000) {
    ElMessage.warning('生成数量需在 1-10000 之间')
    return
  }
  const res: any = await post('/recharge-cards/batch', generateForm)
  generatedCards.value = res || []
  ElMessage.success(`成功生成 ${generatedCards.value.length} 张充值卡`)
  generateVisible.value = false
  loadData()
}

async function handleRedeem() {
  if (!redeemForm.cardNo || !redeemForm.password || !redeemForm.memberId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  const res: any = await post('/recharge-cards/redeem', redeemForm)
  ElMessage.success(`核销成功！余额: ¥${Number(res.balance).toFixed(2)}`)
  redeemVisible.value = false
  loadData()
}

async function handleRevoke(id: number) {
  await put(`/recharge-cards/${id}/revoke`)
  ElMessage.success('充值卡已作废')
  loadData()
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

loadData()
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>充值卡管理</span>
        <div style="display:flex;gap:8px">
          <el-button type="success" @click="redeemVisible = true">核销充值卡</el-button>
          <el-button type="primary" @click="generateVisible = true">批量生成</el-button>
        </div>
      </div>
    </template>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="batchNo" label="批次号" width="160" />
      <el-table-column prop="cardNo" label="卡号" width="180" />
      <el-table-column prop="amount" label="面值(元)" width="100" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.packageId ? '套餐卡' : '余额卡' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'UNUSED' ? 'success' : row.status === 'USED' ? 'info' : 'danger'" size="small">
            {{ row.status === 'UNUSED' ? '未使用' : row.status === 'USED' ? '已使用' : row.status === 'EXPIRED' ? '已过期' : '已作废' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="usedBy" label="使用者ID" width="100" />
      <el-table-column prop="usedAt" label="使用时间" width="170" />
      <el-table-column prop="expireAt" label="过期时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-popconfirm v-if="row.status === 'UNUSED'" title="确定作废此卡?" @confirm="handleRevoke(row.id)">
            <template #reference>
              <el-button size="small" type="danger" link>作废</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
      :total="page.total" layout="total,prev,pager,next" @change="loadData"
      style="margin-top:16px;justify-content:flex-end" />

    <!-- 批量生成弹窗 -->
    <el-dialog v-model="generateVisible" title="批量生成充值卡" width="480px">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="生成数量">
          <el-input-number v-model="generateForm.count" :min="1" :max="10000" style="width:100%" />
        </el-form-item>
        <el-form-item label="面值(元)">
          <el-input-number v-model="generateForm.amount" :min="0.01" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="关联套餐ID">
          <el-input-number v-model="generateForm.packageId" :min="0" style="width:100%" />
          <span style="color:#909399;font-size:12px">0或不填=余额卡，填套餐ID=套餐卡</span>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="generateForm.expireAt" type="datetime" placeholder="不填则永不过期" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 核销弹窗 -->
    <el-dialog v-model="redeemVisible" title="核销充值卡" width="400px">
      <el-form :model="redeemForm" label-width="80px">
        <el-form-item label="卡号"><el-input v-model="redeemForm.cardNo" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="redeemForm.password" /></el-form-item>
        <el-form-item label="会员ID"><el-input-number v-model="redeemForm.memberId" :min="1" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="redeemVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRedeem">核销</el-button>
      </template>
    </el-dialog>

    <!-- 生成结果展示 -->
    <el-dialog v-if="generatedCards.length" :model-value="generatedCards.length > 0" @update:model-value="generatedCards = []" title="生成结果" width="600px">
      <el-table :data="generatedCards" border stripe max-height="400">
        <el-table-column prop="cardNo" label="卡号" width="180" />
        <el-table-column prop="cardPassword" label="密码" width="120" />
        <el-table-column prop="amount" label="面值" width="80" />
      </el-table>
      <div style="margin-top:12px;text-align:right">
        <el-button size="small" @click="copyToClipboard(JSON.stringify(generatedCards))">全部复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="generatedCards = []">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.card-header { display:flex; justify-content:space-between; align-items:center; }
</style>
