/*
 * HotelsX - 酒店房间管理插件
 * MIT License
 *
 * Copyright (c) 2024-2026 HotelsX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hotels.command;

import com.hotels.HotelsPlugin;
import com.hotels.gui.AdminPanelGUI;
import com.hotels.gui.BrowseRoomsGUI;
import com.hotels.gui.MainMenuGUI;
import com.hotels.gui.MyRoomsGUI;
import com.hotels.gui.RoomManageGUI;
import com.hotels.model.HotelRoom;
import com.hotels.model.PlayerSelection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /hotels 命令处理器
 */
public class HotelsCommand implements CommandExecutor, TabCompleter {

    private final HotelsPlugin plugin;

    public HotelsCommand(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // 打开主菜单
            if (!player.hasPermission("hotels.use")) {
                player.sendMessage("§c你没有权限使用酒店系统");
                return true;
            }
            plugin.log(player, "打开主菜单");
            MainMenuGUI.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "wand":
            case "sel":
                handleWand(player);
                break;

            case "create":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ht create <房间名称>");
                    return true;
                }
                handleCreate(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "remove":
            case "delete":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ht remove <房间ID>");
                    return true;
                }
                handleRemove(player, args[1]);
                break;

            case "manage":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ht manage <房间ID>");
                    return true;
                }
                handleManage(player, args[1]);
                break;

            case "list":
                handleList(player);
                break;

            case "checkin":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ht checkin <房间ID>");
                    return true;
                }
                handleCheckin(player, args[1]);
                break;

            case "checkout":
                plugin.log(player, "尝试退房");
                if (args.length >= 2) {
                    plugin.getCheckinHandler().checkout(player, args[1]);
                } else {
                    plugin.getCheckinHandler().checkout(player);
                }
                break;

            case "checkedin":
            case "stays":
                handleListCheckedIn(player);
                break;

            case "web":
                handleWeb(player, args);
                break;

            case "tp":
            case "teleport":
                handleTeleport(player);
                break;

            case "confirm":
                // 将 confirm 命令转发到聊天输入处理器
                player.chat("/confirm");
                break;

            case "info":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ht info <房间ID>");
                    return true;
                }
                handleInfo(player, args[1]);
                break;

            case "debug":
                handleDebug(player);
                break;

            case "elevator":
                handleElevator(player);
                break;

            case "admin":
                handleAdmin(player, args);
                break;

            case "rate":
                handleRate(player, args);
                break;

            case "extend":
                handleExtend(player, args);
                break;

            case "ratings":
            case "rating":
                handleRatings(player, args);
                break;

            case "claim":
                handleClaim(player);
                break;

            default:
                player.sendMessage("§c未知子命令，输入 /ht 查看帮助");
                break;
        }

        return true;
    }

    private void handleWand(Player player) {
        if (!player.hasPermission("hotels.create")) {
            player.sendMessage("§c你没有权限创建房间");
            return;
        }

        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l选区工具");
            meta.setLore(Arrays.asList(
                    "§7左键: 设置第 1 点",
                    "§7右键: 设置第 2 点"
            ));
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        plugin.log(player, "获取选区工具");
        player.sendMessage("§a已获取选区工具（木斧）");
        player.sendMessage("§7左键点击方块/空气设置第 1 点");
        player.sendMessage("§7右键点击方块/空气设置第 2 点");
    }

    private void handleCreate(Player player, String name) {
        if (!player.hasPermission("hotels.create")) {
            player.sendMessage("§c你没有权限创建房间");
            return;
        }

        PlayerSelection sel = plugin.getSelectionManager().getSelection(player);
        if (!sel.hasBothPositions()) {
            player.sendMessage("§c请先使用木斧选择区域的两个对角点");
            return;
        }

        Location p1 = sel.getPos1();
        Location p2 = sel.getPos2();

        if (!p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage("§c两个点必须在同一个世界");
            return;
        }

        if (plugin.getRoomStorage().isOverlapping(
                p1.getWorld().getName(),
                p1.getX(), p1.getY(), p1.getZ(),
                p2.getX(), p2.getY(), p2.getZ(),
                null)) {
            player.sendMessage("§c该区域与其他房间重叠");
            return;
        }

        if (name.length() > 32) {
            player.sendMessage("§c房间名称最长 32 个字符");
            return;
        }

        HotelRoom room = new HotelRoom();
        room.setName(name);
        room.setOwner(player.getUniqueId());
        room.setOwnerName(player.getName());
        room.setWorldName(p1.getWorld().getName());
        room.setX1(p1.getX());
        room.setY1(p1.getY());
        room.setZ1(p1.getZ());
        room.setX2(p2.getX());
        room.setY2(p2.getY());
        room.setZ2(p2.getZ());

        Location playerLoc = player.getLocation();
        room.setSpawnX(playerLoc.getX());
        room.setSpawnY(playerLoc.getY());
        room.setSpawnZ(playerLoc.getZ());
        room.setSpawnYaw(playerLoc.getYaw());
        room.setSpawnPitch(playerLoc.getPitch());

        room.setPrice(0.0);

        plugin.getRoomStorage().saveRoom(room);
        plugin.getSelectionManager().clearSelection(player);

        plugin.log(player, "创建房间: " + name + " (ID: " + room.getId() + ")");
        player.sendMessage("§a房间 §e" + name + " §a创建成功！");
        player.sendMessage("§7房间 ID: §e" + room.getId());
        player.sendMessage("§7传送点已设置为当前位置");
        player.sendMessage("§7使用 §e/ht manage " + room.getId() + " §7管理房间");
    }

    private void handleRemove(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }

        if (!room.getOwner().equals(player.getUniqueId()) && !player.hasPermission("hotels.admin")) {
            player.sendMessage("§c你不是这个房间的房主");
            return;
        }

        plugin.getRoomStorage().removeRoom(roomId);
        plugin.log(player, "删除房间: " + room.getName() + " (ID: " + roomId + ")");
        player.sendMessage("§c房间 §e" + room.getName() + " §c已删除");
    }

    private void handleManage(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }

        if (!room.getOwner().equals(player.getUniqueId()) && !player.hasPermission("hotels.admin")) {
            player.sendMessage("§c你不是这个房间的房主");
            return;
        }

        plugin.log(player, "打开房间管理界面: " + room.getName() + " (ID: " + roomId + ")");
        RoomManageGUI.open(player, room, plugin);
    }

    private void handleList(Player player) {
        plugin.log(player, "查看我的房间列表");
        MyRoomsGUI.open(player, plugin);
    }

    private void handleListCheckedIn(Player player) {
        java.util.List<HotelRoom> rooms = plugin.getCheckinHandler().getPlayerRooms(player);
        
        if (rooms.isEmpty()) {
            player.sendMessage("§c你当前没有入住任何房间");
            return;
        }

        player.sendMessage("§6===== 你当前入住的房间 =====");
        for (HotelRoom room : rooms) {
            String name = room.getName() != null ? room.getName() : room.getId();
            player.sendMessage("§7- §e" + name + " §7(ID: " + room.getId() + ")");
            player.sendMessage("§7  §f退房: §e/ht checkout " + room.getId());
        }
        player.sendMessage("§6============================");
    }

    private void handleWeb(Player player, String[] args) {
        com.hotels.web.WebServer ws = plugin.getWebServer();
        if (ws == null) {
            player.sendMessage("§cWeb 面板未初始化");
            return;
        }

        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";

        // 普通玩家可用的子命令
        switch (sub) {
            case "register":
                handleWebRegister(player, args, ws);
                return;
            case "changepwd":
            case "password":
                handleWebChangePwd(player, args, ws);
                return;
            case "me":
            case "info":
                handleWebMe(player, ws);
                return;
            case "help":
                handleWebHelp(player, ws);
                return;
        }

        // 其他命令需要 admin 权限
        if (!player.hasPermission("hotels.admin") && !player.isOp()) {
            player.sendMessage("§c你没有权限使用该命令");
            player.sendMessage("§7普通玩家可用命令:");
            player.sendMessage("§8  /ht web register <用户名>  §7- 注册 Web 账户（密码在聊天框输入）");
            player.sendMessage("§8  /ht web changepwd           §7- 修改密码（密码在聊天框输入）");
            player.sendMessage("§8  /ht web me                  §7- 查看注册信息");
            player.sendMessage("§8  /ht web help                §7- 显示帮助");
            return;
        }

        String url = "http://<服务器IP>:" + ws.getPort();

        switch (sub) {
            case "start":
                if (ws.isRunning()) {
                    player.sendMessage("§cWeb 面板已在运行");
                    player.sendMessage("§7访问: §e" + url);
                } else if (ws.start()) {
                    player.sendMessage("§aWeb 面板已启动");
                    player.sendMessage("§7访问: §e" + url);
                } else {
                    player.sendMessage("§c启动失败，请查看控制台日志");
                }
                break;

            case "stop":
                if (ws.isRunning()) {
                    ws.stop();
                    player.sendMessage("§cWeb 面板已停止");
                } else {
                    player.sendMessage("§7Web 面板未运行");
                }
                break;

            case "restart":
                if (ws.isRunning()) ws.stop();
                if (ws.start()) {
                    player.sendMessage("§aWeb 面板已重启");
                    player.sendMessage("§7访问: §e" + url);
                } else {
                    player.sendMessage("§c重启失败");
                }
                break;

            case "status":
                if (ws.isRunning()) {
                    player.sendMessage("§aWeb 面板运行中");
                    player.sendMessage("§7访问地址: §e" + url);
                    player.sendMessage("§7端口: §e" + ws.getPort());
                } else {
                    player.sendMessage("§7Web 面板未运行，输入 §e/ht web start §7启动");
                }
                break;

            default:
                handleWebHelp(player, ws);
                break;
        }
    }

    private void handleWebHelp(Player player, com.hotels.web.WebServer ws) {
        player.sendMessage("§6═══════ §eHotelsX Web 面板 §6═══════");
        boolean isAdmin = player.hasPermission("hotels.admin") || player.isOp();
        if (isAdmin) {
            player.sendMessage("§6管理命令:");
            player.sendMessage("§8  /ht web start    §7- 启动 Web 面板");
            player.sendMessage("§8  /ht web stop     §7- 停止 Web 面板");
            player.sendMessage("§8  /ht web restart  §7- 重启 Web 面板");
            player.sendMessage("§8  /ht web status   §7- 查看运行状态");
        }
        player.sendMessage("§6账户命令:");
        player.sendMessage("§8  /ht web register <用户名>  §7- 注册 Web 账户（密码在聊天框输入）");
        player.sendMessage("§8  /ht web changepwd           §7- 修改密码（密码在聊天框输入）");
        player.sendMessage("§8  /ht web me                  §7- 查看注册信息");
        player.sendMessage("§8  /ht web help                §7- 显示此帮助");
        if (ws.isRunning()) {
            player.sendMessage("§7访问地址: §ehttp://<服务器IP>:" + ws.getPort());
        } else {
            player.sendMessage("§7Web 面板当前未运行" + (isAdmin ? "，输入 §e/ht web start §7启动" : ""));
        }
        player.sendMessage("§6══════════════════════════════════");
    }

    private void handleWebRegister(Player player, String[] args, com.hotels.web.WebServer ws) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /ht web register <用户名>");
            return;
        }
        String username = args[2];
        if (ws.usernameExists(username)) {
            player.sendMessage("§c用户名已存在");
            return;
        }
        // 密码通过聊天框输入，避免出现在命令日志中
        player.sendMessage("§e请输入密码（至少4位，输入后回车确认）:");
        player.sendMessage("§7提示: 注册后自动关联您的游戏ID（" + player.getName() + "），作为普通用户只能管理自己的房间");
        plugin.getChatInputHandler().expectInput(player, "web_register_pwd:" + username);
    }

    private void handleWebChangePwd(Player player, String[] args, com.hotels.web.WebServer ws) {
        if (ws.getUsernameByMinecraft(player.getName()) == null) {
            player.sendMessage("§c您尚未注册 Web 账户");
            player.sendMessage("§7输入 §e/ht web register <用户名> §7注册");
            return;
        }
        // 密码通过聊天框输入，避免出现在命令日志中
        player.sendMessage("§e请输入新密码（至少4位，输入后回车确认）:");
        plugin.getChatInputHandler().expectInput(player, "web_changepwd");
    }

    private void handleWebMe(Player player, com.hotels.web.WebServer ws) {
        String username = ws.getUsernameByMinecraft(player.getName());
        if (username == null) {
            player.sendMessage("§7您尚未注册 Web 管理员账户");
            player.sendMessage("§7输入 §e/ht web register <用户名> <密码> §7注册");
        } else {
            player.sendMessage("§a您已注册 Web 管理员账户");
            player.sendMessage("§7账户: §e" + username);
            player.sendMessage("§7关联游戏ID: §e" + player.getName());
            player.sendMessage("§7修改密码: §e/ht web changepwd <新密码>");
        }
    }

    private void handleCheckin(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }
        // 有密码且无 bypass 权限时，通过聊天框输入密码（避免密码出现在命令日志中）
        if (room.hasPassword() && !player.hasPermission("hotels.bypass")) {
            player.sendMessage("§e请输入房间密码（输入后回车确认）:");
            plugin.getChatInputHandler().expectInput(player, "checkin_password:" + roomId);
            return;
        }
        plugin.log(player, "尝试入住房间: " + room.getName() + " (ID: " + roomId + ")");
        plugin.getCheckinHandler().attemptCheckin(player, room);
    }

    private void handleInfo(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }

        plugin.log(player, "查看房间信息: " + room.getName() + " (ID: " + roomId + ")");
        player.sendMessage("§6=== 房间信息 ===");
        player.sendMessage("§7名称: §f" + room.getName());
        player.sendMessage("§7ID: §f" + room.getId());
        player.sendMessage("§7房主: §f" + room.getOwnerName());
        player.sendMessage("§7状态: " + getStatusDisplay(room.getStatus()));
        player.sendMessage("§7价格: §f" + plugin.getEconomyManager().format(room.getPrice()));
        player.sendMessage("§7世界: §f" + room.getWorldName());
        player.sendMessage("§7区域: §f" + room.getVolume() + " 方块");
        player.sendMessage("§7锁定: " + (room.isLocked() ? "§c是" : "§a否"));
        player.sendMessage("§7密码: " + (room.hasPassword() ? "§c是" : "§a否"));
        if (room.isOccupied()) {
            player.sendMessage("§7客人: §f" + room.getCurrentGuestName());
        }
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("hotels.admin")) {
            player.sendMessage("§c你没有管理员权限");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§6=== 酒店管理 ===");
            player.sendMessage("§e/ht admin panel §7- 打开管理员面板");
            player.sendMessage("§e/ht admin list §7- 所有房间列表");
            player.sendMessage("§e/ht admin tp <ID> §7- 传送到指定房间");
            player.sendMessage("§e/ht admin remove <ID> §7- 强制删除房间");
            player.sendMessage("§e/ht admin reload §7- 重载配置");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "panel":
                AdminPanelGUI.open(player);
                plugin.log(player, "打开管理员面板");
                break;

            case "list":
                player.sendMessage("§6所有房间 (§e" + plugin.getRoomStorage().getRoomCount() + "§6):");
                for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
                    player.sendMessage(String.format(
                            " §7- §e%s §7(%s) §7房主: %s §7[%s]",
                            room.getName(), room.getId(), room.getOwnerName(),
                            getStatusDisplay(room.getStatus())
                    ));
                }
                break;

            case "tp":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /ht admin tp <房间ID>");
                    return;
                }
                HotelRoom tpRoom = plugin.getRoomStorage().getRoom(args[2]);
                if (tpRoom == null) {
                    player.sendMessage("§c房间不存在");
                    return;
                }
                Location tpLoc = new Location(
                        Bukkit.getWorld(tpRoom.getWorldName()),
                        tpRoom.getSpawnX(), tpRoom.getSpawnY(), tpRoom.getSpawnZ(),
                        tpRoom.getSpawnYaw(), tpRoom.getSpawnPitch()
                );
                com.hotels.util.SchedulerCompat.teleport(player, tpLoc);
                plugin.log(player, "管理员传送: 到房间 " + tpRoom.getName());
                player.sendMessage("§a已传送到房间 " + tpRoom.getName());
                break;

            case "remove":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /ht admin remove <房间ID>");
                    return;
                }
                HotelRoom rmRoom = plugin.getRoomStorage().getRoom(args[2]);
                if (rmRoom == null) {
                    player.sendMessage("§c房间不存在");
                    return;
                }
                String rmName = rmRoom.getName();
                plugin.getRoomStorage().removeRoom(args[2]);
                plugin.log(player, "管理员强制删除房间: " + rmName + " (ID: " + args[2] + ")");
                player.sendMessage("§c已强制删除房间 " + rmName);
                break;

            case "reload":
                plugin.reloadConfig();
                plugin.getRoomStorage().loadAll();
                plugin.log(player, "管理员重新加载配置和房间数据");
                player.sendMessage("§a配置和房间数据已重新加载");
                player.sendMessage("§7请重新打开房间列表以查看更新");
                break;

            default:
                player.sendMessage("§c未知管理命令");
                break;
        }
    }

    /**
     * /ht rate <房间ID> <分数1-5> [评语] - 给入住过的房间评分
     */
    private void handleRate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /ht rate <房间ID> <分数1-5> [评语]");
            return;
        }
        HotelRoom room = plugin.getRoomStorage().getRoom(args[1]);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }
        int score;
        try {
            score = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c分数必须是 1-5 的整数");
            return;
        }
        if (score < 1 || score > 5) {
            player.sendMessage("§c分数必须是 1-5 的整数");
            return;
        }

        // 检查玩家是否入住过该房间（当前入住或有付款流水）
        boolean everStayed = room.isOccupied()
                && room.getCurrentGuest() != null
                && room.getCurrentGuest().equals(player.getUniqueId());
        if (!everStayed) {
            for (com.hotels.model.Transaction tx : plugin.getTransactionStorage().getByPlayer(player.getUniqueId())) {
                if (tx.getType() == com.hotels.model.Transaction.TxType.CHECKIN_PAY
                        && args[1].equals(tx.getRoomId())) {
                    everStayed = true;
                    break;
                }
            }
        }
        if (!everStayed) {
            player.sendMessage("§c你还没有入住过这个房间，无法评分");
            return;
        }

        String comment = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        if (comment.length() > 100) {
            player.sendMessage("§c评语最长 100 个字符");
            return;
        }

        com.hotels.model.Rating r = new com.hotels.model.Rating();
        r.setRoomId(room.getId());
        r.setRoomName(room.getName());
        r.setGuestUuid(player.getUniqueId().toString());
        r.setGuestName(player.getName());
        r.setScore(score);
        r.setComment(comment);
        boolean wasRated = plugin.getRatingStorage().hasRated(room.getId(), player.getUniqueId().toString());
        plugin.getRatingStorage().add(r);

        plugin.log(player, "评分: " + room.getName() + " (ID: " + room.getId() + ") " + score + " 星" + (comment.isEmpty() ? "" : " 评语:" + comment));
        player.sendMessage((wasRated ? "§a已更新评分" : "§a评分成功") + " §e" + room.getName() + " §7" + r.getStars());
        if (!comment.isEmpty()) {
            player.sendMessage("§7评语: §f" + comment);
        }
    }

    /**
     * /ht extend <房间ID> <续费分钟> - 客人为自己入住的房间续费延长
     */
    private void handleExtend(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /ht extend <房间ID> <续费分钟>");
            return;
        }
        HotelRoom room = plugin.getRoomStorage().getRoom(args[1]);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }
        if (room.getCurrentGuest() == null || !room.getCurrentGuest().equals(player.getUniqueId())) {
            player.sendMessage("§c你不是该房间的入住客人，无法续费");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c续费分钟数必须是整数");
            return;
        }
        if (minutes <= 0) {
            player.sendMessage("§c续费分钟数必须大于 0");
            return;
        }
        if (minutes > 1440) {
            player.sendMessage("§c单次最多续费 24 小时（1440 分钟）");
            return;
        }

        // 获取本次入住的有效时长（分钟）
        int duration = room.getDurationMinutes();
        if (duration == -1) {
            for (com.hotels.model.RoomCollection col : plugin.getRoomStorage().getAllCollections()) {
                if (col.getRoomIds().contains(room.getId())) {
                    duration = col.getDurationMinutes();
                    break;
                }
            }
            if (duration == -1) duration = 0;
        }
        if (duration <= 0) {
            player.sendMessage("§c该房间不限时，无需续费");
            return;
        }

        // 续费价格 = 当前价格 × (续费分钟 / 有效时长分钟)
        double cost = room.getCurrentPrice() * ((double) minutes / duration);
        cost = Math.round(cost * 100) / 100.0;

        if (plugin.getEconomyManager().isEnabled()) {
            double balance = plugin.getEconomyManager().getBalance(player);
            if (balance < cost) {
                player.sendMessage("§c余额不足！续费需要 " + plugin.getEconomyManager().format(cost)
                        + "，你只有 " + plugin.getEconomyManager().format(balance));
                return;
            }
            if (!plugin.getEconomyManager().withdraw(player, cost)) {
                player.sendMessage("§c扣款失败");
                return;
            }
            // 续费流水
            try {
                com.hotels.model.Transaction tx = new com.hotels.model.Transaction();
                tx.setType(com.hotels.model.Transaction.TxType.EXTEND_PAY);
                tx.setPlayerUUID(player.getUniqueId().toString());
                tx.setPlayerName(player.getName());
                tx.setAmount(cost);
                tx.setRoomId(room.getId());
                tx.setRoomName(room.getName());
                tx.setRemark("续费: " + room.getName() + " 延长 " + minutes + " 分钟");
                plugin.getTransactionStorage().add(tx);
            } catch (Exception e) {
                plugin.getLogger().warning("记录续费流水失败: " + e.getMessage());
            }
        }

        // 延长到期时间：将房间时长改为 有效时长+续费分钟（到期 = checkinTime + 新时长）
        room.setDurationMinutes(duration + minutes);
        plugin.getRoomStorage().saveRoom(room);
        // 重置临期警告，避免再次刷屏
        plugin.getAlertedRooms().remove(room.getId());

        plugin.log(player, "续费: " + room.getName() + " (ID: " + room.getId() + ") 延长 " + minutes + " 分钟, 花费 "
                + (plugin.getEconomyManager().isEnabled() ? plugin.getEconomyManager().format(cost) : "0"));
        player.sendMessage("§a续费成功！房间 §e" + room.getName() + "§a 已延长 §e" + minutes + " §a分钟");
        long newExpire = room.getCheckinTime() + (duration + minutes) * 60 * 1000L;
        player.sendMessage("§7新的到期时间: §e" + java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(new java.util.Date(newExpire)));
    }

    /**
     * /ht ratings [房间ID] - 查看房间评分
     */
    private void handleRatings(Player player, String[] args) {
        if (args.length >= 2) {
            HotelRoom room = plugin.getRoomStorage().getRoom(args[1]);
            if (room == null) {
                player.sendMessage("§c房间不存在");
                return;
            }
            java.util.List<com.hotels.model.Rating> list = plugin.getRatingStorage().getByRoom(room.getId());
            player.sendMessage("§6=== 房间评分: §e" + room.getName() + " §6===");
            if (list.isEmpty()) {
                player.sendMessage("§7暂无评分");
                return;
            }
            player.sendMessage("§7平均分: §e" + plugin.getRatingStorage().getAverage(room.getId()) + " §7(共 " + list.size() + " 条)");
            for (com.hotels.model.Rating r : list) {
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm")
                        .format(new java.util.Date(r.getTime()));
                player.sendMessage(" §7[" + time + "] §e" + r.getGuestName() + " §f" + r.getStars()
                        + (r.getComment() != null && !r.getComment().isEmpty() ? " §7- " + r.getComment() : ""));
            }
            return;
        }

        // 无参数：显示自己房间的评分概况
        player.sendMessage("§6=== 我的房间评分概况 ===");
        boolean any = false;
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (!room.getOwner().equals(player.getUniqueId())) continue;
            int count = plugin.getRatingStorage().countByRoom(room.getId());
            if (count == 0) continue;
            any = true;
            player.sendMessage(" §e" + room.getName() + " §7(ID: " + room.getId() + ") "
                    + "§f" + plugin.getRatingStorage().getAverage(room.getId()) + " 分 §7(" + count + " 条评分)");
        }
        if (!any) {
            player.sendMessage("§7你的房间还没有评分");
        }
    }

    /**
     * /ht claim - 提现托管收益（需在 config.yml 开启 economy.escrow-mode）
     */
    private void handleClaim(Player player) {
        if (!plugin.getConfig().getBoolean("economy.escrow-mode", false)) {
            player.sendMessage("§7当前为实时到账模式，收益已直接发放到钱包，无需提现");
            return;
        }
        if (!plugin.getEconomyManager().isEnabled()) {
            player.sendMessage("§c经济系统未启用，无法提现");
            return;
        }
        double pending = plugin.getEscrowStorage().getPending(player.getUniqueId().toString());
        if (pending <= 0) {
            player.sendMessage("§7你没有待提现的收益");
            return;
        }
        if (plugin.getEconomyManager().deposit(player, pending)) {
            plugin.getEscrowStorage().completeWithdrawal(player.getUniqueId().toString(), player.getName(), pending);
            plugin.log(player, "提现收益: " + pending + " 到钱包");
            player.sendMessage("§a提现成功！已发放 §e" + plugin.getEconomyManager().format(pending) + " §a到你的钱包");
        } else {
            player.sendMessage("§c提现失败，请查看控制台日志");
        }
    }

    private void handleTeleport(Player player) {
        HotelRoom room = plugin.getRoomStorage().getRoomByGuest(player.getUniqueId());
        if (room == null) {
            player.sendMessage("§c你没有入住任何房间");
            return;
        }

        Location loc = new Location(
                Bukkit.getWorld(room.getWorldName()),
                room.getSpawnX(), room.getSpawnY(), room.getSpawnZ(),
                room.getSpawnYaw(), room.getSpawnPitch()
        );
        com.hotels.util.SchedulerCompat.teleport(player, loc);
        plugin.log(player, "传送回已入住房间: " + room.getName());
        player.sendMessage("§a已传送到房间 §e" + room.getName());
    }

    private void handleDebug(Player player) {
        if (!player.hasPermission("hotels.admin")) {
            player.sendMessage("§c你没有管理员权限");
            return;
        }

        boolean newState = !plugin.isDebugMode();
        plugin.setDebugMode(newState);
        if (newState) {
            player.sendMessage("§a调试模式已开启");
            player.sendMessage("§7调试日志将发送到控制台和所有在线OP");
            plugin.log(player, "调试模式已开启");
        } else {
            plugin.log(player, "调试模式已关闭");
            player.sendMessage("§c调试模式已关闭");
        }
    }

    private void handleElevator(Player player) {
        if (!player.isOp() && !player.hasPermission("hotels.admin")) {
            player.sendMessage("§c你没有权限使用此指令");
            return;
        }
        boolean newState = !plugin.isElevatorEnabled();
        plugin.setElevatorEnabled(newState);
        if (newState) {
            player.sendMessage("§a铁块电梯已全局启用");
            player.sendMessage("§7所有玩家站在铁块上跳跃向上传送，潜行向下传送");
            plugin.log(player, "铁块电梯已全局启用");
        } else {
            plugin.log(player, "铁块电梯已全局禁用");
            player.sendMessage("§c铁块电梯已全局禁用");
        }
    }

    private String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return "§a空闲";
            case OCCUPIED: return "§c已入住";
            case MAINTENANCE: return "§7维护中";
            default: return "§7未知";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "wand", "create", "remove", "manage",
                    "list", "checkin", "checkout", "tp", "teleport", "info",
                    "confirm", "elevator", "checkedin", "stays", "rate", "ratings", "claim", "extend"
            ));
            if (player.hasPermission("hotels.admin") || player.isOp()) {
                completions.add("admin");
                completions.add("debug");
                completions.add("web");
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "remove":
                case "manage":
                case "info":
                case "checkin":
                case "rate":
                case "ratings":
                case "rating":
                case "extend":
                    // 补全房间 ID
                    return plugin.getRoomStorage().getAllRooms().stream()
                            .map(HotelRoom::getId)
                            .filter(id -> id.startsWith(args[1]))
                            .collect(Collectors.toList());

                case "admin":
                    return Arrays.asList("list", "tp", "remove", "reload", "panel").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "web":
                    return Arrays.asList("start", "stop", "restart", "status", "register", "changepwd", "password", "me", "info", "help").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "checkout":
                    return plugin.getRoomStorage().getAllRooms().stream()
                            .map(HotelRoom::getId)
                            .filter(id -> id.startsWith(args[1]))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("tp") || args[1].equalsIgnoreCase("remove")) {
                return plugin.getRoomStorage().getAllRooms().stream()
                        .map(HotelRoom::getId)
                        .filter(id -> id.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
