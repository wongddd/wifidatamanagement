# 酒店 WiFi 数据管理计费系统

## 项目简介

面向连锁酒店的互联网管理计费平台，提供数据流量分发和计费功能。支持包天/包月/包年会员计费以及按 MB/GB 流量计费，支持 MikroTik 和标准 RADIUS 协议设备接入。

### 核心功能

- **多租户管理**：一个集团管理多家酒店，tenant_id 行级数据隔离
- **会员管理**：住客账号、余额、积分、状态管理、到期提醒
- **套餐体系**：包天 / 包月 / 包年 / 按 MB/GB 流量计费 / 混合计费
- **实时计费引擎**：预扣费策略、Redis Lua 脚本原子扣费、费用封顶
- **充值卡系统**：批量生成 / 核销 / 作废、余额卡 + 套餐卡双模式
- **流量采集**：MikroTik REST API 30秒轮询 + RADIUS Accounting 计费采集
- **设备管理**：多路由器集中管控、配置同步、连接测试
- **认证方式**：账号密码 / 微信扫码 / 短信验证码 / 充值卡（SPI 可扩展）
- **实时 Dashboard**：WebSocket 10秒推送在线人数和收入
- **报表统计**：ECharts 收入趋势图、流量统计、在线趋势
- **到期提醒**：每天定时检查即将到期会员，自动发送短信提醒
- **设备监控**：SNMP v2c 采集 CPU/内存/接口流量
- **多语言**：中英文国际化支持

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2+ |
| ORM | MyBatis-Plus | 3.5+ |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x (Lettuce) |
| 认证 | Spring Security + JWT | - |
| 定时任务 | Spring Scheduler + ShedLock | - |
| API 文档 | Knife4j / SpringDoc | - |
| RADIUS 协议 | TinyRadius + Netty UDP | 1.3.2 |
| SNMP 监控 | SNMP4J | 3.8.1 |
| 前端框架 | Vue 3 + TypeScript + Vite | - |
| UI 框架 | Element Plus | - |
| 图表 | ECharts 5 | - |
| 状态管理 | Pinia | - |
| 国际化 | vue-i18n | - |
| 构建工具 | Maven（后端）/ pnpm（前端） | - |
| 容器化 | Docker + Docker Compose | - |
| 安装包 | jpackage (Windows MSI / Linux RPM) | JDK 17+ |
| 设备对接 | MikroTik REST API / RADIUS / SNMP | RouterOS 6.45+ |

## 项目结构

