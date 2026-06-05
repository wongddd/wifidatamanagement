<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { get } from '@/api'
import * as echarts from 'echarts'

const days = ref(7)
let chartInstance: echarts.ECharts | null = null

async function loadData() {
  try {
    const data: any = await get('/reports/revenue-trend')
    if (chartInstance && Array.isArray(data)) {
      chartInstance.setOption({
        title: { text: '收入趋势', left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 60, right: 20, top: 50, bottom: 30 },
        xAxis: { type: 'category', data: data.map((d: any) => d.date) },
        yAxis: { type: 'value', name: '元' },
        series: [{
          data: data.map((d: any) => d.amount),
          type: 'bar',
          barWidth: 32,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#409EFF' },
              { offset: 1, color: '#79bbff' },
            ]),
            borderRadius: [4, 4, 0, 0],
          },
        }]
      })
    }

    // 流量统计面板
    const trafficData: any = await get('/reports/traffic', { days: days.value })
    trafficStats.value = trafficData

    const revenueData: any = await get('/reports/revenue', { days: days.value })
    revenueStats.value = revenueData
  } catch {}
}

const trafficStats = ref<any>({ totalTraffic: 0, days: 7 })
const revenueStats = ref<any>({ totalRevenue: 0, days: 7 })

function formatBytes(bytes: number) {
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(2) + ' GB'
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(2) + ' MB'
  return bytes + ' B'
}

onMounted(() => {
  const dom = document.getElementById('revenue-bar-chart')
  if (dom) {
    chartInstance = echarts.init(dom)
    loadData()
  }
})

onUnmounted(() => chartInstance?.dispose())
</script>

<template>
  <div>
    <!-- 汇总卡片 -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="text-align:center;padding:16px">
            <p style="color:#909399;font-size:14px">近{{ days }}天总流量</p>
            <p style="font-size:28px;font-weight:bold;color:#409EFF;margin:8px 0">
              {{ formatBytes(trafficStats.totalTraffic) }}
            </p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="text-align:center;padding:16px">
            <p style="color:#909399;font-size:14px">近{{ days }}天总收入</p>
            <p style="font-size:28px;font-weight:bold;color:#67C23A;margin:8px 0">
              ¥{{ Number(revenueStats.totalRevenue || 0).toFixed(2) }}
            </p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div style="text-align:center;padding:16px">
            <p style="color:#909399;font-size:14px">时间范围</p>
            <el-radio-group v-model="days" @change="loadData" style="margin:8px 0">
              <el-radio-button :value="7">7天</el-radio-button>
              <el-radio-button :value="30">30天</el-radio-button>
              <el-radio-button :value="90">90天</el-radio-button>
            </el-radio-group>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 收入柱状图 -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <div id="revenue-bar-chart" style="height:400px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
