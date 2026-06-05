<script setup lang="ts">
import { provide, ref } from 'vue'

// 从 URL 参数中提取 MikroTik Hotspot 传递的信息
const params = new URLSearchParams(window.location.search)
const macAddress = ref(params.get('mac') || '')
const ipAddress = ref(params.get('ip') || '')
const linkLogin = ref(params.get('link-login') || 'only')
const linkOrig = ref(params.get('link-orig') || '')

provide('macAddress', macAddress)
provide('ipAddress', ipAddress)
</script>

<template>
  <router-view />
</template>

<style>
html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Microsoft YaHei', sans-serif;
  background: #f5f7fa;
}
</style>