```
D:\alenwifidata\
├── hotel-wifi-platform/                    # 后端 Spring Boot 多模块项目
│   ├── pom.xml                             # 父 POM（依赖管理）
│   ├── hotel-wifi-common/                  # 公共模块
│   │   └── src/.../common/
│   │       ├── constant/                   # 系统常量
│   │       ├── dto/                        # ApiResult、PageReq
│   │       ├── enums/                      # BillingType、AuthType 等
│   │       ├── exception/                  # 全局异常处理
│   │       └── util/                       # JWT 工具类
│   ├── hotel-wifi-core/                    # 核心业务模块
│   │   └── src/.../core/
│   │       ├── tenant/                     # 多租户管理
│   │       ├── hotel/                      # 酒店管理
│   │       ├── member/                     # 会员管理
│   │       ├── package/                    # 套餐管理
│   │       ├── billing/                    # 计费引擎（引擎核心、预扣费策略、定时扣费）
│   │       ├── traffic/                    # 流量采集（轮询 MikroTik、流量快照）
│   │       ├── device/                     # 设备管理
│   │       │   ├── mikrotik/               # MikroTik REST API 客户端
│   │       │   ├── radius/                 # RADIUS 适配器（1812/1813 UDP Server）
│   │       │   └── snmp/                   # SNMP 监控采集器
│   │       ├── auth/                       # 认证模块
│   │       │   ├── spi/                    # AuthProvider SPI 接口
│   │       │   └── provider/               # 密码/短信/微信 Provider 实现
│   │       ├── recharge/                   # 充值卡系统（批量生成/核销/作废）
│   │       ├── notification/               # 通知提醒（短信/到期检查）
│   │       ├── report/                     # 报表统计（收入趋势/流量汇总）
│   │       ├── websocket/                  # WebSocket 实时推送
│   │       └── config/                     # 配置
│   │           └── performance/            # 高可用配置（Redis Cluster/连接池调优）
│   ├── hotel-wifi-api/                     # API 聚合 + 启动类
│   │   └── src/main/resources/
│   │       ├── application.yml             # 开发环境配置
│   │       ├── application-docker.yml      # Docker 环境配置
│   │       ├── application-production.yml  # 生产环境配置
│   │       ├── application-cluster.yml     # Redis Cluster 配置
│   │       └── db/migration/               # Flyway 数据库迁移脚本
│   │           ├── V1__init_tables.sql     # 初始化表结构（12张核心表）
│   │           └── V2__performance_optimize.sql  # 性能优化（分区/索引）
│   ├── jpackage/                           # 二进制安装包构建
│   │   ├── build-installer.bat             # Windows MSI 构建脚本
│   │   └── build-installer.sh              # Linux RPM 构建脚本
│   ├── stress-test/                        # 压力测试
│   │   ├── hotel-wifi-stress.jmx           # JMeter JMX 配置
│   │   └── run-stress-test.sh              # Shell 压测脚本
│   ├── Dockerfile                          # 多阶段构建
│   ├── docker-compose.yml                  # 一键部署
│   └── nginx.conf                          # Nginx 反向代理
│
├── hotel-wifi-frontend/                    # 前端 Vue 3 管理后台
│   └── src/
│       ├── api/                            # Axios 封装 + API 模块
│       │   └── modules/auth.ts             # 认证 API
│       ├── router/                         # 路由配置 + 权限守卫
│       ├── store/modules/user.ts           # Pinia 用户状态
│       ├── views/                          # 页面视图
│       │   ├── login/                      # 登录页
│       │   ├── dashboard/                  # 仪表盘（WebSocket + ECharts）
│       │   ├── tenant/                     # 租户管理（CRUD 对话框）
│       │   ├── hotel/                      # 酒店管理（CRUD 对话框）
│       │   ├── member/                     # 会员管理（CRUD + 充值弹窗）
│       │   ├── package/                    # 套餐管理（完整配置对话框）
│       │   ├── billing/                    # 计费管理（在线用户 + 踢下线）
│       │   │   └── RechargeCardView.vue    # 充值卡管理（批量生成/核销/作废）
│       │   ├── device/                     # 设备管理（测试连接/同步/删除）
│       │   └── report/                     # 统计报表（ECharts 柱状图 + 范围切换）
│       ├── components/layout/              # 布局组件（侧栏 + 顶栏）
│       ├── composables/                    # 组合式函数
│       │   └── useWebSocket.ts             # WebSocket 客户端（自动重连）
│       ├── locales/                        # 国际化
│       │   ├── zh-CN.json                  # 简体中文（40+ 键）
│       │   ├── en-US.json                  # English
│       │   └── index.ts                    # vue-i18n 配置
│       └── styles/                         # 全局样式
│
├── CLAUDE.md                               # Claude Code 工作规则
├── README.md                               # 本文件
└── .gitignore
```

## 快速启动

### 方式 1：Docker Compose（推荐）

```bash
cd hotel-wifi-platform
docker-compose up -d
```

访问：
- 管理后台: http://localhost
- API 文档: http://localhost:8080/doc.html
- WebSocket: ws://localhost:8080/ws/dashboard
- 默认账号: admin / admin123

### 方式 2：生产服务器部署

```bash
cd hotel-wifi-platform
docker compose -f docker-compose.prod.yml up -d
```

> **演示服务器**: http://185.239.71.210:8080 (admin / admin123)

### 方式 3：本地开发

**后端：**
```bash
cd hotel-wifi-platform
mvn clean package -DskipTests -pl hotel-wifi-api -am
java -jar hotel-wifi-api/target/hotel-wifi-api-1.0.0-SNAPSHOT.jar
```

