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

/**
 * 主菜单：酒店礼宾台。
 *
 * <p>视觉结构（暖木 + 黄铜）：
 * <ul>
 *   <li>上下两条琥珀色檐口（{@link #RAIL}）框住台面</li>
 *   <li>四角与左右立柱为深木色（{@link #POST}），中庭为墨黑（{@link #FIELD}）</li>
 *   <li>正中 4 号位是礼宾铃，兼作欢迎牌与实时概览</li>
 *   <li>第一排三件「房间业务」，中庭一块「大堂看板」，第三排三件「后台事务」</li>
 * </ul>
 */
public class MainMenuGUI {

    public static final String GUI_NAME = "main_menu";
    private static final String TITLE = "§6§l酒 店 礼 宾 台";

    /** 檐口：琥珀色玻璃板，读作大堂上方的暖光灯带 */
    private static final Material RAIL = Material.ORANGE_STAINED_GLASS_PANE;
    /** 立柱：深木色玻璃板，构成四角与左右承重柱 */
    private static final Material POST = Material.BROWN_STAINED_GLASS_PANE;
    /** 中庭：墨黑玻璃板，让台面内容向前浮出 */
    private static final Material FIELD = Material.BLACK_STAINED_GLASS_PANE;

    /** 说明文字里的分隔细线：删除线空格是原版界面画线的惯用手法 */
    private static final String RULE = "§8§m                    ";

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME), 45, TITLE);

        fill(inv, RAIL, 1, 2, 3, 5, 6, 7, 37, 38, 39, 40, 41, 42, 43);
        fill(inv, POST, 0, 8, 9, 17, 18, 26, 27, 35, 36, 44);
        fill(inv, FIELD, 10, 12, 14, 16, 19, 20, 21, 23, 24, 25, 28, 30, 32, 34);

        int myRooms = countPlayerRooms(player);
        int available = countAvailableRooms();
        int occupied = countByStatus(HotelRoom.RoomStatus.OCCUPIED);
        int maintenance = countByStatus(HotelRoom.RoomStatus.MAINTENANCE);

        // 台面正中：礼宾铃，兼作欢迎牌与实时概览
        inv.setItem(4, createItem(Material.BELL, "§6§l酒 店 礼 宾 台",
                "§7欢迎回来，§f" + player.getName(),
                RULE,
                "§7我的房间 §f" + myRooms + " §8| §7空闲中 §a" + available,
                RULE,
                "§8请在下方选择要办理的业务"));

        // 第一排：房间业务
        inv.setItem(11, createItem(Material.OAK_DOOR, "§6§l我的房间",
                "§7查看和管理你拥有的房间",
                RULE,
                "§7持有房间 §f" + myRooms + " §7个",
                RULE,
                "§8» 点击进入"));

        inv.setItem(13, createItem(Material.COMPASS, "§6§l浏览房间",
                "§7查看所有可入住的房间",
                RULE,
                "§7当前空闲 §f" + available + " §7间",
                RULE,
                "§8» 点击浏览"));

        inv.setItem(15, createItem(Material.EMERALD_BLOCK, "§6§l创建新房间",
                "§7选区后即可开设新房间",
                RULE,
                "§7第一步 §f手持木斧选两点",
                "§7第二步 §f站在传送点上",
                "§7第三步 §f/ht create <名称>",
                RULE,
                "§8» 点击查看指引"));

        // 中庭：大堂看板
        inv.setItem(22, createItem(Material.OAK_SIGN, "§e§l今日概览",
                "§7礼宾台实时统计",
                RULE,
                "§7房间总数 §f" + countTotalRooms(),
                "§7空闲中 §a" + available,
                "§7已入住 §c" + occupied,
                "§7维护中 §7" + maintenance,
                RULE,
                "§8数据于打开界面时统计"));

        // 第三排：后台事务
        inv.setItem(29, createItem(Material.CHEST, "§f§l酒店合集",
                "§7创建和管理房间合集",
                "§7浏览其他玩家开设的酒店",
                RULE,
                "§8» 点击进入"));

        inv.setItem(31, createItem(Material.BOOK, "§f§l帮助说明",
                "§7查看酒店系统使用指南",
                "§7命令列表与玩法说明",
                RULE,
                "§8» 点击查看"));

        inv.setItem(33, createItem(Material.GOLD_BLOCK, "§f§l房间排行榜",
                "§7查看最大的房间排名",
                RULE,
                "§7榜单长度 §fTOP 10",
                RULE,
                "§8» 点击查看"));

        player.openInventory(inv);
    }

    /** 用同一种玻璃板铺满一组槽位 */
    private static void fill(Inventory inv, Material material, int... slots) {
        ItemStack pane = createItem(material, "§8 ");
        for (int slot : slots) {
            inv.setItem(slot, pane);
        }
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

    private static int countTotalRooms() {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) return 0;
        return plugin.getRoomStorage().getRoomCount();
    }

    private static int countByStatus(HotelRoom.RoomStatus status) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) return 0;
        int count = 0;
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getStatus() == status) {
                count++;
            }
        }
        return count;
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
