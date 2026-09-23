# HotelsX - 酒店房间管理系统

一个 Bukkit/Spigot/Paper 插件，玩家可以圈地创建酒店房间，其他玩家可以付费入住。

## 打包（下载后一键构建）

只需要装 **JDK 21 或更高版本**，**不需要预先安装 Maven** —— 仓库自带 Maven Wrapper，首次构建会自动下载 Maven 和依赖。

| 系统 | 操作 |
|------|------|
| Windows | 双击 `build.bat`，或在终端执行 `.\build.bat` |
| Linux / macOS | `chmod +x build.sh && ./build.sh` |
| 任意平台（手动） | `./mvnw clean package -DskipTests`（Windows 用 `mvnw.cmd`） |

构建产物位于 `target/HotelsX-<版本>.jar`，把它放进服务器的 `plugins` 目录后重启服务器即可。

脚本会先检查 Java 版本：如果 PATH 上的 `java` 是旧版本，会直接给出中文提示，而不是抛出一串编译错误。

> Windows 上 `build.bat` 只是启动器，真正的逻辑和中文提示在 `build.ps1` 里。原因是 cmd.exe 解析「含中文的批处理文件」时存在码页错位问题，会把行切错位置，所以 `.bat` 保持纯 ASCII、中文全部交给 PowerShell 输出。

需要跑单元测试时去掉 `-DskipTests`，即 `./mvnw clean verify`。

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