**前端：**
```bash
cd hotel-wifi-frontend
pnpm install
pnpm dev
```

### 方式 3：生产环境安装包

**Windows (MSI)：**
```cmd
cd hotel-wifi-platform\jpackage
build-installer.bat
```

**Linux (RPM)：**
```bash
cd hotel-wifi-platform/jpackage
chmod +x build-installer.sh
./build-installer.sh
```

## 核心 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/auth/login` | POST | 管理后台登录 |
| `/api/v1/auth/me` | GET | 获取当前用户信息 |
| `/api/v1/tenants` | GET/POST | 租户管理 |
| `/api/v1/hotels` | GET/POST | 酒店管理 |
| `/api/v1/members` | GET/POST | 会员管理 |
| `/api/v1/members/{id}/recharge` | POST | 会员充值 |
| `/api/v1/packages` | GET/POST | 套餐管理 |
| `/api/v1/billing/orders` | POST | 创建订单（购买套餐） |
| `/api/v1/billing/sessions/active` | GET | 实时在线用户 |
| `/api/v1/billing/sessions/{id}/kick` | POST | 强制踢下线 |
| `/api/v1/recharge-cards` | GET | 充值卡列表 |
| `/api/v1/recharge-cards/batch` | POST | 批量生成充值卡 |
| `/api/v1/recharge-cards/redeem` | POST | 核销充值卡 |
| `/api/v1/recharge-cards/{id}/revoke` | PUT | 作废充值卡 |
| `/api/v1/devices` | GET/POST | 路由器管理 |
| `/api/v1/devices/{id}/test` | POST | 测试设备连接 |
| `/api/v1/devices/{id}/sync` | POST | 同步配置到路由器 |
| `/api/v1/reports/traffic` | GET | 流量统计 |
| `/api/v1/reports/revenue` | GET | 收入统计 |
| `/api/v1/reports/revenue-trend` | GET | 收入趋势（图表用） |
| `/api/v1/dashboard/overview` | GET | 首页概览数据 |
| `/ws/dashboard` | WebSocket | 实时数据推送（10秒间隔） |
| `/api/v1/portal/auth/{type}` | POST | Portal 认证（type=username/sms/wechat/card） |

**RADIUS 端口：**
| 端口 | 协议 | 说明 |
|------|------|------|
| 1812/UDP | RADIUS Authentication | 认证请求 (Access-Request) |
| 1813/UDP | RADIUS Accounting | 计费请求 (Accounting-Request Start/Stop/Interim) |

## 数据库核心表

| 表名 | 说明 |
|------|------|
| `sys_tenant` | 租户（集团） |
| `hotel` | 酒店 |
| `sys_user` | 系统用户（管理后台） |
| `member` | 会员（住客） |
| `billing_package` | 套餐（包天/包月/包年/流量包） |
| `billing_order` | 订单 |
| `online_session` | 上网会话 |
| `traffic_snapshot` | 流量采样（按月分区） |
| `billing_deduction` | 扣费记录 |
| `router_device` | 路由器设备 |
| `recharge_card` | 充值卡 |
| `device_sync_log` | 设备配置同步日志 |
| `snmp_snapshot` | SNMP 监控快照 |
| `shedlock` | 分布式任务锁 |

## 内网设备管理方案

### 问题
公网服务器无法直接访问酒店内网的路由器和 AP（NAT/防火墙阻挡）。

### 方案架构

```
公网服务器 (185.239.71.210)
  │
  ├─ DeviceRelayHandler  ←WebSocket→  内网 Agent (Linux盒子/RPi)
  │  /ws/device                              │
  │  /api/v1/devices/relay/*                  ├─ MikroTik REST API 代理
  │                                           ├─ 局域网 ARP 扫描
  │                                           └─ 心跳上报 + 命令执行
  └─ 管理后台设备管理页面
```

### 方案 A：Agent 中继模式（内网/无公网IP）⭐推荐

**Step 1 — 服务器端已就绪**

服务器已自动监听 `/ws/device` WebSocket 端点，无需额外配置。

**Step 2 — 内网部署 Agent**

