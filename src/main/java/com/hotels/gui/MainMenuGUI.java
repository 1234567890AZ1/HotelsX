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

    /** 檐口：琥珀色玻璃板，读作大堂上方的暖光灯带 */
    private static final Material RAIL = Material.ORANGE_STAINED_GLASS_PANE;
    /** 立柱：深木色玻璃板，构成四角与左右承重柱 */
    private static final Material POST = Material.BROWN_STAINED_GLASS_PANE;
    /** 中庭：墨黑玻璃板，让台面内容向前浮出 */
    private static final Material FIELD = Material.BLACK_STAINED_GLASS_PANE;

    /** 说明文字里的分隔细线：删除线空格是原版界面画线的惯用手法 */
    private static final String RULE = "§8§m                    ";

    public static void open(Player player) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME), 45, plugin.getLang().get("gui.main_menu.title"));

        fill(inv, RAIL, 1, 2, 3, 5, 6, 7, 37, 38, 39, 40, 41, 42, 43);
        fill(inv, POST, 0, 8, 9, 17, 18, 26, 27, 35, 36, 44);
        fill(inv, FIELD, 10, 12, 14, 16, 19, 20, 21, 23, 24, 25, 28, 30, 32, 34);

        int myRooms = countPlayerRooms(player);
        int available = countAvailableRooms();
        int occupied = countByStatus(HotelRoom.RoomStatus.OCCUPIED);
        int maintenance = countByStatus(HotelRoom.RoomStatus.MAINTENANCE);

        // 台面正中：礼宾铃，兼作欢迎牌与实时概览
        inv.setItem(4, createItem(Material.BELL, plugin.getLang().get("gui.main_menu.title"),
                plugin.getLang().get("gui.main_menu.welcome", "player", player.getName()),
                RULE,
                plugin.getLang().get("gui.main_menu.summary", "rooms", myRooms, "available", available),
                RULE,
                plugin.getLang().get("gui.main_menu.prompt")));

        // 第一排：房间业务
        inv.setItem(11, createItem(Material.OAK_DOOR, plugin.getLang().get("gui.main_menu.my_rooms_name"),
                plugin.getLang().get("gui.main_menu.my_rooms_lore1"),
                RULE,
                plugin.getLang().get("gui.main_menu.my_rooms_lore2", "count", myRooms),
                RULE,
                plugin.getLang().get("gui.main_menu.click_enter")));

        inv.setItem(13, createItem(Material.COMPASS, plugin.getLang().get("gui.main_menu.browse_name"),
                plugin.getLang().get("gui.main_menu.browse_lore1"),
                RULE,
                plugin.getLang().get("gui.main_menu.browse_lore2", "count", available),
                RULE,
                plugin.getLang().get("gui.main_menu.click_browse")));

        inv.setItem(15, createItem(Material.EMERALD_BLOCK, plugin.getLang().get("gui.main_menu.create_name"),
                plugin.getLang().get("gui.main_menu.create_lore1"),
                RULE,
                plugin.getLang().get("gui.main_menu.create_lore2"),
                plugin.getLang().get("gui.main_menu.create_lore3"),
                plugin.getLang().get("gui.main_menu.create_lore4"),
                RULE,
                plugin.getLang().get("gui.main_menu.create_lore5")));

        // 中庭：大堂看板
        inv.setItem(22, createItem(Material.OAK_SIGN, plugin.getLang().get("gui.main_menu.overview_name"),
                plugin.getLang().get("gui.main_menu.overview_lore1"),
                RULE,
                plugin.getLang().get("gui.main_menu.overview_lore2", "count", countTotalRooms()),
                plugin.getLang().get("gui.main_menu.overview_lore3", "count", available),
                plugin.getLang().get("gui.main_menu.overview_lore4", "count", occupied),
                plugin.getLang().get("gui.main_menu.overview_lore5", "count", maintenance),
                RULE,
                plugin.getLang().get("gui.main_menu.overview_lore6")));

        // 第三排：后台事务
        inv.setItem(29, createItem(Material.CHEST, plugin.getLang().get("gui.main_menu.collection_name"),
                plugin.getLang().get("gui.main_menu.collection_lore1"),
                plugin.getLang().get("gui.main_menu.collection_lore2"),
                RULE,
                plugin.getLang().get("gui.main_menu.click_enter")));

        inv.setItem(31, createItem(Material.BOOK, plugin.getLang().get("gui.main_menu.help_name"),
                plugin.getLang().get("gui.main_menu.help_lore1"),
                plugin.getLang().get("gui.main_menu.help_lore2"),
                RULE,
                plugin.getLang().get("gui.main_menu.click_view")));

        inv.setItem(33, createItem(Material.GOLD_BLOCK, plugin.getLang().get("gui.main_menu.ranking_name"),
                plugin.getLang().get("gui.main_menu.ranking_lore1"),
                RULE,
                plugin.getLang().get("gui.main_menu.ranking_lore2"),
                RULE,
                plugin.getLang().get("gui.main_menu.click_view")));

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
        if (plugin == null) return;

        List<HotelRoom> allRooms = new ArrayList<>(plugin.getRoomStorage().getAllRooms());
        allRooms.sort((a, b) -> Long.compare(b.getVolume(), a.getVolume()));

        List<HotelRoom> top = allRooms.stream().limit(10).collect(Collectors.toList());

        int size = Math.min(54, Math.max(9, ((top.size() / 9) + 2) * 9));
        Inventory inv = Bukkit.createInventory(new GUIHolder("ranking"), size, plugin.getLang().get("gui.ranking.title"));

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
                        plugin.getLang().get("gui.common.owner", "owner", room.getOwnerName()),
                        plugin.getLang().get("gui.ranking.size", "volume", room.getVolume()),
                        plugin.getLang().get("gui.common.status", "status", getStatusDisplay(room.getStatus())),
                        plugin.getLang().get("gui.common.world", "world", room.getWorldName())
                ));
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
            rank++;
        }

        inv.setItem(size - 1, createItem(Material.ARROW,
                plugin.getLang().get("gui.common.back_gold"),
                plugin.getLang().get("gui.common.back_to_main_lore")));

        player.openInventory(inv);
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
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
