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

import java.util.Arrays;

public class RoomManageGUI {

    public static final String GUI_NAME = "room_manage";

    public static void open(Player player, HotelRoom room, HotelsPlugin plugin) {
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME, room), 27, plugin.getLang().get("gui.room_manage.title"));

        inv.setItem(4, createRoomInfoItem(room));

        inv.setItem(10, createItem(Material.GOLD_INGOT, plugin.getLang().get("gui.room_manage.set_price_name"),
                plugin.getLang().get("gui.room_manage.set_price_current", "price", room.getPrice()),
                plugin.getLang().get("gui.room_manage.set_price_hint")));

        inv.setItem(11, createItem(Material.FIREWORK_STAR, plugin.getLang().get("gui.room_manage.discount_name"),
                plugin.getLang().get(room.hasActiveDiscount() ? "gui.room_manage.discount_status_active" : "gui.room_manage.discount_status_unset", "price", room.getDiscountPrice()),
                plugin.getLang().get("gui.room_manage.discount_hint")));

        inv.setItem(12, createItem(Material.NAME_TAG, plugin.getLang().get("gui.room_manage.tag_name"),
                plugin.getLang().get("gui.room_manage.tag_current", "tags", room.getTagsDisplay()),
                plugin.getLang().get("gui.room_manage.tag_hint")));

        inv.setItem(13, createItem(Material.TRIPWIRE_HOOK, plugin.getLang().get("gui.room_manage.pwd_name"),
                plugin.getLang().get(room.hasPassword() ? "gui.room_manage.pwd_status_active" : "gui.room_manage.pwd_status_unset"),
                plugin.getLang().get("gui.room_manage.pwd_hint")));

        inv.setItem(14, createItem(Material.IRON_DOOR, plugin.getLang().get("gui.room_manage.lock_name"),
                plugin.getLang().get(room.isLocked() ? "gui.room_manage.lock_line_unlocked" : "gui.room_manage.lock_line_locked"),
                plugin.getLang().get("gui.room_manage.lock_hint")));

        inv.setItem(15, createItem(Material.REDSTONE, plugin.getLang().get("gui.room_manage.status_name"),
                plugin.getLang().get("gui.room_manage.status_line", "status", getStatusDisplay(room.getStatus())),
                plugin.getLang().get("gui.room_manage.status_hint")));

        inv.setItem(21, createItem(Material.ENDER_PEARL, plugin.getLang().get("gui.room_manage.tp_name"),
                plugin.getLang().get("gui.room_manage.tp_lore")));

        if (room.isOccupied()) {
            inv.setItem(23, createItem(Material.IRON_SWORD, plugin.getLang().get("gui.room_manage.kick_name"),
                    plugin.getLang().get("gui.common.current_guest", "guest", room.getCurrentGuestName()),
                    plugin.getLang().get("gui.room_manage.kick_lore")));
        }

        inv.setItem(26, createItem(Material.ARROW, plugin.getLang().get("gui.common.back_gold"),
                plugin.getLang().get("gui.room_manage.back_lore")));

        player.openInventory(inv);
    }

    private static ItemStack createRoomInfoItem(HotelRoom room) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + room.getName());

            String durationLine = room.getDurationMinutes() == -1
                    ? plugin.getLang().get("gui.room_manage.info_duration_default")
                    : plugin.getLang().get("gui.room_manage.info_duration", "duration", room.getDurationDisplay(0));
            String discountLine = room.hasActiveDiscount()
                    ? plugin.getLang().get("gui.room_manage.info_discount", "price", room.getDiscountPrice())
                    : plugin.getLang().get("gui.room_manage.info_discount_none");
            String lockedLine = plugin.getLang().get("gui.room_manage.info_locked", "value",
                    plugin.getLang().get(room.isLocked() ? "common.yes" : "common.no"));
            String passwordLine = plugin.getLang().get("gui.room_manage.info_password", "value",
                    plugin.getLang().get(room.hasPassword() ? "common.yes" : "common.no"));

            meta.setLore(Arrays.asList(
                    plugin.getLang().get("gui.common.id", "id", room.getId()),
                    plugin.getLang().get("gui.common.status", "status", getStatusDisplay(room.getStatus())),
                    plugin.getLang().get("gui.common.price", "price", room.getPrice()),
                    plugin.getLang().get("gui.common.tags", "tags", room.getTagsDisplay()),
                    durationLine,
                    discountLine,
                    lockedLine,
                    passwordLine
            ));
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

    private static String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return HotelsPlugin.getInstance().getLang().get("command.status.available");
            case OCCUPIED: return HotelsPlugin.getInstance().getLang().get("command.status.occupied");
            case MAINTENANCE: return HotelsPlugin.getInstance().getLang().get("command.status.maintenance");
            default: return HotelsPlugin.getInstance().getLang().get("command.status.unknown");
        }
    }
}
