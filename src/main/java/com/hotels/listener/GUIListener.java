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
                    plugin.getLang().send(player, "common.no_permission_create");
                    return;
                }
                plugin.log(player, "查看创建房间说明");
                player.closeInventory();
                plugin.getLang().send(player, "listener.gui.create_room_header");
                plugin.getLang().send(player, "listener.gui.create_room_line1");
                plugin.getLang().send(player, "listener.gui.create_room_line2");
                plugin.getLang().send(player, "listener.gui.create_room_line3");
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
                plugin.getLang().send(player, "listener.gui.enter_search_hotel");
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
                    plugin.getLang().send(player, "listener.gui.enter_search_room");
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
                plugin.getLang().send(player, "listener.gui.enter_search_hotel");
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
                    plugin.getLang().send(player, "listener.gui.enter_new_collection_name");
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
                        plugin.getLang().send(player, "listener.gui.confirm_delete_collection", "name", col.getName());
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
                    plugin.getLang().send(player, "listener.gui.only_owner_manage_admin");
                    return;
                }
                if (col.getOwner().equals(target.getUniqueId())) {
                    plugin.getLang().send(player, "listener.gui.cannot_operate_owner");
                    return;
                }
                if (col.getAdmins().contains(target.getUniqueId().toString())) {
                    col.removeAdmin(target.getUniqueId());
                    plugin.log(player, "移除合集管理员: " + target.getName() + " 从 " + col.getName());
                    plugin.getLang().send(player, "listener.gui.admin_removed", "name", target.getName());
                } else {
                    col.addAdmin(target.getUniqueId());
                    plugin.log(player, "添加合集管理员: " + target.getName() + " 到 " + col.getName());
                    plugin.getLang().send(player, "listener.gui.admin_added", "name", target.getName());
                }
                plugin.getRoomStorage().saveCollection(col);
                CollectionGUI.openAdminManage(player, col, plugin);
                return;
            }

            if (item == null || !item.hasItemMeta()) return;

            String itemName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if (itemName.contains("管理员管理")) {
                if (!col.canManage(player.getUniqueId())) {
                    plugin.getLang().send(player, "common.no_permission_manage_collection");
                    return;
                }
                plugin.log(player, "打开合集管理员管理: " + col.getName());
                CollectionGUI.openAdminManage(player, col, plugin);
                return;
            }

            if (itemName.contains("一键定价")) {
                if (!col.canManage(player.getUniqueId())) {
                    plugin.getLang().send(player, "common.no_permission_manage_collection");
                    return;
                }
                plugin.log(player, "开始合集一键定价: " + col.getName());
                player.closeInventory();
                plugin.getLang().send(player, "listener.gui.enter_collection_price");
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
                            plugin.getLang().send(player, "listener.gui.room_removed_from_collection", "name", room.getName());
                        } else {
                            col.addRoom(room.getId());
                            plugin.log(player, "添加房间到合集: " + room.getName() + " 到 " + col.getName());
                            plugin.getLang().send(player, "listener.gui.room_added_to_collection", "name", room.getName());
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
            plugin.getLang().send(player, "listener.gui.enter_search_room_id");
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
                    plugin.getLang().send(player, "listener.gui.confirm_delete_room", "name", room.getName());
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
            plugin.getLang().send(player, "listener.gui.enter_search_room_id");
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
            plugin.getLang().send(player, "listener.gui.room_data_error");
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 10:
                player.closeInventory();
                plugin.log(player, "准备设置房间价格: " + targetRoom.getName());
                plugin.getLang().send(player, "listener.gui.enter_new_price");
                plugin.getChatInputHandler().expectInput(player, "setprice:" + targetRoom.getId());
                break;
            case 11:
                player.closeInventory();
                if (targetRoom.hasActiveDiscount()) {
                    targetRoom.clearDiscount();
                    plugin.getRoomStorage().saveRoom(targetRoom);
                    plugin.log(player, "取消房间折扣: " + targetRoom.getName());
                    plugin.getLang().send(player, "listener.gui.discount_cancelled");
                    RoomManageGUI.open(player, targetRoom, plugin);
                } else {
                    plugin.log(player, "准备设置房间折扣: " + targetRoom.getName());
                    plugin.getLang().send(player, "listener.gui.enter_discount_price");
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
                    plugin.getLang().send(player, "listener.gui.password_cleared");
                } else {
                    plugin.log(player, "准备设置房间密码: " + targetRoom.getName());
                    plugin.getLang().send(player, "listener.gui.enter_room_password");
                    plugin.getChatInputHandler().expectInput(player, "setpassword:" + targetRoom.getId());
                }
                break;
            case 14:
                targetRoom.setLocked(!targetRoom.isLocked());
                plugin.getRoomStorage().saveRoom(targetRoom);
                plugin.log(player, "房间" + (targetRoom.isLocked() ? "锁定" : "解锁") + ": " + targetRoom.getName());
                plugin.getLang().send(player, targetRoom.isLocked() ? "listener.gui.room_locked" : "listener.gui.room_unlocked");
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
                        plugin.getLang().send(player, "listener.gui.room_occupied_cannot_switch");
                        return;
                }
                plugin.getRoomStorage().saveRoom(targetRoom);
                plugin.log(player, "房间状态更新为: " + targetRoom.getStatus() + " (" + targetRoom.getName() + ")");
                plugin.getLang().send(player, "listener.gui.room_status_updated");
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
                plugin.getLang().send(player, "listener.gui.teleported_to_room");
                break;
            case 23:
                if (targetRoom.isOccupied()) {
                    Player guest = plugin.getServer().getPlayer(targetRoom.getCurrentGuest());
                    String guestName = guest != null ? guest.getName() : targetRoom.getCurrentGuestName();
                    if (guest != null && guest.isOnline()) {
                        plugin.getLang().send(guest, "listener.gui.kicked_by_owner", "room", targetRoom.getName());
                    }
                    targetRoom.setCurrentGuest(null);
                    targetRoom.setCurrentGuestName(null);
                    targetRoom.setStatus(HotelRoom.RoomStatus.AVAILABLE);
                    targetRoom.setCheckinTime(0);
                    plugin.getRoomStorage().saveRoom(targetRoom);
                    plugin.log(player, "踢出客人: " + guestName + " 从房间 " + targetRoom.getName());
                    plugin.getLang().send(player, "listener.gui.guest_kicked");
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
                plugin.getLang().send(player, "listener.gui.tag_removed", "tag", tagName);
            } else {
                if (room.getTags().size() >= 3) {
                    plugin.getLang().send(player, "listener.gui.tag_limit");
                } else {
                    room.addTag(tagName);
                    plugin.log(player, "添加房间标签: " + tagName + " 到 " + room.getName());
                    plugin.getLang().send(player, "listener.gui.tag_added", "tag", tagName);
                }
            }
            plugin.getRoomStorage().saveRoom(room);
            TagSelectGUI.open(player, room, plugin);
        }
    }

    private void sendHelp(Player player) {
        plugin.getLang().send(player, "listener.gui.help_header");
        plugin.getLang().send(player, "listener.gui.help_menu");
        plugin.getLang().send(player, "listener.gui.help_create");
        plugin.getLang().send(player, "listener.gui.help_manage");
        plugin.getLang().send(player, "listener.gui.help_remove");
        plugin.getLang().send(player, "listener.gui.help_list");
        plugin.getLang().send(player, "listener.gui.help_tp");

        plugin.getLang().send(player, "listener.gui.help_wand");
        plugin.getLang().send(player, "listener.gui.help_admin");
    }

    private void handleAdminPanelClick(Player player, InventoryClickEvent event, String guiName) {
        int slot = event.getSlot();
        ItemStack item = event.getCurrentItem();

        if (!player.hasPermission("hotels.admin")) {
            plugin.getLang().send(player, "command.admin.no_permission");
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
                plugin.getLang().send(player, "listener.gui.confirm_delete_all");
                plugin.getChatInputHandler().expectInput(player, "admin_deleteall");
            }
            return;
        }

        if (slot == 51) {
            plugin.getRoomStorage().loadAll();
            plugin.log(player, "管理员重新加载数据");
            plugin.getLang().send(player, "listener.gui.data_reloaded");
            AdminPanelGUI.open(player, page);
            return;
        }

        if (slot == 48) {
            player.closeInventory();
            plugin.getLang().send(player, "listener.gui.enter_search_room_id");
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
                    plugin.getLang().send(player, "listener.gui.admin_teleported", "room", room.getName());
                } else if (event.isRightClick()) {
                    player.closeInventory();
                    plugin.getLang().send(player, "listener.gui.confirm_delete_room", "name", room.getName());
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
            plugin.getLang().send(player, "listener.gui.elevator_already_here");
            return;
        }

        Location target = floor.getTeleportLocation(player);
        if (target == null) {
            plugin.getLang().send(player, "listener.gui.target_world_missing");
            return;
        }

        player.closeInventory();
        com.hotels.util.SchedulerCompat.teleport(player, target);
        player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);

        int diff = floor.y - ((int) player.getLocation().getY() - 1);
        String direction = diff > 0 ? "上升" : "下降";
        plugin.log(player, "电梯选层传送: " + direction + " 到 Y:" + floor.y);
        plugin.getLang().send(player, diff > 0 ? "listener.gui.elevator_moved_up" : "listener.gui.elevator_moved_down", "y", floor.y);
    }
}