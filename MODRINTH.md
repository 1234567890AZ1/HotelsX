# Summary（粘贴到 Modrinth 的 Summary 字段，上限 256 字符）

把任意区域变成可入住的酒店房间：圈地选区、上锁与密码、房主定价、限时租期、Vault 经济、五星评分、装修预设与完整 GUI 菜单。 Turn any region into a rentable hotel room: region selection, locks & passwords, owner-set prices, timed stays, Vault economy, star ratings, decoration presets and a full GUI.

---

# Description（粘贴到 Modrinth 的 Description 字段，以下全部内容）

## 把任意一块区域变成酒店房间

HotelsX 让玩家用木斧圈出一块地，把它变成可以收费入住的房间。
房主定价、上锁或设密码，其他玩家付费入住。

房主赚取收入，住客得到落脚处。日常使用完全不需要改配置文件，所有操作都在游戏内的 GUI 里完成。

### 功能

- **圈地建店** — 用选区工具（木斧）点选两个对角点，再执行 `/ht create <名称>`
- **上锁与密码** — 一键上锁阻止他人进入，也可以只把密码告诉指定的人
- **房主定价** — 每个房间可以单独设置入住价格
- **限时租期** — 按分钟出租，住客可用 `/ht extend` 续费延长
- **自动结算** — 接入 Vault 后入住自动扣款、退房自动结算；可开启托管模式，收益由房主用 `/ht claim` 自行提现
- **房间状态** — 空闲 / 已入住 / 维护中
- **五星评分** — 入住过的玩家可以打 1–5 分并留言，房主用 `/ht ratings` 查看
- **酒店合集** — 把多个房间归入一个合集，共享同一套租期
- **装修预设** — 把某个房间的建筑存为预设，一键套用到尺寸完全一致的其他房间，支持 0/90/180/270 度旋转
- **完整 GUI** — 主菜单、浏览房间、房间管理、管理员面板
- **细节体验** — 铁块电梯（全局开关）、`/ht tp` 传送回房、可同时入住多个房间、内置操作日志

### 命令

| 命令 | 说明 |
| --- | --- |
| `/hotels`、`/ht` | 打开主菜单 |
| `/ht wand` | 获取选区工具（木斧） |
| `/ht setspawn` | 设置房间传送点 |
| `/ht create <名称>` | 用当前选区创建房间 |
| `/ht remove <ID>` | 删除自己的房间 |
| `/ht manage <ID>` | 修改价格、密码、锁与租期 |
| `/ht list` | 查看自己的房间 |
| `/ht info <ID>` | 查看房间详情 |
| `/ht checkin <ID> [密码]` | 入住房间 |
| `/ht checkout [ID]` | 退房 |
| `/ht checkedin`、`/ht stays` | 查看当前入住的所有房间 |
| `/ht extend <ID> <分钟>` | 续费延长租期 |
| `/ht rate <ID> <1-5> [评语]` | 给自己住过的房间评分 |
| `/ht ratings <ID>` | 查看某个房间的评分 |
| `/ht tp` | 传送回自己的房间 |
| `/ht claim` | 提现托管收益 |
| `/ht preset list / save / apply / delete` | 管理装修预设 |
| `/ht elevator` | 全局开关铁块电梯（管理员） |
| `/ht admin ...` | 管理员工具 |

### 权限

| 权限节点 | 默认 | 说明 |
| --- | --- | --- |
| `hotels.use` | true | 使用酒店系统 |
| `hotels.create` | true | 创建房间 |
| `hotels.remove` | true | 删除自己的房间 |
| `hotels.admin` | op | 完整管理员权限 |
| `hotels.bypass` | op | 无视锁与密码入住任意房间 |

### 安装

1. （可选，推荐）安装一个兼容 Vault 的经济插件
2. 把 `HotelsX-<版本>.jar` 放进服务器的 `plugins/` 目录
3. 重启服务器

开箱即用，无需任何额外配置。

### 环境要求

