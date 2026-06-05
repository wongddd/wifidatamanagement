# 酒店 WiFi 数据管理计费系统

## 项目简介

面向连锁酒店的互联网管理计费平台，提供数据流量分发和计费功能。

### 核心功能

- **多租户管理**：一个集团管理多家酒店，数据隔离
- **会员管理**：住客账号、余额、积分、状态管理
- **套餐体系**：包天 / 包月 / 包年 / 按 MB/GB 流量计费
- **实时计费引擎**：预扣费策略，Redis Lua 脚本原子扣费
- **流量采集**：定时轮询 MikroTik RouterOS，30 秒间隔
- **设备管理**：多路由器集中管控，配置同步
- **认证方式**：账号密码 / 微信扫码 / 短信验证码 / 充值卡（SPI 可扩展）
- **报表统计**：流量统计、收入统计、在线趋势

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2+ |
| ORM | MyBatis-Plus | 3.5+ |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 认证 | Spring Security + JWT | - |
| 定时任务 | Spring Scheduler | - |
| API 文档 | Knife4j / SpringDoc | - |
| 前端框架 | Vue 3 + TypeScript + Vite | - |
| UI 框架 | Element Plus | - |
| 图表 | ECharts 5 | - |
| 状态管理 | Pinia | - |
| 构建工具 | Maven（后端）/ pnpm（前端） | - |
| 容器化 | Docker + Docker Compose | - |
| 设备对接 | MikroTik RouterOS REST API | RouterOS 6.45+ |

## 项目结构

```
D:\alenwifidata\
├── hotel-wifi-platform/               # 后端 Spring Boot 多模块项目
│   ├── pom.xml                        # 父 POM
│   ├── hotel-wifi-common/             # 公共模块（DTO、枚举、异常、JWT）
│   ├── hotel-wifi-core/               # 核心业务模块
│   │   └── src/main/java/com/alenwifidata/core/
│   │       ├── tenant/                # 多租户管理
│   │       ├── hotel/                 # 酒店管理
│   │       ├── member/                # 会员管理
│   │       ├── package/               # 套餐管理
│   │       ├── billing/               # 计费引擎（引擎核心、预扣费策略）
│   │       ├── traffic/               # 流量采集（定时轮询 MikroTik）
│   │       ├── device/                # 设备管理 + MikroTik 客户端
│   │       ├── auth/                  # 认证模块（SPI 可扩展）
│   │       ├── recharge/              # 充值卡管理
│   │       ├── notification/          # 通知提醒
│   │       └── report/                # 报表统计
│   ├── hotel-wifi-api/                # API 聚合 + 启动类 + 数据库迁移
│   ├── Dockerfile                     # 多阶段构建
│   ├── docker-compose.yml             # 一键部署（MySQL + Redis + API + Nginx）
│   └── nginx.conf                     # Nginx 反向代理配置
│
├── hotel-wifi-frontend/               # 前端 Vue 3 管理后台
│   └── src/
│       ├── api/                       # Axios 封装 + API 模块
│       ├── router/                    # 路由配置 + 权限守卫
│       ├── store/                     # Pinia 状态管理
│       ├── views/                     # 页面视图
│       │   ├── login/                 # 登录页
│       │   ├── dashboard/             # 首页仪表盘
│       │   ├── tenant/                # 租户管理
│       │   ├── hotel/                 # 酒店管理
│       │   ├── member/                # 会员管理
│       │   ├── package/               # 套餐管理
│       │   ├── billing/               # 计费管理（在线用户、实时扣费）
│       │   ├── device/                # 设备管理
│       │   └── report/                # 统计报表
│       ├── components/                # 通用组件（布局、表格、图表）
│       └── styles/                    # 全局样式
│
├── CLAUDE.md                          # Claude Code 工作规则
└── README.md                          # 本文件
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
- 默认账号: admin / admin123

### 方式 2：本地开发

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

## 核心 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/auth/login` | POST | 管理后台登录 |
| `/api/v1/members` | GET/POST | 会员管理 |
| `/api/v1/members/{id}/recharge` | POST | 会员充值 |
| `/api/v1/packages` | GET/POST | 套餐管理 |
| `/api/v1/billing/orders` | POST | 创建订单（购买套餐） |
| `/api/v1/billing/sessions/active` | GET | 实时在线用户 |
| `/api/v1/billing/sessions/{id}/kick` | POST | 强制踢下线 |
| `/api/v1/devices` | GET/POST | 路由器管理 |
| `/api/v1/devices/{id}/test` | POST | 测试设备连接 |
| `/api/v1/reports/traffic` | GET | 流量统计 |

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
| `traffic_snapshot` | 流量采样 |
| `billing_deduction` | 扣费记录 |
| `router_device` | 路由器设备 |
| `recharge_card` | 充值卡 |

## 实施路线图

- **Phase 1 ✅ 已完成**：项目骨架、多租户、会员/套餐 CRUD、基础计费、MikroTik 客户端、Vue 管理后台、Docker 部署
- **Phase 2（规划中）**：微信/短信认证、充值卡系统、报表图表完善、Dashboard 实时数据
- **Phase 3（规划中）**：RADIUS 协议对接、SNMP 监控、二进制安装包、性能优化

## 工作规则

详见 [CLAUDE.md](CLAUDE.md)，主要规则：
- 会话使用中文回复
- 统一使用 PowerShell 执行命令
- 每次对话结束后保存关键记忆
- 先计划后执行
- 每次提交后更新本 README 和工作日志
