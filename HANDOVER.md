# HotelsX 项目交接文档

文档更新日期：2026-09-10
项目版本：1.5.0

---

## 一、项目概述

HotelsX 是一个 Bukkit/Spigot/Paper 服务器端的酒店房间管理插件。玩家可以通过木斧圈地创建酒店房间，其他玩家可以付费入住。插件提供完整的房间生命周期管理（创建、入住、退房、续租、评分）、Vault 经济对接、装修预设系统（支持旋转）、Web 管理面板等功能。

项目默认支持 Folia（folia-supported: true），采用 region 线程调度兼容方案。

--- 

## 二、技术栈与构建环境

| 项目 | 说明 |
|------|------|
| 语言 | Java 21 |
| 构建工具 | Maven 3.11+ |
| Minecraft 版本 | 1.21（api-version: '1.21'） |
| 服务端核心 | Paper / Spigot（依赖 paper-api 1.21.11-R0.1-SNAPSHOT），Folia 兼容 |
| 软依赖 | Vault（optional，缺失时经济功能降级，插件仍可启动） |
| 测试框架 | JUnit 5（junit-jupiter 5.10.2） |
| 打包 | maven-shade-plugin 3.5.1，生成 shaded jar |

### 常用构建命令

```text
mvn compile        # 仅编译
mvn test           # 运行全部单元测试（47 个）
mvn clean package  # 清理并打包，输出 target/HotelsX-1.5.0.jar
```

注意：pom.xml 中的 surefire 配置了 `useModulePath=false` 和 `--add-modules=ALL-SYSTEM`，这是为了解决自定义 JDK 环境下 JUnit Platform 无法发现 TestEngine 的问题，请勿随意删除。

---

## 三、源码结构

主包：`com.hotels`，入口类 `com.hotels.HotelsPlugin`。

### 1. 核心入口与业务

| 文件 | 职责 |
|------|------|
| `HotelsPlugin.java` | 插件主入口，初始化并注册 Storage、PresetManager、CheckinHandler、WebServer、GUI 等所有核心组件，提供全局 getter |
| `CheckinHandler.java` | 入住流程核心：校验房间状态/密码/经济余额，扣款/托管入账，更新房间状态并传送玩家，触发欢迎效果（标题/ActionBar/音效） |
| `EconomyManager.java` | Vault 经济抽象层，处理余额扣款、入账、托管（escrow）模式 |
| `Selection.java`（selection 包） | 玩家圈地选区（木斧两角点），`SelectionManager` 管理选区创建/校验，`SelectionListener` 监听木斧交互 |

### 2. 命令（command 包）

`HotelsCommand.java` 是唯一命令入口（/hotels，别名 /ht），按子命令分发。核心子命令包括：

- 房间：wand、setspawn、create、remove、manage、list、info
- 入住：checkin、checkout、extend、stays / checkedin、rate
- 财务：claim（托管提现）
- 预设：preset save / list / apply / delete / undo（apply 支持 0/90/180/270 旋转角度与 confirm 参数）
- 管理：admin、web（含 web restart）
- 电梯：elevator（仅 OP 或 hotels.admin 可执行，全局开关）

### 3. 数据模型（model 包）

| 文件 | 职责 |
|------|------|
| `HotelRoom.java` | 房间数据模型：区域坐标、名称、价格、密码、标签、状态、入住信息 |
| `RoomPreset.java` | 装修预设模型：尺寸、方块快照、extras（容器内容/告示牌）、旋转字段（0/90/180/270），含坐标变换 `getRotatedRelCoords` 与 YAML 序列化 |
| `RoomCollection.java` | 房间合集（组）模型 |
| `Transaction.java` / `Rating.java` | 交易记录 / 评分模型 |

### 4. 存储层（storage 包）

| 文件 | 持久化文件 | 职责 |
|------|-----------|------|
| `RoomStorage.java` | rooms.yml | 房间与合集持久化，UTF-8 读写 |
| `PresetStorage.java` | presets.yml | 装修预设持久化，ReentrantLock 并发保护 |
| `TransactionStorage.java` | transactions.yml | 交易流水 |
| `RatingStorage.java` | ratings.yml | 评分记录 |
| `EscrowStorage.java` | escrow.yml | 托管资金（待提现收益） |

### 5. 装修预设（preset 包）

`PresetManager.java` 是预设核心逻辑：

- 区域快照（方块数据、容器、告示牌）
- 尺寸校验：仅当目标房间与预设尺寸完全一致（支持旋转后 X/Z 互换）才允许应用
- 按 chunk 分块应用，非 Folia 每 tick 处理一个 chunk，Folia 并行调度到区域线程
- 旋转支持：方块坐标变换 + BlockData（StructureRotation）朝向同步旋转
- 冲突检测：应用前统计房间已有方块数，非空需 confirm
- 自动备份与撤销：应用前自动 snapshot 到 undoBackups，/ht preset undo 可回滚

