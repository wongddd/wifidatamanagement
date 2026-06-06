# ============================================================
# MikroTik RouterOS 配置脚本 — 酒店 WiFi Hotspot 快速部署
# 使用方法：WinBox → New Terminal → 粘贴全部内容 → 回车
# 然后修改以下变量中的实际值
# ============================================================

# ========== 配置变量（请根据实际情况修改） ==========
:local serverIp "185.239.71.210"
:local serverPort "8080"
:local hotelName "默认酒店"
:local wanInterface "ether1"
:local lanBridge "bridge1"
:local hotspotInterface "bridge1"
:local addressPool "10.10.0.0/24"
:local dnsServer "8.8.8.8,114.114.114.114"

# ========== 1. 启用 REST API ==========
/ip service enable www-ssl
/ip service set www-ssl port=443 disabled=no
/ip service set api disabled=yes

# ========== 2. 创建 IP 地址池 ==========
/ip pool add name=hotspot-pool ranges=$addressPool

# ========== 3. 创建 Hotspot Server ==========
/ip hotspot profile add name=hsprof1 \
    hotspot-address=10.10.0.1 \
    dns-name=$hotelName \
    html-directory=hotspot

/ip hotspot add name=hotspot1 \
    interface=$hotspotInterface \
    address-pool=hotspot-pool \
    profile=hsprof1 \
    idle-timeout=30m \
    keepalive-timeout=5m

# ========== 4. 配置 Hotspot Server 地址 ==========
/ip hotspot user profile add name="default" \
    rate-limit="5M/10M" shared-users=2

# ========== 5. 创建 Walled Garden（白名单） ==========
# 允许未认证用户访问 Portal 服务器
/ip hotspot walled-garden ip add \
    dst-host=$serverIp \
    action=allow comment="允许Portal服务器"

# 允许微信/支付宝支付域名
/ip hotspot walled-garden ip add dst-host=*.weixin.qq.com action=allow
/ip hotspot walled-garden ip add dst-host=*.alipay.com action=allow
/ip hotspot walled-garden ip add dst-host=*.alicdn.com action=allow

# ========== 6. 设置默认 DNS ==========
/ip dns set servers=$dnsServer allow-remote-requests=yes

# ========== 7. NAT 伪装 ==========
/ip firewall nat add \
    chain=srcnat \
    src-address=$addressPool \
    out-interface=$wanInterface \
    action=masquerade \
    comment="hotspot-nat"

# ========== 8. 强制 Portal 重定向 ==========
# 将未认证用户重定向到公网 Portal
/ip hotspot profile set hsprof1 \
    login-by=http-pap,cookie,http-chap

# ========== 9. 创建初始管理员 ==========
/user add name=hotelapi password=ChangeMe123! group=full \
    comment="酒店管理系统API用户"

# ========== 10. 带宽限速默认规则 ==========
/queue simple add name="default-limit" \
    target=$addressPool \
    max-limit=5M/10M \
    comment="默认每用户5M上行/10M下行"

# ========== 11. SNMP 开启（可选，用于监控） ==========
/snmp set enabled=yes contact="酒店IT部门"
/snmp community add name=public read-access=yes

# ========== 12. 日志记录 ==========
/system logging add topics=hotspot,info,account action=memory

# ======== 完成 ========
:log info "Hotspot配置完成！"
:log info "请修改以下配置："
:log info "  1. /user set hotelapi password=你的密码"
:log info "  2. 将路由器添加到管理系统(设备管理→添加设备)"
:log info "  3. Portal地址: http://$serverIp/portal/"
