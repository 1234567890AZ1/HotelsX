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
import com.hotels.model.HotelRoom;
import com.hotels.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天输入处理器 - 接收玩家在聊天框输入的价格/密码等
 */
public class ChatInputHandler implements Listener {

    private final HotelsPlugin plugin;
    private final Map<UUID, String> pendingInputs;

    public ChatInputHandler(HotelsPlugin plugin) {
        this.plugin = plugin;
        this.pendingInputs = new ConcurrentHashMap<>();
    }

    /**
     * 期待玩家输入
     * @param player 玩家
     * @param context 上下文，格式 "action:roomId"，如 "setprice:abc123"
     */
    public void expectInput(Player player, String context) {
        pendingInputs.put(player.getUniqueId(), context);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String context = pendingInputs.get(player.getUniqueId());

        if (context == null) return;

        event.setCancelled(true);
        pendingInputs.remove(player.getUniqueId());

        String message = event.getMessage().trim();
        final String finalContext = context;

        SchedulerCompat.runTask(plugin, () -> {
            handleChatInput(player, finalContext, message);
        });
    }

    private void handleChatInput(Player player, String context, String message) {
        if (!context.contains(":") ||
            context.startsWith("deletecollection:") ||
            context.startsWith("setcollectionduration:") ||
            context.startsWith("deleteroom:") ||
            context.startsWith("setdiscountprice:") ||
            context.startsWith("setdiscountduration:") ||
            context.startsWith("setcollectionprice:") ||
            context.startsWith("admin_delete:") ||
            context.equals("admin_deleteall") ||
            context.startsWith("admin_search:") ||
            context.equals("browse_search") ||
            context.equals("myrooms_search") ||
            context.startsWith("collection_search:") ||
            context.equals("mycollection_search") ||
            context.startsWith("managecollection_search:") ||
            context.equals("web_changepwd") ||
            context.startsWith("web_register_pwd:") ||
            context.startsWith("checkin_password:")) {
            handleNonRoomContext(player, context, message);
            return;
        }

        String[] parts = context.split(":", 2);

        if (parts.length < 2) return;

        String action = parts[0];
        String roomId = parts[1];

        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            plugin.getLang().send(player, "listener.chat.room_not_found_or_deleted");
            return;
        }

        if (!room.getOwner().equals(player.getUniqueId())) {
            plugin.getLang().send(player, "common.not_room_owner");
            return;
        }

