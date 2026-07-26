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
                    player.sendMessage("§c用法: /ht checkin <房间ID> [密码]");
                    return true;
                }
                String password = args.length >= 3 ? args[2] : null;
                handleCheckin(player, args[1], password);
                break;

            case "checkout":
                plugin.log(player, "尝试退房");
                plugin.getCheckinHandler().checkout(player);
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

    private void handleCheckin(Player player, String roomId, String password) {
        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            player.sendMessage("§c房间不存在");
            return;
        }

        // 检查密码
        if (room.hasPassword() && !player.hasPermission("hotels.bypass")) {
            if (password == null || !password.equals(room.getPassword())) {
                player.sendMessage("§c密码错误");
                return;
            }
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
                player.teleport(tpLoc);
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
                plugin.log(player, "管理员重载配置");
                player.sendMessage("§a配置已重载");
                break;

            default:
                player.sendMessage("§c未知管理命令");
                break;
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
        player.teleport(loc);
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
                    "list", "checkin", "checkout", "tp", "teleport", "info"
            ));
            if (player.hasPermission("hotels.admin")) {
                completions.add("admin");
                completions.add("debug");
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
                    // 补全房间 ID
                    return plugin.getRoomStorage().getAllRooms().stream()
                            .map(HotelRoom::getId)
                            .filter(id -> id.startsWith(args[1]))
                            .collect(Collectors.toList());

                case "admin":
                    return Arrays.asList("list", "tp", "remove", "reload").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
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
