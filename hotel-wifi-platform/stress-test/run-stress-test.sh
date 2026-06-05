# =============================================
# Apache JMeter 压力测试脚本
# 酒店 WiFi 管理计费系统 — 1000并发在线会话扣费性能测试
# =============================================
# 运行方式:
#   jmeter -n -t stress-test/hotel-wifi-stress.jmx -l results.jtl -e -o report/
# =============================================

# 测试目标
BASE_URL=localhost:8080
API_PREFIX=/api/v1

# 登录获取 Token
LOGIN_URL=${BASE_URL}${API_PREFIX}/auth/login
TOKEN=$(curl -s -X POST ${LOGIN_URL} \
  -H "Content-Type: application/json" \
  -d '{"tenantId":1,"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

echo "Token: ${TOKEN:0:20}..."

# =============================================
# 场景1: 并发查询在线用户 (100用户并发)
# =============================================
echo ""
echo "=== 场景1: 并发查询在线用户 (100并发) ==="
for i in $(seq 1 100); do
  curl -s -o /dev/null -w "%{http_code} " \
    -H "Authorization: Bearer ${TOKEN}" \
    ${BASE_URL}${API_PREFIX}/billing/sessions/active &
done
wait
echo ""
echo "场景1 完成"

# =============================================
# 场景2: 并发创建订单 (50用户并发)
# =============================================
echo ""
echo "=== 场景2: 并发创建订单 (50并发) ==="
for i in $(seq 1 50); do
  MEMBER_ID=$((RANDOM % 100 + 1))
  curl -s -o /dev/null -w "%{http_code} " \
    -X POST ${BASE_URL}${API_PREFIX}/billing/orders \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"hotelId\":1,\"memberId\":${MEMBER_ID},\"packageId\":1,\"payType\":\"BALANCE\"}" &
done
wait
echo ""
echo "场景2 完成"

# =============================================
# 场景3: 模拟1000活跃会话的扣费轮询
# =============================================
echo ""
echo "=== 场景3: 模拟扣费引擎压测 ==="
echo "通过 Spring Scheduler 每30秒轮询1000在线用户"
echo "预期: 单次轮询 < 5秒, Redis Lua扣费 < 1ms/次"
echo "详见 JMX 配置文件"

# =============================================
# 基准性能指标
# =============================================
echo ""
echo "============================================"
echo "  基准性能指标 (目标)"
echo "============================================"
echo "  并发查询在线用户(100):  < 1s"
echo "  并发创建订单(50):       < 2s"
echo "  扣费引擎轮询(1000会话):  < 5s"
echo "  Redis Lua原子扣费:      < 1ms/次"
echo "  MySQL写入QPS:           > 500"
echo "  内存占用:               < 512MB"
echo "============================================"
