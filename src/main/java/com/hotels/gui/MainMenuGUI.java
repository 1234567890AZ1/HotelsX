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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainMenuGUI {

    public static final String GUI_NAME = "main_menu";
    private static final String TITLE = "§8§l酒店系统";

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME), 45, TITLE);

        HotelsPlugin plugin = HotelsPlugin.getInstance();

        ItemStack borderTop = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderTop);
        }
        inv.setItem(4, createItem(Material.ENDER_PEARL, "§5§l酒 店 系 统", "§8欢迎使用"));

        ItemStack borderBottom = createItem(Material.PURPLE_STAINED_GLASS_PANE, "§8 ");
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, borderBottom);
        }

        ItemStack borderSide = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 9; i < 36; i += 9) {
            inv.setItem(i, borderSide);
            inv.setItem(i + 8, borderSide);
        }

        inv.setItem(11, createItem(Material.OAK_DOOR, "§d§l我的房间",
                "§7查看和管理你拥有的房间",
                "§7你共有: §f" + countPlayerRooms(player) + " §7个房间",
                "",
                "§8点击查看"));

        inv.setItem(13, createItem(Material.COMPASS, "§d§l浏览房间",
                "§7查看所有可入住的房间",
                "§7当前空闲: §f" + countAvailableRooms() + " §7间",
                "",
                "§8点击浏览"));

        inv.setItem(15, createItem(Material.EMERALD_BLOCK, "§a§l创建新房间",
                "§7使用木斧选择区域后创建",
                "§7① 获取木斧选区",
                "§7② 站在传送点输入 /ht create <名称>",
                "",
                "§8点击开始"));

        inv.setItem(29, createItem(Material.CHEST, "§d§l酒店合集",
                "§7创建和管理房间合集",
                "§7浏览所有玩家创建的酒店",
                "",
                "§8点击进入"));

        inv.setItem(31, createItem(Material.BOOK, "§b§l帮助说明",
                "§7查看酒店系统使用指南",
                "§7命令列表 & 玩法说明",
                "",
                "§8点击查看"));

        inv.setItem(33, createItem(Material.GOLD_BLOCK, "§6§l房间排行榜",
                "§7查看最大的房间排名",
                "",
                "§8点击查看"));

        player.openInventory(inv);
    }

    private static int countPlayerRooms(Player player) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) return 0;
        return plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId()).size();
    }

    private static int countAvailableRooms() {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) return 0;
        return plugin.getRoomStorage().getAvailableRooms().size();
    }

    public static void openRanking(Player player) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) {
            player.sendMessage("§c插件未就绪");
            return;
        }

        List<HotelRoom> allRooms = new ArrayList<>(plugin.getRoomStorage().getAllRooms());
        allRooms.sort((a, b) -> Long.compare(b.getVolume(), a.getVolume()));

        List<HotelRoom> top = allRooms.stream().limit(10).collect(Collectors.toList());

        int size = Math.min(54, Math.max(9, ((top.size() / 9) + 2) * 9));
        Inventory inv = Bukkit.createInventory(new GUIHolder("ranking"), size, "§8§l房间排行榜");

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        int slot = 9;
        int rank = 1;
        for (HotelRoom room : top) {
            Material icon;
            String rankColor;
            if (rank == 1) {
                icon = Material.GOLD_BLOCK;
                rankColor = "§6";
            } else if (rank == 2) {
                icon = Material.IRON_BLOCK;
                rankColor = "§7";
            } else if (rank == 3) {
                icon = Material.COPPER_BLOCK;
                rankColor = "§c";
            } else {
                icon = Material.STONE;
                rankColor = "§8";
            }

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(rankColor + "§l#" + rank + " §f" + room.getName());
                meta.setLore(Arrays.asList(
                        "§7房主: §f" + room.getOwnerName(),
                        "§7大小: §f" + room.getVolume() + " §7方块",
                        "§7状态: " + getStatusDisplay(room.getStatus()),
                        "§7世界: §f" + room.getWorldName()
                ));
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
            rank++;
        }

        inv.setItem(size - 1, createItem(Material.ARROW, "§7§l返回", "§8返回主菜单"));

        player.openInventory(inv);
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
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}