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
import java.util.stream.Collectors;

public class BrowseRoomsGUI {

    public static final String GUI_NAME = "browse_rooms";
    private static final int PAGE_SIZE = 36;

    public static void open(Player player, HotelsPlugin plugin) {
        List<HotelRoom> rooms = plugin.getRoomStorage().getAvailableRooms();
        openWithRooms(player, rooms, plugin, null, 0);
    }

    public static void openByTag(Player player, String tag, HotelsPlugin plugin) {
        List<HotelRoom> allRooms = plugin.getRoomStorage().getAvailableRooms();
        List<HotelRoom> filtered = allRooms.stream()
                .filter(r -> r.hasTag(tag))
                .collect(Collectors.toList());
        openWithRooms(player, filtered, plugin, tag, 0);
    }

    public static void openTagFilter(Player player, HotelsPlugin plugin) {
        List<String> presetTags = plugin.getConfig().getStringList("room-tags");
        int size = Math.min(54, Math.max(9, ((presetTags.size() / 9) + 2) * 9));
        Inventory inv = Bukkit.createInventory(new GUIHolder("tag_filter"), size, plugin.getLang().get("gui.browse.tag_filter_title"));

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);

        ItemStack allItem = new ItemStack(Material.COMPASS);
        ItemMeta allMeta = allItem.getItemMeta();
        if (allMeta != null) {
            allMeta.setDisplayName(plugin.getLang().get("gui.browse.all_rooms_name"));
            allMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.browse.all_rooms_lore")));
            allItem.setItemMeta(allMeta);
        }
        inv.setItem(4, allItem);

        int slot = 9;
        for (String tag : presetTags) {
            long count = plugin.getRoomStorage().getAvailableRooms().stream()
                    .filter(r -> r.hasTag(tag)).count();

            ItemStack item = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + tag);
                meta.setLore(java.util.Arrays.asList(
                        plugin.getLang().get("gui.browse.tag_count", "count", count),
                        "",
                        plugin.getLang().get("gui.browse.click_filter")
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        inv.setItem(size - 1, createItem(Material.ARROW,
                plugin.getLang().get("gui.common.back_gold"),
                plugin.getLang().get("gui.common.back_to_main_lore")));

        player.openInventory(inv);
    }

    public static void openWithRooms(Player player, List<HotelRoom> rooms, HotelsPlugin plugin, String currentTag, int page) {
        int totalPages = (int) Math.ceil((double) rooms.size() / PAGE_SIZE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, rooms.size());
        List<HotelRoom> pageRooms = rooms.subList(start, end);

        String tagDisplay = currentTag != null && !currentTag.equals("all") ? " §7- §e" + currentTag : "";
        String title = plugin.getLang().get("gui.browse.title", "tag", tagDisplay);
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":" + (currentTag != null ? currentTag : "all") + ":" + page), 54, title);

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
            titleMeta.setDisplayName(plugin.getLang().get("gui.browse.title_item_name"));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.getLang().get("gui.browse.total_available", "count", rooms.size()));
            if (currentTag != null && !currentTag.equals("all")) {
                lore.add(plugin.getLang().get("gui.browse.tag_filter_line", "tag", currentTag));
            }
            titleMeta.setLore(lore);
            titleItem.setItemMeta(titleMeta);
        }
        inv.setItem(3, titleItem);

        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        if (filterMeta != null) {
            filterMeta.setDisplayName(plugin.getLang().get("gui.browse.filter_name"));
            filterMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.browse.filter_lore")));
            filterItem.setItemMeta(filterMeta);
        }
        inv.setItem(4, filterItem);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName(plugin.getLang().get("gui.common.search_room"));
            searchMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.common.click_search")));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(5, searchItem);

        if (rooms.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getLang().get("gui.browse.empty"));
                empty.setItemMeta(meta);
            }
            inv.setItem(22, empty);
        } else {
            int slot = 9;
            for (HotelRoom room : pageRooms) {
                if (slot >= 45) break;
                inv.setItem(slot++, createRoomItem(room, player));
            }
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

    private static ItemStack createRoomItem(HotelRoom room, Player viewer) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
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
            lore.add(plugin.getLang().get("gui.common.id", "id", room.getId()));
            lore.add(plugin.getLang().get("gui.common.owner", "owner", room.getOwnerName()));
            lore.add(plugin.getLang().get("gui.common.status", "status", statusColor + room.getStatus().name()));
            if (room.hasActiveDiscount()) {
                lore.add(plugin.getLang().get("gui.browse.price_discount", "price", room.getPrice(), "discount", room.getDiscountPrice()));
                lore.add(room.getDiscountDisplay());
            } else {
                lore.add(plugin.getLang().get("gui.common.price", "price", room.getPrice()));
            }
            lore.add(plugin.getLang().get("gui.common.world", "world", room.getWorldName()));
            lore.add(plugin.getLang().get("gui.browse.size", "volume", room.getVolume()));
            if (!room.getTags().isEmpty()) {
                lore.add(plugin.getLang().get("gui.common.tags", "tags", room.getTagsDisplay()));
            }
            if (room.hasPassword()) {
                lore.add(plugin.getLang().get("gui.common.need_password"));
            }
            if (room.isLocked()) {
                lore.add(plugin.getLang().get("gui.common.locked"));
            }
            if (room.isOccupied() && room.getCurrentGuestName() != null) {
                lore.add(plugin.getLang().get("gui.common.current_guest", "guest", room.getCurrentGuestName()));
            }
            lore.add("");
            lore.add(plugin.getLang().get("gui.browse.checkin_hint"));

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
                meta.setLore(java.util.Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
