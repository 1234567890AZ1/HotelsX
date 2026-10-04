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

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":" + page), 54, plugin.getLang().get("gui.my_rooms.title", "page", page + 1, "total", Math.max(1, totalPages)));

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
            titleMeta.setDisplayName(plugin.getLang().get("gui.my_rooms.title_item_name"));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.getLang().get("gui.my_rooms.total_rooms", "count", rooms.size()));
            titleMeta.setLore(lore);
            titleItem.setItemMeta(titleMeta);
        }
        inv.setItem(3, titleItem);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName(plugin.getLang().get("gui.common.search_room"));
            searchMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.common.click_search")));
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
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
            backMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.common.back_to_main_lore_7")));
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(plugin.getLang().get("gui.common.prev_page"));
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(47, prev);
        }

        inv.setItem(49, createItem(Material.PAPER, plugin.getLang().get("gui.common.page", "page", page + 1, "total", Math.max(1, totalPages))));

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(plugin.getLang().get("gui.common.next_page"));
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
        HotelsPlugin plugin = HotelsPlugin.getInstance();
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
            lore.add(plugin.getLang().get("gui.common.id", "id", room.getId()));
            lore.add(plugin.getLang().get("gui.common.status", "status", statusColor + room.getStatus().name()));
            if (room.hasActiveDiscount()) {
                lore.add(plugin.getLang().get("gui.browse.price_discount", "price", room.getPrice(), "discount", room.getDiscountPrice()));
                lore.add(room.getDiscountDisplay());
            } else {
                lore.add(plugin.getLang().get("gui.my_rooms.price_per_night", "price", room.getPrice()));
            }
            lore.add(plugin.getLang().get("gui.common.world", "world", room.getWorldName()));
            lore.add(plugin.getLang().get("gui.my_rooms.area", "volume", room.getVolume()));
            if (!room.getTags().isEmpty()) {
                lore.add(plugin.getLang().get("gui.common.tags", "tags", room.getTagsDisplay()));
            }
            if (room.isLocked()) {
                lore.add(plugin.getLang().get("gui.my_rooms.locked"));
            }
            if (room.hasPassword()) {
                lore.add(plugin.getLang().get("gui.common.need_password"));
            }
            if (room.isOccupied() && room.getCurrentGuestName() != null) {
                lore.add(plugin.getLang().get("gui.my_rooms.guest", "guest", room.getCurrentGuestName()));
            }
            lore.add("");
            lore.add(plugin.getLang().get("gui.my_rooms.manage_hint"));
            lore.add(plugin.getLang().get("gui.my_rooms.delete_hint"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return HotelsPlugin.getInstance().getLang().get("command.status.available");
            case OCCUPIED: return HotelsPlugin.getInstance().getLang().get("command.status.occupied");
            case MAINTENANCE: return HotelsPlugin.getInstance().getLang().get("command.status.maintenance");
            default: return HotelsPlugin.getInstance().getLang().get("command.status.unknown");
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
