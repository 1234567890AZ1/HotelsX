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
package com.hotels.listener;

import com.hotels.HotelsPlugin;
import com.hotels.gui.*;
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public class GUIListener implements Listener {

    private final HotelsPlugin plugin;

    public GUIListener(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();

        if (!(inv.getHolder() instanceof GUIHolder)) return;
        GUIHolder holder = (GUIHolder) inv.getHolder();
        String guiName = holder.getGuiName();

        event.setCancelled(true);

        if (guiName.equals(MainMenuGUI.GUI_NAME)) {
            handleMainMenuClick(player, event.getSlot());
        } else if (guiName.startsWith(MyRoomsGUI.GUI_NAME)) {
            handleMyRoomsClick(player, event, guiName);
        } else if (guiName.startsWith(BrowseRoomsGUI.GUI_NAME)) {
            handleBrowseRoomsClick(player, event, guiName);
        } else if (guiName.equals("tag_filter")) {
            handleTagFilterClick(player, event);
        } else if (guiName.equals(RoomManageGUI.GUI_NAME)) {
            handleRoomManageClick(player, event, holder);
        } else if (guiName.equals("ranking")) {
            handleRankingClick(player, event);
        } else if (guiName.equals(TagSelectGUI.GUI_NAME)) {
            handleTagSelectClick(player, event, holder);
        } else if (guiName.startsWith(CollectionGUI.GUI_NAME)) {
            handleCollectionClick(player, event, holder, guiName);
        } else if (guiName.startsWith(AdminPanelGUI.GUI_NAME)) {
            handleAdminPanelClick(player, event, guiName);
        } else if (guiName.equals(ElevatorGUI.GUI_NAME)) {
            handleElevatorSelectClick(player, event, holder);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 11:
                plugin.log(player, "打开我的房间列表");
                MyRoomsGUI.open(player, plugin);
                break;
            case 13:
                plugin.log(player, "打开浏览房间界面");
                BrowseRoomsGUI.open(player, plugin);
                break;
            case 15:
                if (!player.hasPermission("hotels.create")) {
                    player.sendMessage("§c你没有权限创建房间");
                    return;
                }
                plugin.log(player, "查看创建房间说明");
                player.closeInventory();
                player.sendMessage("§e=== 创建房间 ===");
                player.sendMessage("§71. 手持木斧 §e//wand §7选择区域的两个对角点");
                player.sendMessage("§72. 站在传送点位置输入 §e/ht create <房间名> §7创建");
                player.sendMessage("§73. 创建后可用 §e/ht manage <ID> §7管理房间");
                break;
            case 29:
                plugin.log(player, "打开合集管理界面");
                CollectionGUI.openManage(player);
                break;
            case 31:
                plugin.log(player, "查看帮助信息");
                sendHelp(player);
                break;
            case 33:
                plugin.log(player, "打开排行榜");
                MainMenuGUI.openRanking(player);
                break;
        }
    }

    private void handleRankingClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();

        if (slot == size - 1) {
            plugin.log(player, "从排行榜返回主菜单");
            MainMenuGUI.open(player);
        }
    }

    private void handleCollectionClick(Player player, InventoryClickEvent event, GUIHolder holder, String guiName) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();
        ItemStack item = event.getCurrentItem();

        if (guiName.equals(CollectionGUI.GUI_NAME + ":manage")) {
            if (slot == 22) {
                plugin.log(player, "从合集管理返回主菜单");
                MainMenuGUI.open(player);
                return;
            }
        } else if (guiName.equals(CollectionGUI.GUI_NAME + ":my")) {
            if (slot == 4) {
                player.closeInventory();
                player.sendMessage("§e请输入要搜索的酒店名称:");
                plugin.getChatInputHandler().expectInput(player, "mycollection_search");
                return;
            }
            if (slot == 49) {
                plugin.log(player, "从我的合集返回合集管理");
                CollectionGUI.openManage(player);
                return;
            }
        } else if (slot == 49) {
            if (guiName.startsWith(CollectionGUI.GUI_NAME + ":browse_all")) {
                plugin.log(player, "从浏览合集返回合集管理");
                CollectionGUI.openManage(player);
            } else if (guiName.equals(CollectionGUI.GUI_NAME + ":my")) {
                plugin.log(player, "从我的合集返回合集管理");
                CollectionGUI.openManage(player);
          } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":manage_collection")) {  
                plugin.log(player, "从管理合集返回我的合集");
                CollectionGUI.openMyCollections(player, plugin);
            } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":collection_rooms")) {
                plugin.log(player, "从合集房间返回浏览合集");
                CollectionGUI.openBrowseAll(player, plugin);
            } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":admin_manage")) {
                RoomCollection col = holder.getData(RoomCollection.class);
                if (col != null) {
                    plugin.log(player, "从管理员管理返回合集管理: " + col.getName());
                    CollectionGUI.openManageCollection(player, col, plugin);
                }
            }
            return;
        } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":manage_collection")) {
            RoomCollection col = holder.getData(RoomCollection.class);
            if (col != null) {
                int page = 0;
                try {
                    String[] parts = guiName.split(":");
                    if (parts.length >= 3) {
                        page = Integer.parseInt(parts[2]);
                    }
                } catch (NumberFormatException e) {
                    page = 0;
                }

                if (slot == 3) {
                    player.closeInventory();
                    player.sendMessage("§e请输入要搜索的房间名称:");
                    plugin.getChatInputHandler().expectInput(player, "managecollection_search:" + col.getId());
                    return;
                }

                if (slot == 45) {
                    plugin.log(player, "合集管理上一页: " + col.getName() + " (页 " + (page) + " → " + (page - 1) + ")");
                    CollectionGUI.openManageCollection(player, col, plugin, page - 1);
                    return;
                } else if (slot == 53) {
                    plugin.log(player, "合集管理下一页: " + col.getName() + " (页 " + (page) + " → " + (page + 1) + ")");
                    CollectionGUI.openManageCollection(player, col, plugin, page + 1);
                    return;
                }
            }
        } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":browse_all")) {
            int page = 0;
            try {
                String[] parts = guiName.split(":");
                if (parts.length >= 3) {
                    page = Integer.parseInt(parts[2]);
                }
            } catch (NumberFormatException e) {
                page = 0;
            }

            if (slot == 4) {
                player.closeInventory();
                player.sendMessage("§e请输入要搜索的酒店名称:");
                plugin.getChatInputHandler().expectInput(player, "collection_search:" + page);
                return;
            }

            if (slot == 45) {
                plugin.log(player, "浏览合集上一页 (页 " + (page) + " → " + (page - 1) + ")");
                CollectionGUI.openBrowseAll(player, plugin, page - 1);
                return;
            } else if (slot == 53) {
                plugin.log(player, "浏览合集下一页 (页 " + (page) + " → " + (page + 1) + ")");
                CollectionGUI.openBrowseAll(player, plugin, page + 1);
                return;
            }
        }

        if (guiName.equals(CollectionGUI.GUI_NAME + ":manage")) {
            switch (slot) {
                case 11:
                    plugin.log(player, "打开浏览所有合集");
                    CollectionGUI.openBrowseAll(player, plugin);
                    break;
                case 13:
                    plugin.log(player, "开始创建合集");
                    player.closeInventory();
                    player.sendMessage("§e请输入新酒店合集的名称:");
                    plugin.getChatInputHandler().expectInput(player, "createcollection");
                    break;
                case 15:
                    plugin.log(player, "打开我的合集列表");
                    CollectionGUI.openMyCollections(player, plugin);
                    break;
            }
        } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":browse_all")) {
            if (item == null || !item.hasItemMeta()) return;
            String colName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            for (RoomCollection col : plugin.getRoomStorage().getAllCollections()) {
                if (col.getName().equals(colName)) {
                    plugin.log(player, "打开合集房间列表: " + col.getName());
                    CollectionGUI.openCollectionRooms(player, col, plugin);
                    return;
                }
            }
        } else if (guiName.equals(CollectionGUI.GUI_NAME + ":my")) {
            if (item == null || !item.hasItemMeta()) return;
            String colName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            for (RoomCollection col : plugin.getRoomStorage().getCollectionsByOwner(player.getUniqueId())) {
                if (col.getName().equals(colName)) {
                    if (event.isLeftClick()) {
                        plugin.log(player, "打开合集管理: " + col.getName());
                        CollectionGUI.openManageCollection(player, col, plugin);
                    } else if (event.isRightClick()) {
                        plugin.log(player, "准备删除合集: " + col.getName());
                        player.closeInventory();
                        player.sendMessage("§c确认删除合集 §e" + col.getName() + "§c？在聊天框输入 §e确认 §c或 §e取消");
                        plugin.getChatInputHandler().expectInput(player, "deletecollection:" + col.getId());
                    }
                    return;
                }
            }
        } else if (guiName.startsWith(CollectionGUI.GUI_NAME + ":")) {
            RoomCollection col = holder.getData(RoomCollection.class);
            if (col == null) return;

            if (guiName.contains(":admin_manage")) {
                if (item == null || !item.hasItemMeta()) return;
                String playerName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                Player target = Bukkit.getPlayer(playerName);
                if (target == null) return;

                if (!col.getOwner().equals(player.getUniqueId())) {
                    player.sendMessage("§c只有房主可以管理管理员");
                    return;
                }
                if (col.getOwner().equals(target.getUniqueId())) {
                    player.sendMessage("§c不能操作房主");
                    return;
                }
                if (col.getAdmins().contains(target.getUniqueId().toString())) {
                    col.removeAdmin(target.getUniqueId());
                    plugin.log(player, "移除合集管理员: " + target.getName() + " 从 " + col.getName());
                    player.sendMessage("§c已移除管理员 §e" + target.getName());
                } else {
                    col.addAdmin(target.getUniqueId());
                    plugin.log(player, "添加合集管理员: " + target.getName() + " 到 " + col.getName());
                    player.sendMessage("§a已添加 §e" + target.getName() + " §a为管理员");
                }
                plugin.getRoomStorage().saveCollection(col);
                CollectionGUI.openAdminManage(player, col, plugin);
                return;
            }

            if (item == null || !item.hasItemMeta()) return;

            String itemName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if (itemName.contains("管理员管理")) {
                if (!col.canManage(player.getUniqueId())) {
                    player.sendMessage("§c你没有权限管理此合集");
                    return;
                }
                plugin.log(player, "打开合集管理员管理: " + col.getName());
                CollectionGUI.openAdminManage(player, col, plugin);
                return;
            }

            if (itemName.contains("一键定价")) {
                if (!col.canManage(player.getUniqueId())) {
                    player.sendMessage("§c你没有权限管理此合集");
                    return;
                }
                plugin.log(player, "开始合集一键定价: " + col.getName());
                player.closeInventory();
                player.sendMessage("§e请输入合集内所有房间的统一价格（数字）:");
                plugin.getChatInputHandler().expectInput(player, "setcollectionprice:" + col.getId());
                return;
            }

            String roomName = itemName;

            boolean isManageMode = guiName.contains(":manage_collection");

            if (isManageMode) {
                int page = 0;
                try {
                    String[] parts = guiName.split(":");
                    if (parts.length >= 3) {
                        page = Integer.parseInt(parts[2]);
                    }
                } catch (NumberFormatException e) {
                    page = 0;
                }

                for (HotelRoom room : plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId())) {
                    if (room.getName().equals(roomName)) {
                        if (col.getRoomIds().contains(room.getId())) {
                            col.removeRoom(room.getId());
                            plugin.log(player, "从合集移出房间: " + room.getName() + " 从 " + col.getName());
                            player.sendMessage("§c已从合集移出房间 §e" + room.getName());
                        } else {
                            col.addRoom(room.getId());
                            plugin.log(player, "添加房间到合集: " + room.getName() + " 到 " + col.getName());
                            player.sendMessage("§a已添加房间 §e" + room.getName() + " §a到合集");
                        }
                        plugin.getRoomStorage().saveCollection(col);
                        CollectionGUI.openManageCollection(player, col, plugin, page);
                        return;
                    }
                }
            }

            for (HotelRoom room : plugin.getRoomStorage().getCollectionRooms(col.getId())) {
                if (room.getName().equals(roomName)) {
                    player.closeInventory();
                    plugin.getCheckinHandler().attemptCheckin(player, room);
                    return;
                }
            }
        }
    }

    private void handleMyRoomsClick(Player player, InventoryClickEvent event, String guiName) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();

        if (slot == 45) {
            plugin.log(player, "从我的房间返回主菜单");
            MainMenuGUI.open(player);
            return;
        }

        if (slot == 47) {
            int page = Integer.parseInt(guiName.split(":")[1]);
            plugin.log(player, "我的房间上一页 (页 " + (page) + " → " + (page - 1) + ")");
            MyRoomsGUI.open(player, plugin, page - 1);
            return;
        }

        if (slot == 51) {
            int page = Integer.parseInt(guiName.split(":")[1]);
            plugin.log(player, "我的房间下一页 (页 " + (page) + " → " + (page + 1) + ")");
            MyRoomsGUI.open(player, plugin, page + 1);
            return;
        }

        if (slot >= 45) return;

        if (slot == 5) {
            player.closeInventory();
            player.sendMessage("§e请输入要搜索的房间名称或ID:");
            plugin.getChatInputHandler().expectInput(player, "myrooms_search");
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.startsWith("§e")) return;

        String roomName = ChatColor.stripColor(displayName);

        for (HotelRoom room : plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId())) {
            if (room.getName().equals(roomName)) {
                if (event.isLeftClick()) {
                    plugin.log(player, "打开房间管理: " + room.getName());
                    RoomManageGUI.open(player, room, plugin);
                } else if (event.isRightClick()) {
                    plugin.log(player, "准备删除房间: " + room.getName());
                    player.closeInventory();
                    player.sendMessage("§c确认删除房间 §e" + room.getName() + "§c？在聊天框输入 §e确认 §c或 §e取消");
                    plugin.getChatInputHandler().expectInput(player, "deleteroom:" + room.getId());
                }
                return;
            }
        }
    }

    private void handleBrowseRoomsClick(Player player, InventoryClickEvent event, String guiName) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();
        ItemStack item = event.getCurrentItem();

        if (slot == 45) {
            plugin.log(player, "从浏览房间返回主菜单");
            MainMenuGUI.open(player);
            return;
        }

        if (slot == 47) {
            String[] parts = guiName.split(":");
            String tag = parts.length > 1 ? parts[1] : null;
            int page = Integer.parseInt(parts[parts.length - 1]);
            List<HotelRoom> rooms;
            if (tag != null && !tag.equals("all")) {
                rooms = plugin.getRoomStorage().getAvailableRooms().stream()
                        .filter(r -> r.hasTag(tag))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                rooms = plugin.getRoomStorage().getAvailableRooms();
            }
            plugin.log(player, "浏览房间上一页 (页 " + (page) + " → " + (page - 1) + ")");
            BrowseRoomsGUI.openWithRooms(player, rooms, plugin, tag, page - 1);
            return;
        }

        if (slot == 51) {
            String[] parts = guiName.split(":");
            String tag = parts.length > 1 ? parts[1] : null;
            int page = Integer.parseInt(parts[parts.length - 1]);
            List<HotelRoom> rooms;
            if (tag != null && !tag.equals("all")) {
                rooms = plugin.getRoomStorage().getAvailableRooms().stream()
                        .filter(r -> r.hasTag(tag))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                rooms = plugin.getRoomStorage().getAvailableRooms();
            }
            plugin.log(player, "浏览房间下一页 (页 " + (page) + " → " + (page + 1) + ")");
            BrowseRoomsGUI.openWithRooms(player, rooms, plugin, tag, page + 1);
            return;
        }

        if (slot >= 45) return;

        if (slot == 4) {
            plugin.log(player, "打开标签筛选界面");
            BrowseRoomsGUI.openTagFilter(player, plugin);
            return;
        }

        if (slot == 5) {
            player.closeInventory();
            player.sendMessage("§e请输入要搜索的房间名称或ID:");
            plugin.getChatInputHandler().expectInput(player, "browse_search");
            return;
        }

        if (item == null || !item.hasItemMeta()) return;

        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.startsWith("§e")) return;

        String roomName = ChatColor.stripColor(displayName);

        for (HotelRoom room : plugin.getRoomStorage().getAvailableRooms()) {
            if (room.getName().equals(roomName)) {
                plugin.log(player, "从浏览房间入住: " + room.getName());
                player.closeInventory();
                plugin.getCheckinHandler().attemptCheckin(player, room);
                return;
            }
        }
    }

    private void handleTagFilterClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();
        ItemStack item = event.getCurrentItem();

        if (slot == size - 1) {
            plugin.log(player, "从标签筛选返回主菜单");
            MainMenuGUI.open(player);
            return;
        }

        if (slot == 4) {
            plugin.log(player, "清除标签筛选");
            BrowseRoomsGUI.open(player, plugin);
            return;
        }

        if (item == null || !item.hasItemMeta()) return;

        String tagName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        plugin.log(player, "按标签筛选房间: " + tagName);
        BrowseRoomsGUI.openByTag(player, tagName, plugin);
    }

    private void handleRoomManageClick(Player player, InventoryClickEvent event, GUIHolder holder) {
        int slot = event.getSlot();

        HotelRoom targetRoom = holder.getData(HotelRoom.class);
        if (targetRoom == null) {
            player.sendMessage("§c房间数据异常");
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 10:
                player.closeInventory();
                plugin.log(player, "准备设置房间价格: " + targetRoom.getName());
                player.sendMessage("§e请输入新价格（数字）:");
                plugin.getChatInputHandler().expectInput(player, "setprice:" + targetRoom.getId());
                break;
            case 11:
                player.closeInventory();
                if (targetRoom.hasActiveDiscount()) {
                    targetRoom.clearDiscount();
                    plugin.getRoomStorage().saveRoom(targetRoom);
                    plugin.log(player, "取消房间折扣: " + targetRoom.getName());
                    player.sendMessage("§a已取消折扣");
                    RoomManageGUI.open(player, targetRoom, plugin);
                } else {
                    plugin.log(player, "准备设置房间折扣: " + targetRoom.getName());
                    player.sendMessage("§e请输入折扣价（数字）:");
                    plugin.getChatInputHandler().expectInput(player, "setdiscountprice:" + targetRoom.getId());
                }
                break;
            case 12:
                plugin.log(player, "打开标签选择界面: " + targetRoom.getName());
                TagSelectGUI.open(player, targetRoom, plugin);
                break;
            case 13:
                player.closeInventory();
                if (targetRoom.hasPassword()) {
                    targetRoom.setPassword(null);
                    plugin.getRoomStorage().saveRoom(targetRoom);
                    plugin.log(player, "清除房间密码: " + targetRoom.getName());
                    player.sendMessage("§a已清除房间密码");
                } else {
                    plugin.log(player, "准备设置房间密码: " + targetRoom.getName());
                    player.sendMessage("§e请输入房间密码:");
                    plugin.getChatInputHandler().expectInput(player, "setpassword:" + targetRoom.getId());
                }
                break;
            case 14:
                targetRoom.setLocked(!targetRoom.isLocked());
                plugin.getRoomStorage().saveRoom(targetRoom);
                plugin.log(player, "房间" + (targetRoom.isLocked() ? "锁定" : "解锁") + ": " + targetRoom.getName());
                player.sendMessage("§a房间已" + (targetRoom.isLocked() ? "锁定" : "解锁"));
                RoomManageGUI.open(player, targetRoom, plugin);
                break;
            case 15:
                switch (targetRoom.getStatus()) {
                    case AVAILABLE:
                        targetRoom.setStatus(HotelRoom.RoomStatus.MAINTENANCE);
                        break;
                    case MAINTENANCE:
                        targetRoom.setStatus(HotelRoom.RoomStatus.AVAILABLE);
                        break;
                    case OCCUPIED:
                        player.sendMessage("§c房间已入住，无法切换状态");
                        return;
                }
                plugin.getRoomStorage().saveRoom(targetRoom);
                plugin.log(player, "房间状态更新为: " + targetRoom.getStatus() + " (" + targetRoom.getName() + ")");
                player.sendMessage("§a房间状态已更新");
                RoomManageGUI.open(player, targetRoom, plugin);
                break;
            case 21:
                Location loc = new Location(
                        Bukkit.getWorld(targetRoom.getWorldName()),
                        targetRoom.getSpawnX(), targetRoom.getSpawnY(), targetRoom.getSpawnZ(),
                        targetRoom.getSpawnYaw(), targetRoom.getSpawnPitch()
                );
                com.hotels.util.SchedulerCompat.teleport(player, loc);
                plugin.log(player, "传送到房间: " + targetRoom.getName());
                player.sendMessage("§a已传送到房间");
                break;
            case 23:
                if (targetRoom.isOccupied()) {
                    Player guest = plugin.getServer().getPlayer(targetRoom.getCurrentGuest());
                    String guestName = guest != null ? guest.getName() : targetRoom.getCurrentGuestName();
                    if (guest != null && guest.isOnline()) {
                        guest.sendMessage("§c你被房主从房间 " + targetRoom.getName() + " 中踢出");
                    }
                    targetRoom.setCurrentGuest(null);
                    targetRoom.setCurrentGuestName(null);
                    targetRoom.setStatus(HotelRoom.RoomStatus.AVAILABLE);
                    targetRoom.setCheckinTime(0);
                    plugin.getRoomStorage().saveRoom(targetRoom);
                    plugin.log(player, "踢出客人: " + guestName + " 从房间 " + targetRoom.getName());
                    player.sendMessage("§a已踢出客人");
                    RoomManageGUI.open(player, targetRoom, plugin);
                }
                break;
            case 26:
                plugin.log(player, "从房间管理返回我的房间");
                MyRoomsGUI.open(player, plugin);
                break;
        }
    }

    private void handleTagSelectClick(Player player, InventoryClickEvent event, GUIHolder holder) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();
        ItemStack item = event.getCurrentItem();

        if (slot == size - 1) {
            HotelRoom room = holder.getData(HotelRoom.class);
            if (room != null) {
                plugin.log(player, "从标签选择返回房间管理: " + room.getName());
                RoomManageGUI.open(player, room, plugin);
            }
            return;
        }

        if (item == null || !item.hasItemMeta()) return;

        String tagName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        HotelRoom room = holder.getData(HotelRoom.class);
        if (room != null) {
            if (room.hasTag(tagName)) {
                room.removeTag(tagName);
                plugin.log(player, "移除房间标签: " + tagName + " 从 " + room.getName());
                player.sendMessage("§c已移除标签 §e" + tagName);
            } else {
                if (room.getTags().size() >= 3) {
                    player.sendMessage("§c标签已达上限（最多3个）");
                } else {
                    room.addTag(tagName);
                    plugin.log(player, "添加房间标签: " + tagName + " 到 " + room.getName());
                    player.sendMessage("§a已添加标签 §e" + tagName);
                }
            }
            plugin.getRoomStorage().saveRoom(room);
            TagSelectGUI.open(player, room, plugin);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== 酒店系统帮助 ===");
        player.sendMessage("§e/ht §7- 打开酒店菜单");
        player.sendMessage("§e/ht create <名称> §7- 创建房间（需先选点）");
        player.sendMessage("§e/ht manage <ID> §7- 管理房间");
        player.sendMessage("§e/ht remove <ID> §7- 删除房间");
        player.sendMessage("§e/ht list §7- 查看你的房间列表");
        player.sendMessage("§e/ht tp §7- 传送回已入住的房间");

        player.sendMessage("§e//wand §7- 获取选区工具（木斧）");
        player.sendMessage("§e/ht admin §7- 管理命令");
    }

    private void handleAdminPanelClick(Player player, InventoryClickEvent event, String guiName) {
        int slot = event.getSlot();
        ItemStack item = event.getCurrentItem();

        if (!player.hasPermission("hotels.admin")) {
            player.sendMessage("§c你没有管理员权限");
            player.closeInventory();
            return;
        }

        int page = 0;
        try {
            String[] parts = guiName.split(":");
            if (parts.length >= 2) {
                page = Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            page = 0;
        }

        if (slot == 45) {
            plugin.log(player, "管理员后台上一页 (页 " + (page) + " → " + (page - 1) + ")");
            AdminPanelGUI.open(player, page - 1);
            return;
        }

        if (slot == 53) {
            plugin.log(player, "管理员后台下一页 (页 " + (page) + " → " + (page + 1) + ")");
            AdminPanelGUI.open(player, page + 1);
            return;
        }

        if (slot == 47) {
            if (event.isShiftClick()) {
                player.closeInventory();
                player.sendMessage("§c确认删除所有房间？在聊天框输入 §e确认 §c或 §e取消");
                plugin.getChatInputHandler().expectInput(player, "admin_deleteall");
            }
            return;
        }

        if (slot == 51) {
            plugin.getRoomStorage().loadAll();
            plugin.log(player, "管理员重新加载数据");
            player.sendMessage("§a数据已重新加载");
            AdminPanelGUI.open(player, page);
            return;
        }

        if (slot == 48) {
            player.closeInventory();
            player.sendMessage("§e请输入要搜索的房间名称或ID:");
            plugin.getChatInputHandler().expectInput(player, "admin_search:" + page);
            return;
        }

        if (slot == 49) {
            plugin.log(player, "从管理员后台返回主菜单");
            MainMenuGUI.open(player);
            return;
        }

        if (slot < 9 || slot >= 45) return;

        if (item == null || !item.hasItemMeta()) return;

        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.startsWith("§e")) return;

        String roomName = ChatColor.stripColor(displayName);

        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getName().equals(roomName)) {
                if (event.isShiftClick() && event.isLeftClick()) {
                    Location loc = new Location(
                            Bukkit.getWorld(room.getWorldName()),
                            room.getSpawnX(), room.getSpawnY(), room.getSpawnZ(),
                            room.getSpawnYaw(), room.getSpawnPitch()
                    );
                    com.hotels.util.SchedulerCompat.teleport(player, loc);
                    plugin.log(player, "管理员传送: 到房间 " + room.getName());
                    player.sendMessage("§a已传送到房间 " + room.getName());
                } else if (event.isRightClick()) {
                    player.closeInventory();
                    player.sendMessage("§c确认删除房间 §e" + room.getName() + "§c？在聊天框输入 §e确认 §c或 §e取消");
                    plugin.getChatInputHandler().expectInput(player, "admin_delete:" + room.getId() + ":" + page);
                } else {
                    plugin.log(player, "管理员打开房间管理: " + room.getName());
                    RoomManageGUI.open(player, room, plugin);
                }
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleElevatorSelectClick(Player player, InventoryClickEvent event, GUIHolder holder) {
        int slot = event.getSlot();
        List<ElevatorGUI.FloorData> floors = holder.getData(List.class);
        if (floors == null) {
            player.closeInventory();
            return;
        }
        int size = event.getInventory().getSize();
        // GUI 从底部往上摆放：slot 最大是最低层，slot 最小是最高层
        // idx = size - 1 - slot 对应 floors 列表中的索引（floors 已按 Y 从高到低排序）
        int idx = size - 1 - slot;
        if (idx < 0 || idx >= floors.size()) return;

        ElevatorGUI.FloorData floor = floors.get(idx);
        if (floor.isCurrent) {
            player.sendMessage("§7你已经在这一层");
            return;
        }

        Location target = floor.getTeleportLocation(player);
        if (target == null) {
            player.sendMessage("§c目标世界不存在");
            return;
        }

        player.closeInventory();
        com.hotels.util.SchedulerCompat.teleport(player, target);
        player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);

        int diff = floor.y - ((int) player.getLocation().getY() - 1);
        String direction = diff > 0 ? "上升" : "下降";
        plugin.log(player, "电梯选层传送: " + direction + " 到 Y:" + floor.y);
        player.sendMessage("§a" + direction + "到 Y: §e" + floor.y);
    }
}