import { ref, onUnmounted } from 'vue'

export function useWebSocket(path: string) {
  const data = ref<any>(null)
  const connected = ref(false)
  let ws: WebSocket | null = null
  let reconnectTimer: number | null = null

  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${location.host}${path}?token=${token}`

    ws = new WebSocket(url)
    ws.onopen = () => {
      connected.value = true
      console.log('[WS] 已连接')
    }
    ws.onmessage = (event) => {
      try {
        data.value = JSON.parse(event.data)
      } catch {
        data.value = event.data
      }
    }
    ws.onclose = () => {
      connected.value = false
      reconnectTimer = window.setTimeout(connect, 5000)
    }
    ws.onerror = () => ws?.close()
  }

  function disconnect() {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    ws?.close()
  }

  connect()
  onUnmounted(disconnect)

  return { data, connected }
}
