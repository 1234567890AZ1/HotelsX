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
        if (plugin == null) return;

        List<HotelRoom> allRooms = new ArrayList<>(plugin.getRoomStorage().getAllRooms());
        int totalRooms = allRooms.size();
        int totalPages = (totalRooms + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":" + page), 54, plugin.getLang().get("gui.admin.title"));

        ItemStack blackPane = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        ItemStack redPane = createItem(Material.RED_STAINED_GLASS_PANE, "§8 ");
        ItemStack darkGrayPane = createItem(Material.GRAY_STAINED_GLASS_PANE, "§8 ");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, blackPane);
        }

        inv.setItem(0, createItem(Material.COMMAND_BLOCK, plugin.getLang().get("gui.admin.panel_name"),
                plugin.getLang().get("gui.admin.total_rooms", "count", totalRooms),
                plugin.getLang().get("gui.admin.total_collections", "count", plugin.getRoomStorage().getCollectionCount()),
                plugin.getLang().get("gui.admin.current_page", "page", page + 1, "total", totalPages > 0 ? totalPages : 1)));

        inv.setItem(4, createItem(Material.PAPER, plugin.getLang().get("gui.admin.quick_actions"),
                plugin.getLang().get("gui.admin.quick_left"),
                plugin.getLang().get("gui.admin.quick_right"),
                plugin.getLang().get("gui.admin.quick_shift")));

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, redPane);
        }

        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;

        inv.setItem(45, hasPrev ?
                createItem(Material.ARROW, plugin.getLang().get("gui.admin.prev_enabled")) :
                createItem(Material.GRAY_STAINED_GLASS_PANE, plugin.getLang().get("gui.admin.prev_disabled")));

        inv.setItem(47, createItem(Material.BARRIER, plugin.getLang().get("gui.admin.force_delete"), plugin.getLang().get("gui.admin.force_delete_lore")));
        inv.setItem(48, createItem(Material.COMPASS, plugin.getLang().get("gui.common.search_room"), plugin.getLang().get("gui.common.click_search")));
        inv.setItem(49, createItem(Material.ARROW, plugin.getLang().get("gui.common.back_main_button"), plugin.getLang().get("gui.common.click_back")));
        inv.setItem(50, createItem(Material.PAPER, plugin.getLang().get("gui.admin.page", "page", page + 1, "total", totalPages)));
        inv.setItem(51, createItem(Material.EMERALD, plugin.getLang().get("gui.admin.reload_name"), plugin.getLang().get("gui.admin.reload_lore")));

        inv.setItem(53, hasNext ?
                createItem(Material.ARROW, plugin.getLang().get("gui.admin.next_enabled")) :
                createItem(Material.GRAY_STAINED_GLASS_PANE, plugin.getLang().get("gui.admin.next_disabled")));

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalRooms);

        int slot = 9;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot >= 45) break;

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
            lore.add(plugin.getLang().get("gui.common.id", "id", room.getId()));
            lore.add(plugin.getLang().get("gui.common.owner", "owner", room.getOwnerName()));
            lore.add(plugin.getLang().get("gui.common.status", "status", statusColor + room.getStatus().name()));
            lore.add(plugin.getLang().get("gui.common.price", "price", plugin.getEconomyManager().format(room.getPrice())));
            lore.add(plugin.getLang().get("gui.common.world", "world", room.getWorldName()));
            lore.add(plugin.getLang().get("gui.browse.size", "volume", room.getVolume()));
            if (!room.getTags().isEmpty()) {
                lore.add(plugin.getLang().get("gui.common.tags", "tags", room.getTagsDisplay()));
            }
            if (room.isLocked()) {
                lore.add(plugin.getLang().get("gui.common.locked"));
            }
            if (room.hasPassword()) {
                lore.add(plugin.getLang().get("gui.admin.has_password"));
            }
            if (room.isOccupied() && room.getCurrentGuestName() != null) {
                lore.add(plugin.getLang().get("gui.common.current_guest", "guest", room.getCurrentGuestName()));
            }
            lore.add("");
            lore.add(plugin.getLang().get("gui.admin.room_left"));
            lore.add(plugin.getLang().get("gui.admin.room_right"));
            lore.add(plugin.getLang().get("gui.admin.room_shift"));

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