- Paper / Spigot / Purpur **1.21 及以上**（支持 Folia）
- **Java 21**
- Vault 与经济插件 —— 可选，只有收费房间才需要

### 从源码构建

仓库自带 Maven Wrapper，**不需要预先安装 Maven**，只需要 JDK 21。

```
# Windows
build.bat

# Linux / macOS
chmod +x build.sh && ./build.sh
```

产物位于 `target/`。

### 许可证

MIT

---

## Turn any region into a hotel

HotelsX lets players claim an area with a wooden axe and turn it into a rentable room.
Set a price, add a lock or a password, and let other players pay to check in.

Room owners earn money. Guests get a place to stay. Day-to-day use needs no config
editing at all — everything is driven from the in-game GUI.

### Features

- **Region claiming** — Select two corners with the selection wand (a wooden axe), then run `/ht create <name>`
- **Locks & passwords** — Lock a room to keep everyone out, or hand out a password to chosen guests
- **Owner-set pricing** — Every room carries its own check-in price
- **Timed stays** — Rent rooms by duration, and let guests extend with `/ht extend`
- **Automatic payment** — Hook up Vault and money is charged on check-in, settled on check-out. An optional escrow mode holds earnings until the owner withdraws them with `/ht claim`
- **Room status** — Available / Occupied / Maintenance
- **Star ratings** — Anyone who has actually stayed can rate a room 1–5 with an optional comment; owners read them back with `/ht ratings`
- **Room collections** — Group rooms into a hotel that shares one rental duration
- **Decoration presets** — Save a room's build as a preset and stamp it onto any identically sized room, with 0/90/180/270 rotation
- **Full GUI** — Main menu, room browser, room management and admin panels
- **Quality of life** — Iron-block elevator (one global toggle), `/ht tp` back to your room, check in to several rooms at once, and a built-in action log for owners and admins

### Commands

| Command | Description |
| --- | --- |
| `/hotels`, `/ht` | Open the main menu |
| `/ht wand` | Get the selection wand (wooden axe) |
| `/ht setspawn` | Set the room's teleport point |
| `/ht create <name>` | Create a room from your current selection |
| `/ht remove <id>` | Delete one of your rooms |
| `/ht manage <id>` | Edit price, password, lock and duration |
| `/ht list` | List your own rooms |
| `/ht info <id>` | Show room details |
| `/ht checkin <id> [password]` | Check in |
| `/ht checkout [id]` | Check out |
| `/ht checkedin`, `/ht stays` | List every room you are currently checked into |
| `/ht extend <id> <minutes>` | Extend a timed stay |
| `/ht rate <id> <1-5> [comment]` | Rate a room you have stayed in |
| `/ht ratings <id>` | Read the ratings of a room |
| `/ht tp` | Teleport back to your room |
| `/ht claim` | Withdraw escrow earnings |
| `/ht preset list / save / apply / delete` | Manage decoration presets |
| `/ht elevator` | Toggle the iron-block elevator globally (admin) |
| `/ht admin ...` | Admin tools |

### Permissions

| Node | Default | Description |
| --- | --- | --- |
| `hotels.use` | true | Use the hotel system |
| `hotels.create` | true | Create rooms |
| `hotels.remove` | true | Remove your own rooms |
| `hotels.admin` | op | Full admin access |
| `hotels.bypass` | op | Check in to any room, ignoring locks and passwords |

### Installation

1. *(Optional but recommended)* install a Vault-compatible economy plugin
2. Drop `HotelsX-<version>.jar` into your `plugins/` folder
3. Restart the server

That is it — no configuration required to get started.

### Requirements

- Paper, Spigot or Purpur **1.21+** (Folia is supported)
- **Java 21**
- Vault plus an economy plugin — optional, only needed for paid rooms

### Building from source

The repository ships with the Maven Wrapper, so Maven does not need to be installed.
JDK 21 is the only requirement.

```
# Windows
build.bat

# Linux / macOS
chmod +x build.sh && ./build.sh
```

The jar lands in `target/`.

### License

MIT
