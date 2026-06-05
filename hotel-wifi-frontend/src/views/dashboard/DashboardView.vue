<script setup lang="ts">
import { ref, onMounted } from 'vue'

const stats = ref([
  { title: '今日在线', value: 0, icon: 'User', color: '#409EFF' },
  { title: '今日收入', value: '¥0.00', icon: 'Money', color: '#67C23A' },
  { title: '活跃会员', value: 0, icon: 'Star', color: '#E6A23C' },
  { title: '在线设备', value: 0, icon: 'Monitor', color: '#F56C6C' },
])

onMounted(() => {
  // TODO: 从后端加载 Dashboard 数据
})
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="item in stats" :key="item.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-title">{{ item.title }}</p>
              <p class="stat-value">{{ item.value }}</p>
            </div>
            <el-icon :size="48" :color="item.color">
              <component :is="item.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>实时在线趋势</template>
          <div style="height: 300px; display: flex; align-items: center; justify-content: center; color: #909399;">
            图表区域 — 对接 ECharts
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>今日收入分布</template>
          <div style="height: 300px; display: flex; align-items: center; justify-content: center; color: #909399;">
            图表区域 — 对接 ECharts
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card {
  .stat-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .stat-title {
    color: #909399;
    font-size: 14px;
    margin: 0 0 8px 0;
  }
  .stat-value {
    color: #303133;
    font-size: 24px;
    font-weight: bold;
    margin: 0;
  }
}
</style>