### 6. GUI（gui 包）

所有 GUI 类使用自定义 `GUIHolder`（InventoryHolder）标记界面身份，GUIListener 用 holder.getGuiName() 识别界面（HIGHEST 优先级），不使用标题字符串。

- `MainMenuGUI` / `BrowseRoomsGUI` / `MyRoomsGUI` / `RoomManageGUI` / `CollectionGUI` / `TagSelectGUI` / `AdminPanelGUI` / `ElevatorGUI`

### 7. 监听器（listener 包）

- `GUIListener` / `SelectionListener` / `ChatInputHandler`
- `RoomProtectListener` / `DoorGuardListener` / `RoomGuardListener`（房间保护、门禁）
- `ElevatorListener`（铁块电梯）

### 8. Web 面板（web 包）

| 文件 | 职责 |
|------|------|
| `WebServer.java` | Web 面板主服务：启动/停止 HTTP(S)、路由注册（约 30 个 handler）、会话管理、权限校验、页面构建 |
| `WebHttp.java` | 静态 HTTP/JSON 工具类：sendJson/sendHtml/安全响应头/表单解析/转义/JSON 序列化 |
| `WebConsole.java` | 控制台日志捕获（WebConsoleHandler）/ 内存缓冲（500 条）/ 日志文件读取 |

前端模板已资源化至 `src/main/resources/web/`：

- `style.css` - 全站样式（含夜间模式、图表）
- `login.html` - 登录页
- `dashboard.html` - 管理面板完整模板 + 全部 JS（41 个格式化占位符与 Java `.formatted()` 实参严格对应）

### 9. 工具类（util 包）

- `PasswordUtil.java` - PBKDF2WithHmacSHA256 密码哈希
- `SchedulerCompat.java` - Folia 兼容调度封装（runOnRegion / runTaskTimer / CancellableTask）

### 10. Web 面板路由一览（WebServer.registerHandlers）

- 页面：`/`（Dashboard）、`/login`
- 认证：`/api/login`、`/api/logout`、`/api/me`
- 状态：`/api/server`、`/api/stats`、`/api/stats/detail`、`/api/stats/report`
- 房间：`/api/rooms`、`/api/room/delete`、`/api/room/update`、`/api/room/batch`、`/api/collections`
- 管理：`/api/admins`、`/api/admin/add`、`/api/admin/delete`、`/api/admin/change-password`
- 控制台：`/api/console/logs`、`/api/console/execute`、`/api/broadcast`、`/api/events`、`/api/events/console`
- 数据：`/api/transactions`、`/api/ratings`、`/api/escrow`、`/api/escrow/withdraw`

---

## 四、测试

测试位于 `src/test/java/com/hotels/`，共 47 个用例，全部通过：

- `model/RoomPresetTest.java` - 旋转坐标变换（0/90/180/270/360/负角度/大角度）、角度校验、尺寸匹配、序列化（含旧数据 rotation 默认 0）、快照行解析、性能测试（百万次坐标变换约 3-5ms）
- `preset/PresetManagerTest.java` - 房间边界归一化（正/反向角点、小数向下取整、单方块房间）、尺寸计算（尺寸=距离+1）

运行：`mvn test`

---

## 五、配置说明

配置文件：`src/main/resources/config.yml`，主要节点：

```yaml
max-rooms-per-player: 10          # 每玩家最大房间数
max-collections-per-player: 5     # 每玩家最大合集数
max-room-volume: 50000            # 房间最大体积
allow-cross-world: false          # 是否允许跨世界

checkin:
  auto-teleport: true             # 入住自动传送
  max-stay-seconds: 0             # 最大入住时长（0=不限）
  welcome-title: true             # 入住欢迎标题
  welcome-actionbar: true         # 入住欢迎 ActionBar
  welcome-sound: true             # 入住欢迎音效

economy:
  escrow-mode: false              # 托管模式（收益进待提现，需 /ht claim 提现）

web-port: 17409                   # Web 面板端口
web-bind: 127.0.0.1               # 绑定地址（0.0.0.0 需设强密码）
web-enabled: true
web-admins: []                    # 管理员账号（首次启动自动生成随机密码）
web-session-timeout: 30           # 会话超时（分钟）
web-ssl-enabled: false            # HTTPS 开关及相关配置

elevator:
  enabled: true                   # 电梯
  max-distance: 64

protection:
  block-break-protection: true    # 方块保护
  block-place-protection: true
  container-protection: true
  explosion-protection: true
  door-protection: true
```

