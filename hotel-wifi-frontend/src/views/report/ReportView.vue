<script setup lang="ts">
</script>

<template>
  <el-row :gutter="20">
    <el-col :span="12">
      <el-card shadow="hover">
        <template #header>流量统计</template>
        <div style="height: 350px; display: flex; align-items: center; justify-content: center; color: #909399;">
          流量图表区域 — 选择时间范围查询
        </div>
      </el-card>
    </el-col>
    <el-col :span="12">
      <el-card shadow="hover">
        <template #header>收入统计</template>
        <div style="height: 350px; display: flex; align-items: center; justify-content: center; color: #909399;">
          收入图表区域 — 按天/周/月/年聚合
        </div>
      </el-card>
    </el-col>
    <el-col :span="24" style="margin-top: 20px">
      <el-card shadow="hover">
        <template #header>在线趋势</template>
        <div style="height: 350px; display: flex; align-items: center; justify-content: center; color: #909399;">
          在线用户趋势图 — 小时/天粒度
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>
