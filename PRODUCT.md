# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **游戏内玩家（内容生产者）**：在服务器任意圈地创建酒店房间，进行入住/租赁/退房、评分评价、通过店面购物。玩家产出房间与交易内容。
- **服主/管理员（运营治理者）**：通过 Web 面板统一管理房间、合集、经济流水、评分、收益提现与店面；面板角色分 superadmin / admin / user 三级，游戏内命令 `/ht` 作为补充入口。

两者同等重要：玩家创造内容，管理员负责治理与运营。

## Product Purpose

HotelsX 是 Minecraft 服务器的一体化酒店管理插件（当前 v1.5.0，MIT License）：玩家圈地即创建酒店房间，支持入住/租赁、密码与门锁、经济扣费、评分评价、收益提现（escrow）与 Web 店面交易。内嵌的 Web 面板是运营中枢，覆盖全部管理操作。

成功意味着：普通玩家能零门槛建立并经营酒店房间；服主能以最低学习成本在面板上完成房间、经济与商店的日常治理。

## Positioning

多合一酒店经济闭环，在单插件内自洽：房间 + 合集 + 经济流水 + 评分 + 提现 + 店面商店，全部围绕"酒店"统一管理。Web 面板（管理）与游戏内交互（玩家）双入口并存，区别于依赖多插件拼装的同类方案。

## Operating Context

- Minecraft 服务端：兼容 Spigot / Paper / Folia；`api-version: '1.21'`，`folia-supported: true`。
- 经济系统：通过 Vault 结算，`soft-depend`（无 Vault 也可启动，此时经济功能受限）。
- Web 面板：由插件进程内自建 HTTP 服务（com.sun.net.httpserver）提供，随插件生命周期启停，端口与账号在 config.yml 配置；不依赖外部 Web 服务器。
- 运营语言为中文；数据持久化一律 UTF-8（rooms.yml / shops.yml 等 YAML 文件），兼容中文系统文件路径（一律经 plugin.getDataFolder() 解析，不硬编码路径）。
- 命令入口：`/hotels`（别名 `/ht`）；面板账号体系独立于游戏内账号，登录后与游戏内玩家名绑定用于交易等需实体交互的功能。

## Capabilities and Constraints

已确认功能：

- 房间：圈地创建/删除/编辑、状态（可入住/已入住/锁定/维护）、合集归类、密码与上锁、标签预设。
- 经济：入住/租赁扣费、房主收益、交易流水记录、收益提现（支持 escrow 托管模式）。
- 运营：Web 面板含概览、统计图表、运营报表、服务器状态、房间列表（批量操作）、合集、经济流水、评分评价、收益提现、公告与控制台、账号角色管理与密码修改。
- 店面系统（v1.4.3 起，Web 专属）：商店创建/编辑/停开业/补货入库/删除/购买；FIXED（固定库存+补货）与 AUTO（容器实时库存）两种模式；容器自动发现与绑定保护；离线购买物品进入待领取队列，玩家上线自动补发；销售款项直入店主游戏钱包。
- 安全机制：Web 会话鉴权 + CSRF Token 校验（X-CSRF-Token 头）；敏感 API（批量/删除/更新、管理操作、控制台执行、公告、提现）全部要求 CSRF；资源级权限（店主只能操作自己的店铺/房间）。

技术约束（确认）：

- 兼容 Spigot/Paper/Folia，调度层（SchedulerCompat）区分 Folia 与 Bukkit 调度。
- Vault 为软依赖。
- UTF-8 持久化 + 中文路径兼容。
- 所有 Java 文件含 MIT License 声明。
- Web 面板 UI 由插件内嵌资源（dashboard.html / style.css）运行，无前端构建链。

## Brand Commitments

- 产品名 HotelsX，命令 `/hotels`（别名 `/ht`），当前版本 1.5.0。
- 官方运营语言为中文（界面与注释以中文为主）。
- MIT License 开源。
- 未决（未确认绑定）：Web 面板当前视觉惯例（深色卡片 + 紫色渐变侧边栏）为现状实现，尚未被所有者确认作为持久的品牌视觉承诺；Store 店面 UI 风格同样待确认。

## Evidence on Hand

- 仓库：本项目 Hotels-main，分支 HotelsXshop 为使能店面系统的开发分支。
- 视觉实现：`src/main/resources/web/dashboard.html` + `style.css`（插件内嵌、Java 服务端模板渲染）。
- 配置：`src/main/resources/config.yml`（含 shop.* 店面配置段）；`plugin.yml`。
- 数据格式：rooms.yml / shops.yml / 其他 YAML，UTF-8。
- 公开缺省：无公开演示站、客户案例、测试服截图或评测证据；后续产出不得虚构这些内容。

## Product Principles

1. 双角色共建：玩家产出内容、管理员治理运营，两端体验同等重要，不偏废任一侧。
2. 零摩擦运营：所有管理操作都应在 Web 面板内完成，游戏内保持低门槛的直接交互。
3. 生态自洽：房间—经济—评分—提现—商店的闭环在单插件内成立，避免依赖多插件拼装。
4. 兼容优先：Spigot/Paper/Folia 全矩阵可用，Vault 软依赖退化流畅，中文服务器环境是头等公民。
5. 信任边界清晰：Web 面板的鉴权（会话 + CSRF + 资源归属校验）是运营安全硬底线，任何新管理功能都不能绕过。

## Accessibility & Inclusion

尚未建立产品特指的访问性要求。已知事实：面板面向中文运营者，移动端通过响应式布局可用的现状保留；无针对残障用户的既定承诺，不虚构。