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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MyRoomsGUI {

    public static final String GUI_NAME = "my_rooms";
    private static final int PAGE_SIZE = 36;

    public static void open(Player player, HotelsPlugin plugin) {
        open(player, plugin, 0);
    }

    public static void open(Player player, HotelsPlugin plugin, int page) {
        List<HotelRoom> rooms = plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId());
        openWithRooms(player, rooms, plugin, page);
    }

    public static void openWithRooms(Player player, List<HotelRoom> rooms, HotelsPlugin plugin, int page) {
        int totalPages = (int) Math.ceil((double) rooms.size() / PAGE_SIZE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, rooms.size());
        List<HotelRoom> pageRooms = rooms.subList(start, end);

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":" + page), 54, "§8§l我的房间 §7(" + (page + 1) + "/" + Math.max(1, totalPages) + ")");

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName("§8 ");
            border.setItemMeta(borderMeta);
        }

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        ItemStack titleItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta titleMeta = titleItem.getItemMeta();
        if (titleMeta != null) {
            titleMeta.setDisplayName("§e§l我的房间");
            List<String> lore = new ArrayList<>();
            lore.add("§7共 §e" + rooms.size() + " §7个房间");
            titleMeta.setLore(lore);
            titleItem.setItemMeta(titleMeta);
        }
        inv.setItem(3, titleItem);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName("§d§l搜索房间");
            searchMeta.setLore(java.util.Arrays.asList("§7点击搜索"));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(5, searchItem);

        int slot = 9;
        for (HotelRoom room : pageRooms) {
            inv.setItem(slot++, createRoomItem(room));
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            backMeta.setLore(java.util.Arrays.asList("§7返回主菜单"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName("§7上一页");
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(47, prev);
        }

        inv.setItem(49, createItem(Material.PAPER, "§7第 " + (page + 1) + " / " + Math.max(1, totalPages) + " 页"));

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName("§7下一页");
                next.setItemMeta(nextMeta);
            }
            inv.setItem(51, next);
        }

        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, border);
            }
        }

        player.openInventory(inv);
    }

    private static ItemStack createRoomItem(HotelRoom room) {
        Material material;
        String statusColor;

        switch (room.getStatus()) {
            case AVAILABLE:
                material = Material.GREEN_WOOL;
                statusColor = "§a";
                break;
            case OCCUPIED:
                material = Material.RED_WOOL;
                statusColor = "§c";
                break;
            case MAINTENANCE:
                material = Material.GRAY_WOOL;
                statusColor = "§7";
                break;
            default:
                material = Material.WHITE_WOOL;
                statusColor = "§7";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + room.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7ID: §f" + room.getId());
            lore.add("§7状态: " + statusColor + room.getStatus().name());
            if (room.hasActiveDiscount()) {
                lore.add("§7价格: §m§f" + room.getPrice() + "§r §a§l¥" + room.getDiscountPrice());
                lore.add(room.getDiscountDisplay());
            } else {
                lore.add("§7价格: §f" + room.getPrice() + " 每晚");
            }
            lore.add("§7世界: §f" + room.getWorldName());
            lore.add("§7区域: §f" + room.getVolume() + " 方块");
            if (!room.getTags().isEmpty()) {
                lore.add("§7标签: " + room.getTagsDisplay());
            }
            if (room.isLocked()) {
                lore.add("§c已上锁");
            }
            if (room.hasPassword()) {
                lore.add("§c需要密码");
            }
            if (room.isOccupied() && room.getCurrentGuestName() != null) {
                lore.add("§7客人: §f" + room.getCurrentGuestName());
            }
            lore.add("");
            lore.add("§e左键 §7管理房间");
            lore.add("§c右键 §7删除房间");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return "§a空闲";
            case OCCUPIED: return "§c已入住";
            case MAINTENANCE: return "§7维护中";
            default: return "§7未知";
        }
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(java.util.Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}