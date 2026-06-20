<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { get } from '@/api'
import { useWebSocket } from '@/composables/useWebSocket'
import * as echarts from 'echarts'

// WebSocket 实时数据
const { data: wsData } = useWebSocket('/ws/dashboard')

const stats = ref([
  { title: '实时在线', value: 0, icon: 'User', color: '#409EFF', key: 'onlineCount' },
  { title: '今日收入', value: '¥0.00', icon: 'Money', color: '#67C23A', key: 'todayRevenue' },
  { title: '活跃会员', value: 0, icon: 'Star', color: '#E6A23C', key: 'memberCount' },
  { title: '在线设备', value: 0, icon: 'Monitor', color: '#F56C6C', key: 'deviceCount' },
])

// 图表实例
let revenueChart: echarts.ECharts | null = null

watch(wsData, (val) => {
  if (val?.type === 'dashboard') {
    const onlineItem = stats.value.find(s => s.key === 'onlineCount')
    if (onlineItem) onlineItem.value = val.onlineCount
    const revenueItem = stats.value.find(s => s.key === 'todayRevenue')
    if (revenueItem) revenueItem.value = `¥${Number(val.todayRevenue).toFixed(2)}`
  }
})

async function loadOverview() {
  try {
    const res: any = await get('/dashboard/overview')
    const onlineItem = stats.value.find(s => s.key === 'onlineCount')
    if (onlineItem) onlineItem.value = res.onlineCount || 0
    const revenueItem = stats.value.find(s => s.key === 'todayRevenue')
    if (revenueItem) revenueItem.value = `¥${Number(res.todayRevenue || 0).toFixed(2)}`
    const memberItem = stats.value.find(s => s.key === 'memberCount')
    if (memberItem) memberItem.value = res.newMembers || 0
  } catch {}
}

async function loadCharts() {
  try {
    // 收入趋势
    const revenueData: any = await get('/reports/revenue-trend')
    if (revenueChart && Array.isArray(revenueData)) {
      revenueChart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 50, right: 20, top: 20, bottom: 30 },
        xAxis: { type: 'category', data: revenueData.map((d: any) => d.date) },
        yAxis: { type: 'value', name: '元' },
        series: [{
          data: revenueData.map((d: any) => d.amount),
          type: 'line',
          smooth: true,
          areaStyle: { color: 'rgba(64,158,255,0.2)' },
          lineStyle: { color: '#409EFF' },
          itemStyle: { color: '#409EFF' },
        }]
      })
    }
  } catch {}
}

function handleResize() {
  revenueChart?.resize()
}

onMounted(() => {
  loadOverview()
  const revDom = document.getElementById('revenue-chart')
  if (revDom) {
    revenueChart = echarts.init(revDom)
    loadCharts()
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  revenueChart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="12" :lg="6" v-for="item in stats" :key="item.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-title">{{ item.title }}</p>
              <p class="stat-value" :style="{ color: item.color }">{{ item.value }}</p>
            </div>
            <el-icon :size="48" :color="item.color"><component :is="item.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">近7天收入趋势</span></template>
          <div id="revenue-chart" style="height:320px"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header><span style="font-weight:600">今日流量分布</span></template>
          <div style="height:320px;display:flex;align-items:center;justify-content:center;color:#909399">
            <div style="text-align:center">
              <el-icon :size="64"><DataAnalysis /></el-icon>
              <p style="margin-top:12px">流量分布图表将在后续版本对接</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时连接状态 -->
    <div style="margin-top:8px;text-align:right;font-size:12px;color:#909399">
      <span v-if="wsData" style="color:#67C23A">● 实时连接中 (10秒刷新)</span>
      <span v-else>○ 等待连接</span>
    </div>
  </div>
</template>

<style scoped>
.stat-card .stat-content { display:flex; align-items:center; justify-content:space-between; }
.stat-card .stat-title { color:#909399; font-size:14px; margin:0 0 8px 0; }
.stat-card .stat-value { font-size:24px; font-weight:bold; margin:0; }

@media (max-width: 480px) {
  .stat-card .stat-value { font-size: 20px; }
  .stat-card .stat-title { font-size: 12px; margin: 0 0 4px 0; }
}
</style>
