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

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
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
            context.startsWith("managecollection_search:")) {
            handleNonRoomContext(player, context, message);
            return;
        }

        String[] parts = context.split(":", 2);

        if (parts.length < 2) return;

        String action = parts[0];
        String roomId = parts[1];

        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            player.sendMessage("§c房间不存在或已删除");
            return;
        }

        if (!room.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c你不是这个房间的房主");
            return;
        }

        switch (action) {
            case "setprice":
                try {
                    double price = Double.parseDouble(message);
                    if (price < 0) {
                        player.sendMessage("§c价格不能为负数");
                        return;
                    }
                    if (price > 1000000) {
                        player.sendMessage("§c价格太高了，最高 1000000");
                        return;
                    }
                    room.setPrice(price);
                    plugin.getRoomStorage().saveRoom(room);
                    plugin.log(player, "设置房间价格: " + room.getName() + " = " + price);
                    player.sendMessage("§a房间价格已设置为: §e" + plugin.getEconomyManager().format(price));
                } catch (NumberFormatException e) {
                    player.sendMessage("§c请输入有效的数字");
                }
                break;

            case "setpassword":
                if (message.length() > 20) {
                    player.sendMessage("§c密码最长 20 个字符");
                    return;
                }
                if (message.isEmpty()) {
                    player.sendMessage("§c密码不能为空");
                    return;
                }
                room.setPassword(message);
                plugin.getRoomStorage().saveRoom(room);
                plugin.log(player, "设置房间密码: " + room.getName());
                player.sendMessage("§a房间密码已设置");
                break;
        }
    }

    private void handleNonRoomContext(Player player, String context, String message) {
        if (context.equals("createcollection")) {
            if (message.length() > 32) {
                player.sendMessage("§c合集名称最长 32 个字符");
                return;
            }
            if (message.isEmpty()) {
                player.sendMessage("§c名称不能为空");
                return;
            }

            int maxCols = plugin.getConfig().getInt("max-collections-per-player", 5);
            int currentCols = plugin.getRoomStorage().getCollectionsByOwner(player.getUniqueId()).size();
            if (currentCols >= maxCols) {
                player.sendMessage("§c你已达到最大合集数量限制 (" + maxCols + "个)");
                return;
            }

            player.sendMessage("§e请输入使用时长（分钟），输入 0 表示不限时:");
            plugin.getChatInputHandler().expectInput(player, "setcollectionduration:" + message);
            return;
        }

        if (context.startsWith("setcollectionduration:")) {
            String name = context.substring("setcollectionduration:".length());

            int duration;
            try {
                duration = Integer.parseInt(message);
                if (duration < 0) {
                    player.sendMessage("§c时长不能为负数");
                    return;
                }
                if (duration > 43200) {
                    player.sendMessage("§c时长不能超过 43200 分钟（30天）");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c请输入有效的数字（分钟）");
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
                player.sendMessage("§a酒店合集 §e" + name + " §a创建成功！不限时");
            } else {
                player.sendMessage("§a酒店合集 §e" + name + " §a创建成功！时长: " + duration + " 分钟");
            }
            player.sendMessage("§7使用 §e/ht §7打开菜单管理合集");
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
                    player.sendMessage("§c合集 §e" + name + " §c已删除");
                } else {
                    player.sendMessage("§c合集不存在");
                }
            } else {
                player.sendMessage("§c已取消删除");
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
                    player.sendMessage("§c房间 §e" + name + " §c已删除");
                } else {
                    player.sendMessage("§c房间不存在");
                }
            } else {
                player.sendMessage("§c已取消删除");
            }
            return;
        }

        if (context.startsWith("setdiscountprice:")) {
            String roomId = context.substring("setdiscountprice:".length());
            try {
                double price = Double.parseDouble(message);
                if (price < 0) {
                    player.sendMessage("§c折扣价不能为负数");
                    return;
                }
                if (price > 1000000) {
                    player.sendMessage("§c价格太高了");
                    return;
                }
                player.sendMessage("§e请输入折扣持续时长（分钟），输入 0 取消:");
                plugin.getChatInputHandler().expectInput(player, "setdiscountduration:" + roomId + ":" + price);
            } catch (NumberFormatException e) {
                player.sendMessage("§c请输入有效的数字");
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
                player.sendMessage("§c数据异常");
                return;
            }

            try {
                int minutes = Integer.parseInt(message);
                if (minutes <= 0) {
                    player.sendMessage("§c已取消折扣设置");
                    return;
                }
                if (minutes > 43200) {
                    player.sendMessage("§c时长不能超过 43200 分钟（30天）");
                    return;
                }

                com.hotels.model.HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
                if (room == null) {
                    player.sendMessage("§c房间不存在");
                    return;
                }
                if (!room.getOwner().equals(player.getUniqueId())) {
                    player.sendMessage("§c你不是这个房间的房主");
                    return;
                }

                room.setDiscount(discountPrice, minutes);
                plugin.getRoomStorage().saveRoom(room);
                plugin.log(player, "设置折扣: " + room.getName() + " 价格=" + discountPrice + " 时长=" + minutes + "分钟");
                player.sendMessage("§a折扣已设置！价格 §e" + discountPrice + " §a持续 §e" + minutes + " §a分钟");
            } catch (NumberFormatException e) {
                player.sendMessage("§c请输入有效的数字（分钟）");
            }
            return;
        }

        if (context.startsWith("setcollectionprice:")) {
            String colId = context.substring("setcollectionprice:".length());
            try {
                double price = Double.parseDouble(message);
                if (price < 0) {
                    player.sendMessage("§c价格不能为负数");
                    return;
                }
                if (price > 1000000) {
                    player.sendMessage("§c价格太高了，最高 1000000");
                    return;
                }

                com.hotels.model.RoomCollection col = plugin.getRoomStorage().getCollection(colId);
                if (col == null) {
                    player.sendMessage("§c合集不存在");
                    return;
                }
                if (!col.canManage(player.getUniqueId())) {
                    player.sendMessage("§c你没有权限管理此合集");
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
                player.sendMessage("§a已统一设置合集 §e" + col.getName() + " §a内 §e" + count + " §a个房间的价格为 §e" + price);
            } catch (NumberFormatException e) {
                player.sendMessage("§c请输入有效的数字");
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
                    player.sendMessage("§c房间 §e" + name + " §c已删除");
                    com.hotels.gui.AdminPanelGUI.open(player, page);
                } else {
                    player.sendMessage("§c房间不存在");
                }
            } else {
                player.sendMessage("§c已取消删除");
                com.hotels.gui.AdminPanelGUI.open(player, page);
            }
            return;
        }

        if (context.equals("admin_deleteall")) {
            if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes") || message.equals("确认")) {
                int count = plugin.getRoomStorage().getAllRooms().size();
                plugin.getRoomStorage().clearAllRooms();
                plugin.log(player, "管理员强制删除所有房间: 共 " + count + " 个");
                player.sendMessage("§c已强制删除所有房间，共 §e" + count + " §c个");
                com.hotels.gui.AdminPanelGUI.open(player, 0);
            } else {
                player.sendMessage("§c已取消删除");
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
                player.sendMessage("§c搜索内容不能为空");
                com.hotels.gui.AdminPanelGUI.open(player, page);
                return;
            }

            boolean found = false;
            StringBuilder results = new StringBuilder("§e搜索结果:\n");
            for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
                if (room.getName().toLowerCase().contains(message.toLowerCase()) ||
                    room.getId().toLowerCase().contains(message.toLowerCase())) {
                    results.append("§6").append(room.getName()).append(" §7(ID: ").append(room.getId()).append(")\n");
                    found = true;
                }
            }

            if (!found) {
                player.sendMessage("§c未找到匹配的房间");
            } else {
                player.sendMessage(results.toString());
            }
            com.hotels.gui.AdminPanelGUI.open(player, page);
            return;
        }

        if (context.equals("browse_search")) {
            if (message.isEmpty()) {
                player.sendMessage("§c搜索内容不能为空");
                com.hotels.gui.BrowseRoomsGUI.open(player, plugin);
                return;
            }

            java.util.List<com.hotels.model.HotelRoom> allRooms = plugin.getRoomStorage().getAvailableRooms();
            java.util.List<com.hotels.model.HotelRoom> filtered = allRooms.stream()
                    .filter(r -> r.getName().toLowerCase().contains(message.toLowerCase()) ||
                                r.getId().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                player.sendMessage("§c未找到匹配的房间");
                com.hotels.gui.BrowseRoomsGUI.open(player, plugin);
            } else {
                com.hotels.gui.BrowseRoomsGUI.openWithRooms(player, filtered, plugin, null, 0);
            }
            return;
        }

        if (context.equals("myrooms_search")) {
            if (message.isEmpty()) {
                player.sendMessage("§c搜索内容不能为空");
                com.hotels.gui.MyRoomsGUI.open(player, plugin, 0);
                return;
            }

            java.util.List<com.hotels.model.HotelRoom> allRooms = plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId());
            java.util.List<com.hotels.model.HotelRoom> filtered = allRooms.stream()
                    .filter(r -> r.getName().toLowerCase().contains(message.toLowerCase()) ||
                                r.getId().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                player.sendMessage("§c未找到匹配的房间");
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
                player.sendMessage("§c搜索内容不能为空");
                com.hotels.gui.CollectionGUI.openBrowseAll(player, plugin, page);
                return;
            }

            java.util.List<com.hotels.model.RoomCollection> allCols = new java.util.ArrayList<>(plugin.getRoomStorage().getAllCollections());
            java.util.List<com.hotels.model.RoomCollection> filtered = allCols.stream()
                    .filter(c -> c.getName().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                player.sendMessage("§c未找到匹配的酒店");
                com.hotels.gui.CollectionGUI.openBrowseAll(player, plugin, page);
            } else {
                com.hotels.gui.CollectionGUI.openBrowseAll(player, plugin, filtered, 0);
            }
            return;
        }

        if (context.equals("mycollection_search")) {
            if (message.isEmpty()) {
                player.sendMessage("§c搜索内容不能为空");
                com.hotels.gui.CollectionGUI.openMyCollections(player, plugin);
                return;
            }

            java.util.List<com.hotels.model.RoomCollection> allCols = new java.util.ArrayList<>(plugin.getRoomStorage().getCollectionsByOwner(player.getUniqueId()));
            java.util.List<com.hotels.model.RoomCollection> filtered = allCols.stream()
                    .filter(c -> c.getName().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                player.sendMessage("§c未找到匹配的酒店");
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
                player.sendMessage("§c合集不存在");
                com.hotels.gui.CollectionGUI.openManage(player);
                return;
            }

            if (message.isEmpty()) {
                player.sendMessage("§c搜索内容不能为空");
                com.hotels.gui.CollectionGUI.openManageCollection(player, col, plugin);
                return;
            }

            java.util.List<com.hotels.model.HotelRoom> allRooms = plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId());
            java.util.List<com.hotels.model.HotelRoom> filtered = allRooms.stream()
                    .filter(r -> r.getName().toLowerCase().contains(message.toLowerCase()) ||
                                r.getId().toLowerCase().contains(message.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());

            if (filtered.isEmpty()) {
                player.sendMessage("§c未找到匹配的房间");
                com.hotels.gui.CollectionGUI.openManageCollection(player, col, plugin);
            } else {
                com.hotels.gui.CollectionGUI.openManageCollection(player, col, plugin, filtered, 0);
            }
            return;
        }
    }

    public void clear(Player player) {
        pendingInputs.remove(player.getUniqueId());
    }
}