插件权限（plugin.yml）：hotels.use / hotels.create / hotels.remove（默认 true），hotels.admin（默认 op，含 bypass），hotels.bypass（默认 op）。

---

## 六、数据文件

运行后生成于 `plugins/HotelsX/`：

| 文件 | 内容 |
|------|------|
| rooms.yml | 房间（空数据，格式 rooms/collections） |
| presets.yml | 装修预设 |
| transactions.yml | 交易记录 |
| ratings.yml | 评分 |
| escrow.yml | 托管资金 |
| web-admin.txt | 首次启动生成的 Web 管理员初始密码 |
| web-keystore.p12 | SSL 密钥库（启用 HTTPS 时） |

安全注意：rooms.yml 必须保持小体积（空或最小数据），防止 YAML 解析 OutOfMemoryError。房间数据变化后用 /ht admin save（或服务器保存机制）持久化。

---

## 七、Web 面板安全说明

- 密码使用 PBKDF2WithHmacSHA256（10 万次迭代）哈希存储，登录时自动从明文迁移
- 初始超级管理员密码随机生成，写入 web-admin.txt 与 config.yml
- 三重角色：user（仅管理自己的房间）、admin（可修改所有房间，不可删除）、superadmin（全部权限）
- 会话 Cookie：HttpOnly + SameSite=Lax + Secure（HTTPS 时），30 分钟超时
- 安全响应头：X-Frame-Options: DENY、nosniff、Referrer-Policy、Cache-Control: no-store 等
- 绑定 0.0.0.0 时启动会打印强密码警告

已知安全弱项（历史安全审查发现，待修复）：CSRF 防护缺失、会话固定风险。后续开发建议优先处理。

---

## 八、开发规范与硬约束（务必遵守）

以下为项目历史沉淀的关键约束，改动前必须遵循：

1. 所有 GUI 类必须使用 GUIHolder（自定义 InventoryHolder）识别界面，禁止用标题字符串
2. GUIListener 必须用 holder.getGuiName() 识别界面，事件优先级 HIGHEST
3. ApiServer.java 与 web/index.html 已废弃删除，禁止重新引入
4. 所有 Java 文件必须包含 MIT License 版权头声明
5. RoomStorage 必须使用 plugin.getDataFolder()，禁止硬编码路径（中文服务器路径问题）
6. YAML 读写必须以 UTF-8 编码（中文支持）
7. plugin.yml 使用 soft-depend: ['Vault']，禁止改为 depend
8. 禁止引入 PlaceholderAPI 相关文件/依赖
9. 项目版本固定为 1.5.0
10. `/ht elevator` 仅 OP 或 hotels.admin 可执行，控制全局电梯状态
11. 玩家可同时入住多个房间；/ht checkout 支持可选房间 ID；/ht checkedin 或 /ht stays 显示全部在住房间
12. 旋转预设应用前必须做冲突检测（房间已有方块需 confirm），应用前自动备份，支持 /ht preset undo 回滚

---

## 九、Git 与版本

- 远程仓库：`hotelsx` → https://github.com/1234567890AZ1/HotelsX.git（主推送目标）
- 远程仓库：`origin` → https://github.com/1234567890AZ1/Hotels.git（旧仓库）
- 分支：master（已推送到 hotelsx/master 与 hotelsx/main）
- 最近提交：c1e179b feat: v1.4.3 - Web管理面板、门禁系统、Folia兼容性与安全加固
- 版本历史：1.4.3 包含 — 旋转装修预设、冲突检测与回滚、入住欢迎效果、Web 面板重构（模板资源化 + 类拆分）、PBKDF2 密码哈希、Folia 兼容

---

## 十、后续可发展方向（备选）

- 预订系统（提前预订、时间窗口、防重复）
- 房间状态机（空闲/已预订/清洁中/维护中）
- 房型与等级（标准间/豪华房/套房，独立定价与权限）
- 房间搜索/筛选/排序（Web 与 GUI 同步）
- 动态定价（节假日/周末/高峰）
- 营业数据统计与报表导出
- Web 面板 CSRF 防护、登录失败锁定、二次验证
- 预设选择性应用（只应用墙体/家具/装饰）、预设版本管理

---

## 十一、常见问题排查

1. Web 面板无法访问：检查 web-enabled、web-bind/web-port 配置，执行 /ht web restart
2. 预设应用失败"尺寸不匹配"：目标房间必须与预设尺寸完全一致（旋转 90/270 时 X/Z 互换）
3. 预设应用被拦截：房间已有方块，需加 confirm 参数确认覆盖
4. 测试不运行：检查 maven surefire 配置（useModulePath=false、--add-modules=ALL-SYSTEM）未被改动
5. 中文乱码：确认文件写入均使用 UTF-8 编码
6. Vault 未安装：经济功能不可用但插件可启动（soft-depend）