在内网任意 Linux 设备（树莓派/工控机/旧PC）上：

```bash
# 安装 Java 17+
yum install -y java-17-openjdk-headless

# 创建设备连接器
cd /opt/hotel-wifi/hotel-wifi-platform/device-connector
mvn clean package -DskipTests

# 启动 Agent
java -Xmx128m -Xms64m \
  -Dserver=ws://185.239.71.210:8080/ws/device \
  -DdeviceId=1 \
  -DmikrotikHost=192.168.1.1 \
  -DmikrotikPort=8728 \
  -DmikrotikUser=admin \
  -DmikrotikPassword=你的密码 \
  -jar target/device-connector-1.0.0.jar
```

**Step 3 — 管理后台添加设备**

1. 登录 http://185.239.71.210 → 设备管理 → 添加设备
2. 填写设备名称、选择 **Agent 模式**
3. Agent 连接成功后状态变为"在线"

**Step 4 — 一键部署 (systemd 服务)**

```bash
cd /opt/hotel-wifi/hotel-wifi-platform
bash mikrotik-scripts/deploy-agent.sh
# 修改 connector.env 中的实际参数后重启
systemctl restart hotel-connector
```

### 方案 B：IP 直连模式（有公网IP/端口映射）

直接在管理后台 → 设备管理 → 添加设备，填写路由器公网 IP 和端口。服务器直接通过 MikroTik REST API 通信。

### Agent 支持的命令

| 命令 | 说明 |
|------|------|
| `mikrotik_get` | 代理 REST GET 请求 |
| `mikrotik_post` | 代理 REST POST 请求 |
| `mikrotik_hotspot_active` | 获取在线 Hotspot 用户 |
| `scan_network` | 扫描局域网设备（ARP + DHCP） |
| `disconnect_user` | 踢断指定用户 |
| `ping` | 连通性测试 |

### API 端点

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/v1/devices/relay/stats` | 在线 Agent 数量 |
| GET | `/api/v1/devices/relay/{deviceId}/status` | 检查 Agent 在线状态 |
| POST | `/api/v1/devices/relay/{deviceId}/command` | 下发命令 |
| POST | `/api/v1/devices/relay/{deviceId}/scan` | 扫描局域网 |
| POST | `/api/v1/devices/relay/{deviceId}/disconnect-user` | 踢断用户 |

### MikroTik 一键配置

WinBox → New Terminal → 粘贴 `mikrotik-scripts/setup-hotspot.rsc`，自动完成：
- REST API 开启
- Hotspot Server + IP 地址池 + NAT 配置
- Walled Garden 白名单（Portal服务器/微信/支付宝）
- 默认带宽限速（5M上行/10M下行）
- SNMP 监控开启

## 计费模式说明

| 模式 | 示例 | 扣费方式 |
|------|------|---------|
| **包天** | ¥9.90/天 | 一次性扣费，到期自动断网 |
| **包月** | ¥129/月 | 一次性扣费，到期自动断网 |
| **包年** | ¥999/年 | 一次性扣费，到期自动断网 |
| **1GB 流量包** | ¥15/GB | Redis Lua 实时按流量增量扣费 |
| **不限流量包月** | ¥199/月 | 包月 + 不限流量，到期断网 |
| **混合计费** | 包时 + 超额按流量 | 先扣时长费用，超额部分按MB扣 |

## 实时计费流程

```
会员购买套餐 → 创建订单(扣余额) → 更新到期时间
    ↓
开始上网会话 → MikroTik REST API 认证放行
    ↓
TrafficCollector 30秒轮询 MikroTik 活跃用户
    ↓
BillingEngine.deductTrafficFee() → Redis Lua 原子扣费
    ↓
