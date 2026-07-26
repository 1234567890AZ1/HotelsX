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
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME, room), 27, "§8§l房间管理");

        inv.setItem(4, createRoomInfoItem(room));

        inv.setItem(10, createItem(Material.GOLD_INGOT, "§e§l设置价格",
                "§7当前价格: §f" + room.getPrice(),
                "§e点击设置新价格"));

        String discountStatus = room.hasActiveDiscount() ? "§a进行中 §7(¥" + room.getDiscountPrice() + ")" : "§7未设置";
        inv.setItem(11, createItem(Material.FIREWORK_STAR, "§e§l限时折扣",
                "§7状态: " + discountStatus,
                "§e点击设置折扣价和时长"));

        inv.setItem(12, createItem(Material.NAME_TAG, "§e§l设置标签",
                "§7当前: " + room.getTagsDisplay(),
                "§e点击设置标签（最多3个）"));

        String pwStatus = room.hasPassword() ? "§c已设置" : "§7未设置";
        inv.setItem(13, createItem(Material.TRIPWIRE_HOOK, "§e§l设置密码",
                "§7密码状态: " + pwStatus,
                "§e点击设置或清除密码"));

        String lockStatus = room.isLocked() ? "§a已解锁" : "§c已锁定";
        inv.setItem(14, createItem(Material.IRON_DOOR, "§e§l切换锁定",
                "§7当前: " + lockStatus,
                "§e点击切换"));

        inv.setItem(15, createItem(Material.REDSTONE, "§e§l切换状态",
                "§7当前: " + getStatusDisplay(room.getStatus()),
                "§e点击切换状态"));

        inv.setItem(21, createItem(Material.ENDER_PEARL, "§d§l传送至房间",
                "§7传送到房间入口"));

        if (room.isOccupied()) {
            inv.setItem(23, createItem(Material.IRON_SWORD, "§c§l踢出客人",
                    "§7当前客人: §f" + room.getCurrentGuestName(),
                    "§c点击踢出客人"));
        }

        inv.setItem(26, createItem(Material.ARROW, "§7§l返回",
                "§7返回我的房间"));

        player.openInventory(inv);
    }

    private static ItemStack createRoomInfoItem(HotelRoom room) {
        ItemStack item = new ItemStack(Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + room.getName());
            meta.setLore(Arrays.asList(
                    "§7ID: §f" + room.getId(),
                    "§7状态: " + getStatusDisplay(room.getStatus()),
                    "§7价格: §f" + room.getPrice(),
                    "§7标签: " + room.getTagsDisplay(),
                    "§7时长: " + (room.getDurationMinutes() == -1 ? "§7使用合集默认" : room.getDurationDisplay(0)),
                    "§7折扣: " + (room.hasActiveDiscount() ? "§a¥" + room.getDiscountPrice() : "§7无"),
                    "§7锁定: " + (room.isLocked() ? "§c是" : "§a否"),
                    "§7密码: " + (room.hasPassword() ? "§c是" : "§a否")
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
            case AVAILABLE: return "§a空闲";
            case OCCUPIED: return "§c已入住";
            case MAINTENANCE: return "§7维护中";
            default: return "§7未知";
        }
    }
}