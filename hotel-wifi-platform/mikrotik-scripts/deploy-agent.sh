#!/bin/bash
# ============================================================
# 设备连接器 Agent 部署脚本
# 在酒店内网的 Linux 盒子/Raspberry Pi 上运行
# ============================================================

set -e

# ===== 配置变量 =====
SERVER_URL="ws://185.239.71.210:8080/ws/device"
DEVICE_ID="1"
DEVICE_SECRET="change_me"
MIKROTIK_HOST="192.168.1.1"
MIKROTIK_PORT="8728"
MIKROTIK_USER="hotelapi"
MIKROTIK_PASSWORD="ChangeMe123!"
LOCAL_NETWORK="192.168.1.0/24"
INSTALL_DIR="/opt/hotel-connector"

echo "============================================"
echo "  酒店WiFi设备连接器 Agent 部署脚本"
echo "============================================"

# 1. 创建目录
mkdir -p $INSTALL_DIR
cd $INSTALL_DIR

# 2. 下载最新版 connector JAR（从服务器获取或本地复制）
if [ ! -f "device-connector.jar" ]; then
    echo "[1/4] 请将 device-connector.jar 手动复制到 ${INSTALL_DIR}/"
    echo "      或运行: scp device-connector.jar root@内网IP:${INSTALL_DIR}/"
fi

# 3. 创建启动脚本
echo "[2/4] 创建启动脚本..."
cat > $INSTALL_DIR/start.sh << 'STARTEOF'
#!/bin/bash
cd /opt/hotel-connector
java -Xmx128m -Xms64m \
  -Dserver="${CONNECTOR_SERVER:-ws://185.239.71.210:8080/ws/device}" \
  -DdeviceId="${CONNECTOR_DEVICE_ID:-1}" \
  -Dsecret="${CONNECTOR_SECRET:-change_me}" \
  -DlocalNetwork="${CONNECTOR_LOCAL_NETWORK:-192.168.1.0/24}" \
  -DmikrotikHost="${CONNECTOR_MIKROTIK_HOST:-192.168.1.1}" \
  -DmikrotikPort="${CONNECTOR_MIKROTIK_PORT:-8728}" \
  -DmikrotikUser="${CONNECTOR_MIKROTIK_USER:-hotelapi}" \
  -DmikrotikPassword="${CONNECTOR_MIKROTIK_PASSWORD:-ChangeMe123!}" \
  -jar /opt/hotel-connector/device-connector.jar
STARTEOF
chmod +x $INSTALL_DIR/start.sh

# 4. 创建环境变量文件
echo "[3/4] 创建环境变量配置..."
cat > $INSTALL_DIR/connector.env << ENVEOF
CONNECTOR_SERVER=$SERVER_URL
CONNECTOR_DEVICE_ID=$DEVICE_ID
CONNECTOR_SECRET=$DEVICE_SECRET
CONNECTOR_LOCAL_NETWORK=$LOCAL_NETWORK
CONNECTOR_MIKROTIK_HOST=$MIKROTIK_HOST
CONNECTOR_MIKROTIK_PORT=$MIKROTIK_PORT
CONNECTOR_MIKROTIK_USER=$MIKROTIK_USER
CONNECTOR_MIKROTIK_PASSWORD=$MIKROTIK_PASSWORD
ENVEOF

# 5. 创建 systemd 服务
echo "[4/4] 创建 systemd 服务..."
cat > /etc/systemd/system/hotel-connector.service << SERVICEEOF
[Unit]
Description=Hotel WiFi Device Connector
After=network.target

[Service]
Type=simple
Restart=always
RestartSec=10
User=root
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$INSTALL_DIR/connector.env
ExecStart=/bin/bash $INSTALL_DIR/start.sh
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICEEOF

systemctl daemon-reload
systemctl enable hotel-connector
systemctl start hotel-connector

sleep 3
systemctl status hotel-connector --no-pager || true

echo ""
echo "============================================"
echo "  部署完成！"
echo "============================================"
echo "  查看日志: journalctl -u hotel-connector -f"
echo "  重启:     systemctl restart hotel-connector"
echo "  停止:     systemctl stop hotel-connector"
echo "============================================"