余额不足 → 强制踢下线 + 结算
```

## 多租户隔离方案

- **数据库**: 共享表 + tenant_id 行级隔离
- **SQL**: MyBatis 拦截器自动注入 WHERE tenant_id = ?
- **上下文**: TenantInterceptor → JWT → ThreadLocal
- **Redis**: Key 前缀 `billing:{tenantId}:balance:{memberId}`
- **WebSocket**: 按 tenantId 分组管理连接，分别推送

## 设备对接支持

| 设备类型 | 接口方式 | 状态 |
|------|---------|:--:|
| MikroTik RouterOS | REST API (端口 8728/443) | ✅ |
| 华为 ME60/MA5800 | RADIUS (端口 1812/1813) | ✅ |
| H3C SR88/CR16000 | RADIUS (端口 1812/1813) | ✅ |
| 思科 ASR1000 | RADIUS (端口 1812/1813) | ✅ |
| Juniper MX | RADIUS (端口 1812/1813) | ✅ |
| 通用设备监控 | SNMP v2c (端口 161) | ✅ |

## 性能指标

| 指标 | 目标值 | 适用场景 |
|------|--------|---------|
| 并发在线会话 | 1000+ | 单实例 2C4G |
| 流量轮询间隔 | 30秒 | 可配置 |
| Redis 原子扣费 | <1ms | Lua 脚本 |
| MySQL 写入 QPS | >500 | HikariCP pool=50 |
| 内存占用 | <512MB | JVM -Xmx1024m |
| 连接池泄漏检测 | 60秒告警 | 生产环境 |

## 压力测试

```bash
# JMeter 图形界面
jmeter -t hotel-wifi-platform/stress-test/hotel-wifi-stress.jmx

# 命令行非 GUI 模式
jmeter -n -t hotel-wifi-platform/stress-test/hotel-wifi-stress.jmx -l results.jtl -e -o report/

# Shell 脚本快速测试
bash hotel-wifi-platform/stress-test/run-stress-test.sh
```

## 实施路线图

- **Phase 1 ✅ 已完成** (`1104305`) — 项目骨架、多租户、会员/套餐 CRUD、计费引擎、MikroTik 客户端、Vue 管理后台、Docker 部署
- **Phase 2 ✅ 已完成** (`92ce116`) — 短信认证、充值卡系统、WebSocket 实时推送、ECharts 收入趋势图、到期提醒、前端完整 CRUD 对话框
- **Phase 3 ✅ 已完成** (`999eb6f`) — RADIUS 协议对接（1812/1813）、SNMP 设备监控、MySQL 分区优化、Redis Cluster/Sentinel、jpackage 安装包、vue-i18n 中英双语、JMeter 压力测试
- **Phase 4 ✅ 已完成** (`c2f60b3`) — Portal住客认证前端（8页面）+ 后端API、在线支付（微信/支付宝）、住客个人中心、CardAuthProvider
- **部署 ✅ 已完成** (`1e98a87`) — Docker Compose 生产部署到 CentOS 7、SSH 密钥登录、演示服务器 http://185.239.71.210:8080
- **安全 ✅ 已完成** (`b4d8836`) — 37项安全漏洞审计修复、SSH加固、防火墙、Redis密码、fail2ban
- **Portal上线 ✅ 已完成** (`b6e7a36`) — 住客Portal部署到 /portal/、个人中心登录修复、测试会员数据
- **Phase 5 ✅ 已完成** (`25da45e`) — 内网设备管理方案（Agent中继+IP直连）、WebSocket设备中继、DeviceConnector独立JAR、MikroTik自动配置脚本、Monnify支付集成文档
- **响应式适配 ✅ 已完成** (`17605bb`) — 16文件全局响应式：手机/平板/桌面自适应，侧栏抽屉+抽屉汉堡菜单，7列表页表格横向滚动
- **Phase 8 📋 计划中** — 扩展更多设备类型：DeviceClient 抽象架构 + RADIUS 协议 + SNMP 监控 + UniFi/Omada/Meraki/Aruba/OpenWRT 单品驱动（详见 [使用说明书 §14](docs/使用说明书.md#14-扩展更多设备类型--可行性方案)）

## 工作规则

详见 [CLAUDE.md](CLAUDE.md)，主要规则：
- 会话使用中文回复
- 统一使用 PowerShell 执行命令
- 每次对话结束后保存关键记忆
- 先计划后执行
- 每次提交后更新本 README 和工作日志
