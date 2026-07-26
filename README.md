# HotelsX - 酒店房间管理系统

一个 Bukkit/Spigot/Paper 插件，玩家可以圈地创建酒店房间，其他玩家可以付费入住。

## 功能

- 使用木斧选区创建房间（类似 WorldEdit）
- 房间上锁/密码保护
- 房主设置入住价格
- 房间状态：空闲 / 已入住 / 维护中
- Vault 经济对接
- 完整的 GUI 菜单管理
- 管理员命令

## 命令

| 命令 | 别名 | 说明 |
|------|------|------|
| `/hotels` | `/ht` | 打开酒店主菜单 |
| `/ht wand` | | 获取选区工具（木斧） |
| `/ht setspawn` | | 设置房间传送点 |
| `/ht create <名称>` | | 创建房间 |
| `/ht remove <ID>` | | 删除房间 |
| `/ht manage <ID>` | | 管理房间 |
| `/ht list` | | 查看我的房间 |
| `/ht checkin <ID> [密码]` | | 入住房间 |
| `/ht checkout` | | 退房 |
| `/ht info <ID>` | | 查看房间信息 |
| `/ht admin` | | 管理命令 |

## 权限

| 权限节点 | 默认 | 说明 |
|---------|------|------|
| hotels.use | true | 允许使用酒店系统 |
| hotels.create | true | 允许创建房间 |
| hotels.remove | true | 允许删除自己的房间 |
| hotels.admin | op | 管理员权限（包含所有） |
| hotels.bypass | op | 无视密码/锁入住 |

## 使用流程

1. 输入 `/ht` 打开菜单
2. 点击「创建新房间」或输入 `/ht wand` 获取木斧
3. 左键/右键选择区域两个对角点
4. 站在入口位置输入 `/ht setspawn`
5. 输入 `/ht create 我的豪华套房` 创建房间
6. 使用 `/ht manage <ID>` 设置价格、密码等
7. 其他玩家通过 `/ht` → 「浏览房间」入住
