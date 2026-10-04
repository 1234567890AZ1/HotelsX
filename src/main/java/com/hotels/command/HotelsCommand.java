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
            plugin.getLang().send(sender, "common.player_only");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // 打开主菜单
            if (!player.hasPermission("hotels.use")) {
                plugin.getLang().send(player, "common.no_permission_use");
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
                    plugin.getLang().send(player, "command.create.usage");
                    return true;
                }
                handleCreate(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "remove":
            case "delete":
                if (args.length < 2) {
                    plugin.getLang().send(player, "command.remove.usage");
                    return true;
                }
                handleRemove(player, args[1]);
                break;

            case "manage":
                if (args.length < 2) {
                    plugin.getLang().send(player, "command.manage.usage");
                    return true;
                }
                handleManage(player, args[1]);
                break;

            case "list":
                handleList(player);
                break;

            case "checkin":
                if (args.length < 2) {
                    plugin.getLang().send(player, "command.checkin.usage");
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
                    plugin.getLang().send(player, "command.info.usage");
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

            case "preset":
                handlePreset(player, args);
                break;

            default:
                plugin.getLang().send(player, "common.unknown_subcommand");
                break;
        }

        return true;
    }

    private void handleWand(Player player) {
        if (!player.hasPermission("hotels.create")) {
            plugin.getLang().send(player, "common.no_permission_create");
            return;
        }

        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLang().get("command.wand.item_name"));
            meta.setLore(plugin.getLang().getList("command.wand.lore"));
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        plugin.log(player, "获取选区工具");
        plugin.getLang().send(player, "command.wand.received");
        plugin.getLang().send(player, "command.wand.hint1");
        plugin.getLang().send(player, "command.wand.hint2");
    }

    private void handleCreate(Player player, String name) {
        if (!player.hasPermission("hotels.create")) {
            plugin.getLang().send(player, "common.no_permission_create");
            return;
        }

        PlayerSelection sel = plugin.getSelectionManager().getSelection(player);
        if (!sel.hasBothPositions()) {
            plugin.getLang().send(player, "command.create.no_selection");
            return;
        }

        Location p1 = sel.getPos1();
        Location p2 = sel.getPos2();

        if (!p1.getWorld().equals(p2.getWorld())) {
            plugin.getLang().send(player, "command.create.cross_world");
            return;
        }

        if (plugin.getRoomStorage().isOverlapping(
                p1.getWorld().getName(),
                p1.getX(), p1.getY(), p1.getZ(),
                p2.getX(), p2.getY(), p2.getZ(),
                null)) {
            plugin.getLang().send(player, "command.create.overlap");
            return;
        }

        if (name.length() > 32) {
            plugin.getLang().send(player, "command.create.name_too_long");
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
        plugin.getLang().send(player, "command.create.success", "name", name);
        plugin.getLang().send(player, "command.create.id", "id", room.getId());
        plugin.getLang().send(player, "command.create.spawn_set");
        plugin.getLang().send(player, "command.create.manage_hint", "id", room.getId());
    }

    private void handleRemove(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }

        if (!room.getOwner().equals(player.getUniqueId()) && !player.hasPermission("hotels.admin")) {
            plugin.getLang().send(player, "common.not_room_owner");
            return;
        }

        plugin.getRoomStorage().removeRoom(roomId);
        plugin.log(player, "删除房间: " + room.getName() + " (ID: " + roomId + ")");
        plugin.getLang().send(player, "command.remove.success", "room", room.getName());
    }

    private void handleManage(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }

        if (!room.getOwner().equals(player.getUniqueId()) && !player.hasPermission("hotels.admin")) {
            plugin.getLang().send(player, "common.not_room_owner");
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
            plugin.getLang().send(player, "command.checkedin.none");
            return;
        }

        plugin.getLang().send(player, "command.checkedin.header");
        for (HotelRoom room : rooms) {
            String name = room.getName() != null ? room.getName() : room.getId();
            plugin.getLang().send(player, "command.checkedin.line", "room", name, "id", room.getId());
            plugin.getLang().send(player, "command.checkedin.checkout", "id", room.getId());
        }
        plugin.getLang().send(player, "command.checkedin.footer");
    }

    private void handleWeb(Player player, String[] args) {
        com.hotels.web.WebServer ws = plugin.getWebServer();
        if (ws == null) {
            plugin.getLang().send(player, "command.web.not_initialized");
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
            plugin.getLang().send(player, "command.web.no_permission");
            plugin.getLang().send(player, "command.web.player_commands");
            plugin.getLang().send(player, "command.web.help_register");
            plugin.getLang().send(player, "command.web.help_changepwd");
            plugin.getLang().send(player, "command.web.help_me");
            plugin.getLang().send(player, "command.web.help_help");
            return;
        }

        String url = plugin.getLang().get("command.web.url", "port", ws.getPort());

        switch (sub) {
            case "start":
                if (ws.isRunning()) {
                    plugin.getLang().send(player, "command.web.already_running");
                    plugin.getLang().send(player, "command.web.access", "url", url);
                } else if (ws.start()) {
                    plugin.getLang().send(player, "command.web.started");
                    plugin.getLang().send(player, "command.web.access", "url", url);
                } else {
                    plugin.getLang().send(player, "command.web.start_failed");
                }
                break;

            case "stop":
                if (ws.isRunning()) {
                    ws.stop();
                    plugin.getLang().send(player, "command.web.stopped");
                } else {
                    plugin.getLang().send(player, "command.web.not_running");
                }
                break;

            case "restart":
                if (ws.isRunning()) ws.stop();
                if (ws.start()) {
                    plugin.getLang().send(player, "command.web.restarted");
                    plugin.getLang().send(player, "command.web.access", "url", url);
                } else {
                    plugin.getLang().send(player, "command.web.restart_failed");
                }
                break;

            case "status":
                if (ws.isRunning()) {
                    plugin.getLang().send(player, "command.web.running");
                    plugin.getLang().send(player, "command.web.access_address", "url", url);
                    plugin.getLang().send(player, "command.web.port", "port", ws.getPort());
                } else {
                    plugin.getLang().send(player, "command.web.not_running_hint");
                }
                break;

            default:
                handleWebHelp(player, ws);
                break;
        }
    }

    private void handleWebHelp(Player player, com.hotels.web.WebServer ws) {
        plugin.getLang().send(player, "command.web.help_title");
        boolean isAdmin = player.hasPermission("hotels.admin") || player.isOp();
        if (isAdmin) {
            plugin.getLang().send(player, "command.web.admin_commands");
            plugin.getLang().send(player, "command.web.help_start");
            plugin.getLang().send(player, "command.web.help_stop");
            plugin.getLang().send(player, "command.web.help_restart");
            plugin.getLang().send(player, "command.web.help_status");
        }
        plugin.getLang().send(player, "command.web.account_commands");
        plugin.getLang().send(player, "command.web.help_register");
        plugin.getLang().send(player, "command.web.help_changepwd");
        plugin.getLang().send(player, "command.web.help_me");
        plugin.getLang().send(player, "command.web.help_help_full");
        if (ws.isRunning()) {
            plugin.getLang().send(player, "command.web.access_address",
                    "url", plugin.getLang().get("command.web.url", "port", ws.getPort()));
        } else {
            plugin.getLang().send(player, isAdmin
                    ? "command.web.help_tail_stopped_start"
                    : "command.web.help_tail_stopped");
        }
        plugin.getLang().send(player, "command.web.help_footer");
    }

    private void handleWebRegister(Player player, String[] args, com.hotels.web.WebServer ws) {
        if (args.length < 3) {
            plugin.getLang().send(player, "command.web.register_usage");
            return;
        }
        String username = args[2];
        if (ws.usernameExists(username)) {
            plugin.getLang().send(player, "command.web.username_exists");
            return;
        }
        // 密码通过聊天框输入，避免出现在命令日志中
        plugin.getLang().send(player, "command.web.enter_password");
        plugin.getLang().send(player, "command.web.register_hint", "player", player.getName());
        plugin.getChatInputHandler().expectInput(player, "web_register_pwd:" + username);
    }

    private void handleWebChangePwd(Player player, String[] args, com.hotels.web.WebServer ws) {
        if (ws.getUsernameByMinecraft(player.getName()) == null) {
            plugin.getLang().send(player, "command.web.not_registered");
            plugin.getLang().send(player, "command.web.register_hint2");
            return;
        }
        // 密码通过聊天框输入，避免出现在命令日志中
        plugin.getLang().send(player, "command.web.enter_new_password");
        plugin.getChatInputHandler().expectInput(player, "web_changepwd");
    }

    private void handleWebMe(Player player, com.hotels.web.WebServer ws) {
        String username = ws.getUsernameByMinecraft(player.getName());
        if (username == null) {
            plugin.getLang().send(player, "command.web.me_not_registered");
            plugin.getLang().send(player, "command.web.me_register_hint");
        } else {
            plugin.getLang().send(player, "command.web.me_registered");
            plugin.getLang().send(player, "command.web.me_account", "account", username);
            plugin.getLang().send(player, "command.web.me_linked", "player", player.getName());
            plugin.getLang().send(player, "command.web.me_changepwd");
        }
    }

    private void handleCheckin(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }
        // 有密码且无 bypass 权限时，通过聊天框输入密码（避免密码出现在命令日志中）
        if (room.hasPassword() && !player.hasPermission("hotels.bypass")) {
            plugin.getLang().send(player, "checkin.enter_password");
            plugin.getChatInputHandler().expectInput(player, "checkin_password:" + roomId);
            return;
        }
        plugin.log(player, "尝试入住房间: " + room.getName() + " (ID: " + roomId + ")");
        plugin.getCheckinHandler().attemptCheckin(player, room);
    }

    private void handleInfo(Player player, String roomId) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }

        plugin.log(player, "查看房间信息: " + room.getName() + " (ID: " + roomId + ")");
        plugin.getLang().send(player, "command.info.title");
        plugin.getLang().send(player, "command.info.name", "name", room.getName());
        plugin.getLang().send(player, "command.info.id", "id", room.getId());
        plugin.getLang().send(player, "command.info.owner", "owner", room.getOwnerName());
        plugin.getLang().send(player, "command.info.status", "status", getStatusDisplay(room.getStatus()));
        plugin.getLang().send(player, "command.info.price",
                "price", plugin.getEconomyManager().format(room.getPrice()));
        plugin.getLang().send(player, "command.info.world", "world", room.getWorldName());
        plugin.getLang().send(player, "command.info.area", "volume", room.getVolume());
        plugin.getLang().send(player, "command.info.locked", "value",
                plugin.getLang().get(room.isLocked() ? "common.yes" : "common.no"));
        plugin.getLang().send(player, "command.info.password", "value",
                plugin.getLang().get(room.hasPassword() ? "common.yes" : "common.no"));
        if (room.isOccupied()) {
            plugin.getLang().send(player, "command.info.guest", "guest", room.getCurrentGuestName());
        }
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("hotels.admin")) {
            plugin.getLang().send(player, "command.admin.no_permission");
            return;
        }

        if (args.length < 2) {
            plugin.getLang().send(player, "command.admin.title");
            plugin.getLang().send(player, "command.admin.help_panel");
            plugin.getLang().send(player, "command.admin.help_list");
            plugin.getLang().send(player, "command.admin.help_tp");
            plugin.getLang().send(player, "command.admin.help_remove");
            plugin.getLang().send(player, "command.admin.help_reload");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "panel":
                AdminPanelGUI.open(player);
                plugin.log(player, "打开管理员面板");
                break;

            case "list":
                plugin.getLang().send(player, "command.admin.list_header",
                        "count", plugin.getRoomStorage().getRoomCount());
                for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
                    plugin.getLang().send(player, "command.admin.list_line",
                            "room", room.getName(),
                            "id", room.getId(),
                            "owner", room.getOwnerName(),
                            "status", getStatusDisplay(room.getStatus()));
                }
                break;

            case "tp":
                if (args.length < 3) {
                    plugin.getLang().send(player, "command.admin.tp_usage");
                    return;
                }
                HotelRoom tpRoom = plugin.getRoomStorage().getRoom(args[2]);
                if (tpRoom == null) {
                    plugin.getLang().send(player, "common.room_not_found");
                    return;
                }
                Location tpLoc = new Location(
                        Bukkit.getWorld(tpRoom.getWorldName()),
                        tpRoom.getSpawnX(), tpRoom.getSpawnY(), tpRoom.getSpawnZ(),
                        tpRoom.getSpawnYaw(), tpRoom.getSpawnPitch()
                );
                com.hotels.util.SchedulerCompat.teleport(player, tpLoc);
                plugin.log(player, "管理员传送: 到房间 " + tpRoom.getName());
                plugin.getLang().send(player, "command.admin.tp_success", "room", tpRoom.getName());
                break;

            case "remove":
                if (args.length < 3) {
                    plugin.getLang().send(player, "command.admin.remove_usage");
                    return;
                }
                HotelRoom rmRoom = plugin.getRoomStorage().getRoom(args[2]);
                if (rmRoom == null) {
                    plugin.getLang().send(player, "common.room_not_found");
                    return;
                }
                String rmName = rmRoom.getName();
                plugin.getRoomStorage().removeRoom(args[2]);
                plugin.log(player, "管理员强制删除房间: " + rmName + " (ID: " + args[2] + ")");
                plugin.getLang().send(player, "command.admin.remove_success", "room", rmName);
                break;

            case "reload":
                plugin.reloadConfig();
                plugin.getRoomStorage().loadAll();
                plugin.log(player, "管理员重新加载配置和房间数据");
                plugin.getLang().send(player, "command.admin.reload_success");
                plugin.getLang().send(player, "command.admin.reload_hint");
                break;

            default:
                plugin.getLang().send(player, "command.admin.unknown");
                break;
        }
    }

    /**
     * /ht rate <房间ID> <分数1-5> [评语] - 给入住过的房间评分
     */
    private void handleRate(Player player, String[] args) {
        if (args.length < 3) {
            plugin.getLang().send(player, "command.rate.usage");
            return;
        }
        HotelRoom room = plugin.getRoomStorage().getRoom(args[1]);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }
        int score;
        try {
            score = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getLang().send(player, "command.rate.invalid_score");
            return;
        }
        if (score < 1 || score > 5) {
            plugin.getLang().send(player, "command.rate.invalid_score");
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
            plugin.getLang().send(player, "command.rate.never_stayed");
            return;
        }

        String comment = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        if (comment.length() > 100) {
            plugin.getLang().send(player, "command.rate.comment_too_long");
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
        plugin.getLang().send(player, wasRated ? "command.rate.updated" : "command.rate.success",
                "room", room.getName(), "stars", r.getStars());
        if (!comment.isEmpty()) {
            plugin.getLang().send(player, "command.rate.comment", "comment", comment);
        }
    }

    /**
     * /ht extend <房间ID> <续费分钟> - 客人为自己入住的房间续费延长
     */
    private void handleExtend(Player player, String[] args) {
        if (args.length < 3) {
            plugin.getLang().send(player, "command.extend.usage");
            return;
        }
        HotelRoom room = plugin.getRoomStorage().getRoom(args[1]);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }
        if (room.getCurrentGuest() == null || !room.getCurrentGuest().equals(player.getUniqueId())) {
            plugin.getLang().send(player, "command.extend.not_guest");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getLang().send(player, "command.extend.invalid_minutes");
            return;
        }
        if (minutes <= 0) {
            plugin.getLang().send(player, "command.extend.minutes_positive");
            return;
        }
        if (minutes > 1440) {
            plugin.getLang().send(player, "command.extend.max_minutes");
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
            plugin.getLang().send(player, "command.extend.unlimited");
            return;
        }

        // 续费价格 = 当前价格 × (续费分钟 / 有效时长分钟)
        double cost = room.getCurrentPrice() * ((double) minutes / duration);
        cost = Math.round(cost * 100) / 100.0;

        if (plugin.getEconomyManager().isEnabled()) {
            double balance = plugin.getEconomyManager().getBalance(player);
            if (balance < cost) {
                plugin.getLang().send(player, "command.extend.insufficient_funds",
                        "need", plugin.getEconomyManager().format(cost),
                        "bal", plugin.getEconomyManager().format(balance));
                return;
            }
            if (!plugin.getEconomyManager().withdraw(player, cost)) {
                plugin.getLang().send(player, "command.extend.withdraw_failed");
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
        plugin.getLang().send(player, "command.extend.success", "room", room.getName(), "minutes", minutes);
        long newExpire = room.getCheckinTime() + (duration + minutes) * 60 * 1000L;
        plugin.getLang().send(player, "command.extend.new_expire", "time",
                java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        .format(new java.util.Date(newExpire)));
    }

    /**
     * /ht ratings [房间ID] - 查看房间评分
     */
    private void handleRatings(Player player, String[] args) {
        if (args.length >= 2) {
            HotelRoom room = plugin.getRoomStorage().getRoom(args[1]);
            if (room == null) {
                plugin.getLang().send(player, "common.room_not_found");
                return;
            }
            java.util.List<com.hotels.model.Rating> list = plugin.getRatingStorage().getByRoom(room.getId());
            plugin.getLang().send(player, "command.ratings.title", "room", room.getName());
            if (list.isEmpty()) {
                plugin.getLang().send(player, "command.ratings.none");
                return;
            }
            plugin.getLang().send(player, "command.ratings.average",
                    "avg", plugin.getRatingStorage().getAverage(room.getId()), "count", list.size());
            for (com.hotels.model.Rating r : list) {
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm")
                        .format(new java.util.Date(r.getTime()));
                boolean hasComment = r.getComment() != null && !r.getComment().isEmpty();
                plugin.getLang().send(player, hasComment ? "command.ratings.line_comment" : "command.ratings.line",
                        "time", time, "guest", r.getGuestName(), "stars", r.getStars(),
                        "comment", hasComment ? r.getComment() : "");
            }
            return;
        }

        // 无参数：显示自己房间的评分概况
        plugin.getLang().send(player, "command.ratings.my_title");
        boolean any = false;
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (!room.getOwner().equals(player.getUniqueId())) continue;
            int count = plugin.getRatingStorage().countByRoom(room.getId());
            if (count == 0) continue;
            any = true;
            plugin.getLang().send(player, "command.ratings.my_line",
                    "room", room.getName(), "id", room.getId(),
                    "avg", plugin.getRatingStorage().getAverage(room.getId()), "count", count);
        }
        if (!any) {
            plugin.getLang().send(player, "command.ratings.my_none");
        }
    }

    /**
     * /ht claim - 提现托管收益（需在 config.yml 开启 economy.escrow-mode）
     */
    private void handleClaim(Player player) {
        if (!plugin.getConfig().getBoolean("economy.escrow-mode", false)) {
            plugin.getLang().send(player, "command.claim.realtime");
            return;
        }
        if (!plugin.getEconomyManager().isEnabled()) {
            plugin.getLang().send(player, "command.claim.economy_disabled");
            return;
        }
        double pending = plugin.getEscrowStorage().getPending(player.getUniqueId().toString());
        if (pending <= 0) {
            plugin.getLang().send(player, "command.claim.none");
            return;
        }
        if (plugin.getEconomyManager().deposit(player, pending)) {
            plugin.getEscrowStorage().completeWithdrawal(player.getUniqueId().toString(), player.getName(), pending);
            plugin.log(player, "提现收益: " + pending + " 到钱包");
            plugin.getLang().send(player, "command.claim.success",
                    "amount", plugin.getEconomyManager().format(pending));
        } else {
            plugin.getLang().send(player, "command.claim.failed");
        }
    }

    private void handleTeleport(Player player) {
        HotelRoom room = plugin.getRoomStorage().getRoomByGuest(player.getUniqueId());
        if (room == null) {
            plugin.getLang().send(player, "checkin.not_checked_in");
            return;
        }

        Location loc = new Location(
                Bukkit.getWorld(room.getWorldName()),
                room.getSpawnX(), room.getSpawnY(), room.getSpawnZ(),
                room.getSpawnYaw(), room.getSpawnPitch()
        );
        com.hotels.util.SchedulerCompat.teleport(player, loc);
        plugin.log(player, "传送回已入住房间: " + room.getName());
        plugin.getLang().send(player, "command.teleport.success", "room", room.getName());
    }

    private void handleDebug(Player player) {
        if (!player.hasPermission("hotels.admin")) {
            plugin.getLang().send(player, "command.admin.no_permission");
            return;
        }

        boolean newState = !plugin.isDebugMode();
        plugin.setDebugMode(newState);
        if (newState) {
            plugin.getLang().send(player, "command.debug.enabled");
            plugin.getLang().send(player, "command.debug.enabled_hint");
            plugin.log(player, "调试模式已开启");
        } else {
            plugin.log(player, "调试模式已关闭");
            plugin.getLang().send(player, "command.debug.disabled");
        }
    }

    private void handleElevator(Player player) {
        if (!player.isOp() && !player.hasPermission("hotels.admin")) {
            plugin.getLang().send(player, "command.elevator.no_permission");
            return;
        }
        boolean newState = !plugin.isElevatorEnabled();
        plugin.setElevatorEnabled(newState);
        if (newState) {
            plugin.getLang().send(player, "command.elevator.enabled");
            plugin.getLang().send(player, "command.elevator.enabled_hint");
            plugin.log(player, "铁块电梯已全局启用");
        } else {
            plugin.log(player, "铁块电梯已全局禁用");
            plugin.getLang().send(player, "command.elevator.disabled");
        }
    }

    private String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return plugin.getLang().get("command.status.available");
            case OCCUPIED: return plugin.getLang().get("command.status.occupied");
            case MAINTENANCE: return plugin.getLang().get("command.status.maintenance");
            default: return plugin.getLang().get("command.status.unknown");
        }
    }

    /**
     * /ht preset - 房间装修预设管理
     * 子命令: list / save <名称> / apply <名称> [房间ID] / delete <名称>
     * 仅当房间尺寸与预设完全一致时才能应用
     */
    private void handlePreset(Player player, String[] args) {
        boolean admin = player.hasPermission("hotels.admin") || player.isOp();

        if (args.length < 2) {
            sendPresetHelp(player, admin);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list": {
                List<com.hotels.model.RoomPreset> presets = plugin.getPresetStorage().getAllPresets();
                if (presets.isEmpty()) {
                    plugin.getLang().send(player, "command.preset.none");
                    return;
                }
                plugin.getLang().send(player, "command.preset.list_title", "count", presets.size());
                for (com.hotels.model.RoomPreset p : presets) {
                    if (p.getRotation() > 0) {
                        plugin.getLang().send(player, "command.preset.list_line_rot",
                                "name", p.getName(), "size", p.getSizeDisplay(),
                                "rotation", p.getRotation(), "world", p.getWorld());
                    } else {
                        plugin.getLang().send(player, "command.preset.list_line",
                                "name", p.getName(), "size", p.getSizeDisplay(), "world", p.getWorld());
                    }
                }
                plugin.getLang().send(player, "command.preset.list_hint");
                break;
            }

            case "save": {
                if (!admin) {
                    plugin.getLang().send(player, "command.preset.save_no_permission");
                    return;
                }
                if (args.length < 3) {
                    plugin.getLang().send(player, "command.preset.save_usage");
                    return;
                }
                String name = args[2];
                if (!name.matches("[\\w\\u4e00-\\u9fa5-]{1,24}")) {
                    plugin.getLang().send(player, "command.preset.invalid_name");
                    return;
                }
                com.hotels.model.HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(player.getLocation());
                if (room == null) {
                    plugin.getLang().send(player, "command.preset.not_in_room");
                    return;
                }
                if (!room.getOwner().equals(player.getUniqueId()) && !admin) {
                    plugin.getLang().send(player, "common.not_room_owner");
                    return;
                }
                plugin.getPresetManager().savePreset(player, room, name);
                break;
            }

            case "apply": {
                if (args.length < 3) {
                    plugin.getLang().send(player, "command.preset.apply_usage");
                    return;
                }
                String presetName = args[2];
                com.hotels.model.RoomPreset preset = plugin.getPresetStorage().getPreset(presetName);
                if (preset == null) {
                    plugin.getLang().send(player, "command.preset.not_found", "name", presetName);
                    return;
                }

                // 解析可选参数：旋转角度或房间ID
                int rotation = preset.getRotation(); // 默认使用预设保存时的旋转
                String roomIdArg = null;

                if (args.length >= 4) {
                    // 尝试将 args[3] 解析为旋转角度
                    boolean isRotation = false;
                    try {
                        int rot = Integer.parseInt(args[3]);
                        if (rot % 90 == 0 && rot >= 0 && rot <= 270) {
                            rotation = rot;
                            isRotation = true;
                        }
                    } catch (NumberFormatException ignored) {}

                    if (isRotation) {
                        // args[4] 是房间ID（如果有）
                        if (args.length >= 5) {
                            roomIdArg = args[4];
                        }
                    } else {
                        // args[3] 直接是房间ID
                        roomIdArg = args[3];
                    }
                }

                // 使用副本应用旋转，避免修改存储中的原预设
                preset = preset.copyWithRotation(rotation);

                // 支持 confirm 参数：跳过"房间已有装修"的冲突提示，直接覆盖
                boolean confirm = false;
                for (int i = 3; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("confirm")) {
                        confirm = true;
                        break;
                    }
                }

                com.hotels.model.HotelRoom room;
                if (roomIdArg != null) {
                    room = plugin.getRoomStorage().getRoom(roomIdArg);
                    if (room == null) {
                        plugin.getLang().send(player, "common.room_not_found");
                        return;
                    }
                    if (!room.getOwner().equals(player.getUniqueId()) && !admin) {
                        plugin.getLang().send(player, "common.not_room_owner");
                        return;
                    }
                } else {
                    room = plugin.getRoomStorage().getRoomAtLocation(player.getLocation());
                    if (room == null) {
                        plugin.getLang().send(player, "command.preset.apply_not_in_room");
                        return;
                    }
                    if (!room.getOwner().equals(player.getUniqueId()) && !admin) {
                        plugin.getLang().send(player, "common.not_room_owner");
                        return;
                    }
                }
                plugin.getPresetManager().applyPreset(player, room, preset, confirm);
                break;
            }

            case "undo": {
                // 回滚最近一次应用到房间的装修预设
                com.hotels.model.HotelRoom room;
                if (args.length >= 3) {
                    room = plugin.getRoomStorage().getRoom(args[2]);
                    if (room == null) {
                        plugin.getLang().send(player, "common.room_not_found");
                        return;
                    }
                    if (!room.getOwner().equals(player.getUniqueId()) && !admin) {
                        plugin.getLang().send(player, "common.not_room_owner");
                        return;
                    }
                } else {
                    room = plugin.getRoomStorage().getRoomAtLocation(player.getLocation());
                    if (room == null) {
                        plugin.getLang().send(player, "command.preset.undo_not_in_room");
                        return;
                    }
                    if (!room.getOwner().equals(player.getUniqueId()) && !admin) {
                        plugin.getLang().send(player, "common.not_room_owner");
                        return;
                    }
                }
                plugin.getPresetManager().undoPreset(player, room);
                break;
            }

            case "delete": {
                if (!admin) {
                    plugin.getLang().send(player, "command.preset.delete_no_permission");
                    return;
                }
                if (args.length < 3) {
                    plugin.getLang().send(player, "command.preset.delete_usage");
                    return;
                }
                if (plugin.getPresetStorage().deletePreset(args[2])) {
                    plugin.log(player, "删除装修预设: " + args[2]);
                    plugin.getLang().send(player, "command.preset.deleted", "name", args[2]);
                } else {
                    plugin.getLang().send(player, "command.preset.not_found", "name", args[2]);
                }
                break;
            }

            default:
                sendPresetHelp(player, admin);
                break;
        }
    }

    private void sendPresetHelp(Player player, boolean admin) {
        plugin.getLang().send(player, "command.preset.help_title");
        plugin.getLang().send(player, "command.preset.help_desc");
        if (admin) {
            plugin.getLang().send(player, "command.preset.help_save");
            plugin.getLang().send(player, "command.preset.help_delete");
        }
        plugin.getLang().send(player, "command.preset.help_list");
        plugin.getLang().send(player, "command.preset.help_apply");
        plugin.getLang().send(player, "command.preset.help_undo");
        plugin.getLang().send(player, "command.preset.help_hint1");
        plugin.getLang().send(player, "command.preset.help_hint2");
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
                completions.add("preset");
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

                case "preset":
                    if (args[1].equalsIgnoreCase("apply")) {
                        // 补全预设名称或旋转角度
                        List<String> completions = new ArrayList<>(Arrays.asList("0", "90", "180", "270", "confirm"));
                        completions.addAll(plugin.getPresetStorage().getAllPresets().stream()
                                .map(com.hotels.model.RoomPreset::getName)
                                .collect(Collectors.toList()));
                        return completions.stream()
                                .filter(s -> s.startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    if (args[1].equalsIgnoreCase("undo")) {
                        // 补全房间 ID
                        return plugin.getRoomStorage().getAllRooms().stream()
                                .map(HotelRoom::getId)
                                .filter(id -> id.startsWith(args[2]))
                                .collect(Collectors.toList());
                    }
                    return Arrays.asList("list", "save", "apply", "undo", "delete").stream()
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