        switch (action) {
            case "setprice":
                try {
                    double price = Double.parseDouble(message);
                    if (price < 0) {
                        plugin.getLang().send(player, "listener.chat.price_negative");
                        return;
                    }
                    if (price > 1000000) {
                        plugin.getLang().send(player, "listener.chat.price_too_high_max");
                        return;
                    }
                    room.setPrice(price);
                    plugin.getRoomStorage().saveRoom(room);
                    plugin.log(player, "设置房间价格: " + room.getName() + " = " + price);
                    plugin.getLang().send(player, "listener.chat.price_set", "price", plugin.getEconomyManager().format(price));
                } catch (NumberFormatException e) {
                    plugin.getLang().send(player, "listener.chat.invalid_number");
                }
                break;

            case "setpassword":
                if (message.length() > 20) {
                    plugin.getLang().send(player, "listener.chat.password_too_long");
                    return;
                }
                if (message.isEmpty()) {
                    plugin.getLang().send(player, "listener.chat.password_empty");
                    return;
                }
                room.setPassword(message);
                plugin.getRoomStorage().saveRoom(room);
                plugin.log(player, "设置房间密码: " + room.getName());
                plugin.getLang().send(player, "listener.chat.password_set");
                break;
        }
    }

    private void handleNonRoomContext(Player player, String context, String message) {
        if (context.equals("createcollection")) {
            if (message.length() > 32) {
                plugin.getLang().send(player, "listener.chat.collection_name_too_long");
                return;
            }
            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.name_empty");
                return;
            }

            int maxCols = plugin.getConfig().getInt("max-collections-per-player", 5);
            int currentCols = plugin.getRoomStorage().getCollectionsByOwner(player.getUniqueId()).size();
            if (currentCols >= maxCols) {
                plugin.getLang().send(player, "listener.chat.collection_limit", "max", maxCols);
                return;
            }

            plugin.getLang().send(player, "listener.chat.enter_duration");
            plugin.getChatInputHandler().expectInput(player, "setcollectionduration:" + message);
            return;
        }

        if (context.startsWith("setcollectionduration:")) {
            String name = context.substring("setcollectionduration:".length());

            int duration;
            try {
                duration = Integer.parseInt(message);
                if (duration < 0) {
                    plugin.getLang().send(player, "listener.chat.duration_negative");
                    return;
                }
                if (duration > 43200) {
                    plugin.getLang().send(player, "listener.chat.duration_too_long");
                    return;
                }
            } catch (NumberFormatException e) {
                plugin.getLang().send(player, "listener.chat.invalid_number_minutes");
                return;
            }

            com.hotels.model.RoomCollection col = new com.hotels.model.RoomCollection();
            col.setName(name);
            col.setDurationMinutes(duration);
            col.setOwner(player.getUniqueId());
            col.setOwnerName(player.getName());
            plugin.getRoomStorage().saveCollection(col);

            plugin.log(player, "创建酒店合集: " + name + " (时长: " + duration + "分钟)");

            if (duration <= 0) {
                plugin.getLang().send(player, "listener.chat.collection_created_unlimited", "name", name);
            } else {
                plugin.getLang().send(player, "listener.chat.collection_created", "name", name, "minutes", duration);
            }
            plugin.getLang().send(player, "listener.chat.collection_manage_hint");
            return;
        }

        if (context.startsWith("deletecollection:")) {
            String colId = context.substring("deletecollection:".length());
            if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes") || message.equals("确认")) {
                com.hotels.model.RoomCollection col = plugin.getRoomStorage().getCollection(colId);
                if (col != null) {
                    String name = col.getName();
                    plugin.getRoomStorage().removeCollection(colId);
                    plugin.log(player, "删除酒店合集: " + name);
                    plugin.getLang().send(player, "listener.chat.collection_deleted", "name", name);
                } else {
                    plugin.getLang().send(player, "listener.chat.collection_not_found");
                }
            } else {
                plugin.getLang().send(player, "listener.chat.delete_cancelled");
            }
            return;
        }

        if (context.startsWith("deleteroom:")) {
            String roomId = context.substring("deleteroom:".length());
            if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes") || message.equals("确认")) {
                com.hotels.model.HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
                if (room != null) {
                    String name = room.getName();
                    plugin.getRoomStorage().removeRoom(roomId);
                    plugin.log(player, "删除房间: " + name + " (ID: " + roomId + ")");
                    plugin.getLang().send(player, "listener.chat.room_deleted", "name", name);
                } else {
                    plugin.getLang().send(player, "common.room_not_found");
                }
            } else {
                plugin.getLang().send(player, "listener.chat.delete_cancelled");
            }
            return;
        }

        if (context.startsWith("setdiscountprice:")) {
            String roomId = context.substring("setdiscountprice:".length());
            try {
                double price = Double.parseDouble(message);
                if (price < 0) {
                    plugin.getLang().send(player, "listener.chat.discount_negative");
                    return;
                }
                if (price > 1000000) {
                    plugin.getLang().send(player, "listener.chat.price_too_high");
                    return;
                }
                plugin.getLang().send(player, "listener.chat.enter_discount_duration");
                plugin.getChatInputHandler().expectInput(player, "setdiscountduration:" + roomId + ":" + price);
            } catch (NumberFormatException e) {
                plugin.getLang().send(player, "listener.chat.invalid_number");
            }
            return;
        }

        if (context.startsWith("setdiscountduration:")) {
            String[] parts = context.substring("setdiscountduration:".length()).split(":", 2);
            if (parts.length < 2) return;
            String roomId = parts[0];
            double discountPrice;
            try {
                discountPrice = Double.parseDouble(parts[1]);
            } catch (NumberFormatException e) {
                plugin.getLang().send(player, "listener.chat.data_error");
                return;
            }

            try {
                int minutes = Integer.parseInt(message);
                if (minutes <= 0) {
                    plugin.getLang().send(player, "listener.chat.discount_cancelled");
                    return;
                }
                if (minutes > 43200) {
                    plugin.getLang().send(player, "listener.chat.duration_too_long");
                    return;
                }

                com.hotels.model.HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
                if (room == null) {
                    plugin.getLang().send(player, "common.room_not_found");
                    return;
                }
                if (!room.getOwner().equals(player.getUniqueId())) {
                    plugin.getLang().send(player, "common.not_room_owner");
                    return;
                }

                room.setDiscount(discountPrice, minutes);
                plugin.getRoomStorage().saveRoom(room);
                plugin.log(player, "设置折扣: " + room.getName() + " 价格=" + discountPrice + " 时长=" + minutes + "分钟");
                plugin.getLang().send(player, "listener.chat.discount_set", "price", discountPrice, "minutes", minutes);
            } catch (NumberFormatException e) {
                plugin.getLang().send(player, "listener.chat.invalid_number_minutes");
            }
            return;
        }

        if (context.startsWith("setcollectionprice:")) {
            String colId = context.substring("setcollectionprice:".length());
            try {
                double price = Double.parseDouble(message);
                if (price < 0) {
                    plugin.getLang().send(player, "listener.chat.price_negative");
                    return;
                }
                if (price > 1000000) {
                    plugin.getLang().send(player, "listener.chat.price_too_high_max");
                    return;
                }

                com.hotels.model.RoomCollection col = plugin.getRoomStorage().getCollection(colId);
                if (col == null) {
                    plugin.getLang().send(player, "listener.chat.collection_not_found");
                    return;
                }
                if (!col.canManage(player.getUniqueId())) {
                    plugin.getLang().send(player, "common.no_permission_manage_collection");
                    return;
                }

                int count = 0;
                for (String roomId : col.getRoomIds()) {
                    com.hotels.model.HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
                    if (room != null) {
                        room.setPrice(price);
                        plugin.getRoomStorage().saveRoom(room);
                        count++;
                    }
                }
                plugin.log(player, "合集一键定价: " + col.getName() + " 设置 " + count + " 个房间价格为 " + price);
                plugin.getLang().send(player, "listener.chat.collection_price_set", "name", col.getName(), "count", count, "price", price);
            } catch (NumberFormatException e) {
                plugin.getLang().send(player, "listener.chat.invalid_number");
            }
            return;
        }

        if (context.startsWith("admin_delete:")) {
            String[] parts = context.substring("admin_delete:".length()).split(":", 2);
            if (parts.length < 2) return;
            String roomId = parts[0];
            int page;
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                page = 0;
            }

            if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes") || message.equals("确认")) {
                HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
                if (room != null) {
                    String name = room.getName();
                    plugin.getRoomStorage().removeRoom(roomId);
                    plugin.log(player, "管理员删除房间: " + name + " (ID: " + roomId + ")");
                    plugin.getLang().send(player, "listener.chat.room_deleted", "name", name);
                    com.hotels.gui.AdminPanelGUI.open(player, page);
                } else {
                    plugin.getLang().send(player, "common.room_not_found");
                }
            } else {
                plugin.getLang().send(player, "listener.chat.delete_cancelled");
                com.hotels.gui.AdminPanelGUI.open(player, page);
            }
            return;
        }

        if (context.equals("admin_deleteall")) {
            if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes") || message.equals("确认")) {
                int count = plugin.getRoomStorage().getAllRooms().size();
                plugin.getRoomStorage().clearAllRooms();
                plugin.log(player, "管理员强制删除所有房间: 共 " + count + " 个");
                plugin.getLang().send(player, "listener.chat.all_rooms_deleted", "count", count);
                com.hotels.gui.AdminPanelGUI.open(player, 0);
            } else {
                plugin.getLang().send(player, "listener.chat.delete_cancelled");
                com.hotels.gui.AdminPanelGUI.open(player, 0);
            }
            return;
        }

        if (context.startsWith("admin_search:")) {
            int page;
            try {
                page = Integer.parseInt(context.substring("admin_search:".length()));
            } catch (NumberFormatException e) {
                page = 0;
            }

            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.search_empty");
                com.hotels.gui.AdminPanelGUI.open(player, page);
                return;
            }

            boolean found = false;
            StringBuilder results = new StringBuilder(plugin.getLang().get("listener.chat.search_results_header")).append("\n");
            for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
                if (room.getName().toLowerCase().contains(message.toLowerCase()) ||
                    room.getId().toLowerCase().contains(message.toLowerCase())) {
                    results.append(plugin.getLang().get("listener.chat.search_result_line", "name", room.getName(), "id", room.getId())).append("\n");
                    found = true;
                }
            }

            if (!found) {
                plugin.getLang().send(player, "listener.chat.no_room_match");
            } else {
                player.sendMessage(results.toString());
            }
            com.hotels.gui.AdminPanelGUI.open(player, page);
            return;
        }

        if (context.equals("browse_search")) {
            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.search_empty");
                com.hotels.gui.BrowseRoomsGUI.open(player, plugin);
                return;
            }

            java.util.List<com.hotels.model.HotelRoom> allRooms = plugin.getRoomStorage().getAvailableRooms();
            java.util.List<com.hotels.model.HotelRoom> filtered = allRooms.stream()
                    .filter(r -> r.getName().toLowerCase().contains(message.toLowerCase()) ||
                                r.getId().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.no_room_match");
                com.hotels.gui.BrowseRoomsGUI.open(player, plugin);
            } else {
                com.hotels.gui.BrowseRoomsGUI.openWithRooms(player, filtered, plugin, null, 0);
            }
            return;
        }

        if (context.equals("myrooms_search")) {
            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.search_empty");
                com.hotels.gui.MyRoomsGUI.open(player, plugin, 0);
                return;
            }

            java.util.List<com.hotels.model.HotelRoom> allRooms = plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId());
            java.util.List<com.hotels.model.HotelRoom> filtered = allRooms.stream()
                    .filter(r -> r.getName().toLowerCase().contains(message.toLowerCase()) ||
                                r.getId().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.no_room_match");
                com.hotels.gui.MyRoomsGUI.open(player, plugin, 0);
            } else {
                com.hotels.gui.MyRoomsGUI.openWithRooms(player, filtered, plugin, 0);
            }
            return;
        }

        if (context.startsWith("collection_search:")) {
            int page = 0;
            try {
                page = Integer.parseInt(context.split(":")[1]);
            } catch (Exception e) {
                page = 0;
            }

            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.search_empty");
                com.hotels.gui.CollectionGUI.openBrowseAll(player, plugin, page);
                return;
            }

            java.util.List<com.hotels.model.RoomCollection> allCols = new java.util.ArrayList<>(plugin.getRoomStorage().getAllCollections());
            java.util.List<com.hotels.model.RoomCollection> filtered = allCols.stream()
                    .filter(c -> c.getName().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.no_hotel_match");
                com.hotels.gui.CollectionGUI.openBrowseAll(player, plugin, page);
            } else {
                com.hotels.gui.CollectionGUI.openBrowseAll(player, plugin, filtered, 0);
            }
            return;
        }

        if (context.equals("mycollection_search")) {
            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.search_empty");
                com.hotels.gui.CollectionGUI.openMyCollections(player, plugin);
                return;
            }

            java.util.List<com.hotels.model.RoomCollection> allCols = new java.util.ArrayList<>(plugin.getRoomStorage().getCollectionsByOwner(player.getUniqueId()));
            java.util.List<com.hotels.model.RoomCollection> filtered = allCols.stream()
                    .filter(c -> c.getName().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.no_hotel_match");
                com.hotels.gui.CollectionGUI.openMyCollections(player, plugin);
            } else {
                com.hotels.gui.CollectionGUI.openMyCollections(player, plugin, filtered);
            }
            return;
        }

        if (context.startsWith("managecollection_search:")) {
            String colId = context.substring("managecollection_search:".length());
            com.hotels.model.RoomCollection col = plugin.getRoomStorage().getCollection(colId);
            if (col == null) {
                plugin.getLang().send(player, "listener.chat.collection_not_found");
                com.hotels.gui.CollectionGUI.openManage(player);
                return;
            }

            if (message.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.search_empty");
                com.hotels.gui.CollectionGUI.openManageCollection(player, col, plugin);
                return;
            }

            java.util.List<com.hotels.model.HotelRoom> allRooms = plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId());
            java.util.List<com.hotels.model.HotelRoom> filtered = allRooms.stream()
                    .filter(r -> r.getName().toLowerCase().contains(message.toLowerCase()) ||
                                r.getId().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                plugin.getLang().send(player, "listener.chat.no_room_match");
                com.hotels.gui.CollectionGUI.openManageCollection(player, col, plugin);
            } else {
                com.hotels.gui.CollectionGUI.openManageCollection(player, col, plugin, filtered, 0);
            }
            return;
        }

        // Web 账户注册：密码输入（/ht web register <用户名> 后聊天输入）
        if (context.startsWith("web_register_pwd:")) {
            String username = context.substring("web_register_pwd:".length());
            if (message.length() < 4) {
                plugin.getLang().send(player, "listener.chat.password_min_length");
                plugin.getChatInputHandler().expectInput(player, context);
                return;
            }
            String err = plugin.getWebServer().registerUser(username, message, player.getName());
            if (err != null) {
                player.sendMessage("§c" + err);
            } else {
                plugin.getLang().send(player, "listener.chat.register_success");
                plugin.getLang().send(player, "listener.chat.account", "username", username);
                plugin.getLang().send(player, "listener.chat.role_user");
                plugin.getLang().send(player, "listener.chat.linked_game_id", "player", player.getName());
                if (plugin.getWebServer().isRunning()) {
                    plugin.getLang().send(player, "listener.chat.web_access", "port", plugin.getWebServer().getPort());
                }
            }
            return;
        }

        // Web 账户修改密码（/ht web changepwd 后聊天输入）
        if (context.equals("web_changepwd")) {
            if (message.length() < 4) {
                plugin.getLang().send(player, "listener.chat.password_min_length");
                plugin.getChatInputHandler().expectInput(player, context);
                return;
            }
            String err = plugin.getWebServer().changePasswordByMinecraft(player.getName(), message);
            if (err != null) {
                player.sendMessage("§c" + err);
            } else {
                plugin.getLang().send(player, "listener.chat.password_changed");
            }
            return;
        }

        // 房间入住密码验证（/ht checkin <房间ID> 后聊天输入）
        if (context.startsWith("checkin_password:")) {
            String roomId = context.substring("checkin_password:".length());
            HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
            if (room == null) {
                plugin.getLang().send(player, "listener.chat.room_not_found_or_deleted");
                return;
            }
            if (!room.checkPassword(message)) {
                plugin.getLang().send(player, "listener.chat.password_wrong");
                return;
            }
            // 密码验证通过后触发自动迁移，保存房间
            plugin.getRoomStorage().saveRoom(room);
            plugin.log(player, "尝试入住房间: " + room.getName() + " (ID: " + roomId + ")");
            plugin.getCheckinHandler().attemptCheckin(player, room);
            return;
        }
    }

    public void clear(Player player) {
        pendingInputs.remove(player.getUniqueId());
    }
}
