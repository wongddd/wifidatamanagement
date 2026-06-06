import { ref, onUnmounted } from 'vue'

export function useWebSocket(path: string) {
  const data = ref<any>(null)
  const connected = ref(false)
  let ws: WebSocket | null = null
  let reconnectTimer: number | null = null

  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return

    // 安全: Token 不在 URL 参数中传递，改为连接建立后发送第一条消息
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${location.host}${path}`

    ws = new WebSocket(url)
    ws.onopen = () => {
      connected.value = true
      // 连接成功后通过消息发送 Token（不暴露在 URL/日志中）
      ws?.send(JSON.stringify({ type: 'auth', token }))
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
