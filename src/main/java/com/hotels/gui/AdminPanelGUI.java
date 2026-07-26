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
package com.hotels.gui;

import com.hotels.HotelsPlugin;
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminPanelGUI {

    public static final String GUI_NAME = "admin_panel";
    private static final int PAGE_SIZE = 36;

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) {
            player.sendMessage("§c插件未就绪");
            return;
        }

        List<HotelRoom> allRooms = new ArrayList<>(plugin.getRoomStorage().getAllRooms());
        int totalRooms = allRooms.size();
        int totalPages = (totalRooms + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":" + page), 54, "§c§l管理员后台");

        ItemStack blackPane = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        ItemStack redPane = createItem(Material.RED_STAINED_GLASS_PANE, "§8 ");
        ItemStack darkGrayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, "§8 ");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, blackPane);
        }

        inv.setItem(0, createItem(Material.COMMAND_BLOCK, "§c§l管理员面板",
                "§7总房间数: §f" + totalRooms,
                "§7总合集数: §f" + plugin.getRoomStorage().getCollectionCount(),
                "§7当前页面: §f" + (page + 1) + "/" + (totalPages > 0 ? totalPages : 1)));

        inv.setItem(4, createItem(Material.PAPER, "§6§l快捷操作",
                "§7左键: 查看房间详情",
                "§7右键: 删除房间",
                "§7Shift+左键: 传送"));

        if (totalPages > 1) {
            boolean hasPrev = page > 0;
            boolean hasNext = page < totalPages - 1;

            inv.setItem(36, hasPrev ?
                    createItem(Material.ARROW, "§a§l上一页") :
                    createItem(Material.GRAY_STAINED_GLASS_PANE, "§7上一页"));

            inv.setItem(40, createItem(Material.PAPER, "§e§l第 " + (page + 1) + " / " + totalPages + " 页"));

            inv.setItem(44, hasNext ?
                    createItem(Material.ARROW, "§a§l下一页") :
                    createItem(Material.GRAY_STAINED_GLASS_PANE, "§7下一页"));
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, redPane);
        }

        inv.setItem(45, createItem(Material.BARRIER, "§c§l强制删除选中房间", "§7按住Shift点击"));
        inv.setItem(46, createItem(Material.EMERALD, "§a§l重新加载数据", "§7点击重新加载"));
        inv.setItem(49, createItem(Material.ARROW, "§7§l返回主菜单", "§8点击返回"));
        inv.setItem(52, createItem(Material.COMPASS, "§d§l搜索房间", "§7点击搜索"));

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalRooms);

        int slot = 9;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot >= 36) break;

            HotelRoom room = allRooms.get(i);
            inv.setItem(slot++, createRoomItem(room, plugin));
        }

        player.openInventory(inv);
    }

    private static ItemStack createRoomItem(HotelRoom room, HotelsPlugin plugin) {
        Material icon;
        String statusColor;

        switch (room.getStatus()) {
            case AVAILABLE:
                icon = Material.LIME_WOOL;
                statusColor = "§a";
                break;
            case OCCUPIED:
                icon = Material.RED_WOOL;
                statusColor = "§c";
                break;
            case MAINTENANCE:
                icon = Material.GRAY_WOOL;
                statusColor = "§7";
                break;
            default:
                icon = Material.WHITE_WOOL;
                statusColor = "§7";
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + room.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §f" + room.getId());
            lore.add("§7房主: §f" + room.getOwnerName());
            lore.add("§7状态: " + statusColor + room.getStatus().name());
            lore.add("§7价格: §f" + plugin.getEconomyManager().format(room.getPrice()));
            lore.add("§7世界: §f" + room.getWorldName());
            lore.add("§7大小: §f" + room.getVolume() + " 方块");
            if (!room.getTags().isEmpty()) {
                lore.add("§7标签: " + room.getTagsDisplay());
            }
            if (room.isLocked()) {
                lore.add("§c已锁定");
            }
            if (room.hasPassword()) {
                lore.add("§c有密码");
            }
            if (room.isOccupied() && room.getCurrentGuestName() != null) {
                lore.add("§7当前客人: §f" + room.getCurrentGuestName());
            }
            lore.add("");
            lore.add("§8左键: 管理房间");
            lore.add("§8右键: 删除房间");
            lore.add("§8Shift+左键: 传送");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
