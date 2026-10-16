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
import com.hotels.analytics.RoomAnalytics;
import com.hotels.model.HotelRoom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 全服酒店总览榜：收益榜 / 好评榜 / 入住率榜，三榜一键切换。
 */
public class RankingGUI {

    public static final String GUI_NAME = "ranking";

    public static final int TAB_REVENUE = 0;
    public static final int TAB_RATING = 1;
    public static final int TAB_OCCUPANCY = 2;

    /** 打开指定榜单页 */
    public static void open(Player player, int tab) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        if (plugin == null) return;

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME, tab), 54,
                plugin.getLang().get("gui.ranking.title"));

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        // 三个榜单切换页签
        inv.setItem(0, createTab(Material.GOLD_INGOT, "gui.ranking.tab_revenue", tab == TAB_REVENUE, plugin));
        inv.setItem(1, createTab(Material.NETHER_STAR, "gui.ranking.tab_rating", tab == TAB_RATING, plugin));
        inv.setItem(2, createTab(Material.CLOCK, "gui.ranking.tab_occupancy", tab == TAB_OCCUPANCY, plugin));

        inv.setItem(4, createItem(Material.BOOK, plugin.getLang().get("gui.ranking.footer"),
                plugin.getLang().get("gui.ranking.tab_hint")));

        // 汇总所有房间数据并排序
        List<HotelRoom> rooms = new ArrayList<>(plugin.getRoomStorage().getAllRooms());
        RoomAnalytics.RoomStats empty = new RoomAnalytics.RoomStats();
        Map<String, RoomAnalytics.RoomStats> all = new RoomAnalytics(plugin).computeAll();

        String tabKey;
        switch (tab) {
            case TAB_RATING:
                rooms.sort(Comparator
                        .comparingDouble((HotelRoom r) -> all.getOrDefault(r.getId(), empty).avgRating).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (HotelRoom r) -> all.getOrDefault(r.getId(), empty).ratingCount).reversed()));
                tabKey = "rating";
                break;
            case TAB_OCCUPANCY:
                rooms.sort(Comparator
                        .comparingDouble((HotelRoom r) -> all.getOrDefault(r.getId(), empty).occupancyRate).reversed());
                tabKey = "occupancy";
                break;
            case TAB_REVENUE:
            default:
                rooms.sort(Comparator
                        .comparingDouble((HotelRoom r) -> all.getOrDefault(r.getId(), empty).revenue).reversed());
                tabKey = "revenue";
                break;
        }

        int shown = 0;
        for (HotelRoom room : rooms) {
            if (shown >= 10) break;
            RoomAnalytics.RoomStats stats = all.getOrDefault(room.getId(), empty);

            // 好评榜与入住率榜只展示有数据（评分/入住）的房间，避免大量 0 分房间刷屏
            if (tab == TAB_RATING && stats.ratingCount == 0) continue;
            if (tab == TAB_OCCUPANCY && stats.checkinCount == 0) continue;

            shown++;
            int rank = shown;
            Material icon = medalIcon(rank);
            String rankColor = rankColor(rank);

            List<String> lore = new ArrayList<>();
            lore.add(plugin.getLang().get("gui.common.owner", "owner", room.getOwnerName()));
            if (tab == TAB_REVENUE) {
                lore.add(plugin.getLang().get("gui.ranking.entry_revenue",
                        "value", plugin.getEconomyManager().format(stats.revenue)));
            } else if (tab == TAB_RATING) {
                lore.add(plugin.getLang().get("gui.ranking.entry_rating",
                        "value", stats.avgRating, "count", stats.ratingCount));
            } else {
                lore.add(plugin.getLang().get("gui.ranking.entry_occupancy",
                        "value", String.format("%.1f%%", stats.occupancyRate * 100)));
            }
            lore.add(plugin.getLang().get("gui.ranking.entry_stay", "count", stats.checkinCount));
            lore.add(plugin.getLang().get("gui.common.status", "status", getStatusDisplay(room.getStatus())));

            ItemStack item = createItem(icon,
                    plugin.getLang().get("gui.ranking.entry_name",
                            "color", rankColor, "rank", rank, "room", room.getName()),
                    lore.toArray(new String[0]));
            inv.setItem(8 + rank, item);
        }

        if (shown == 0) {
            inv.setItem(22, createItem(Material.GLASS, plugin.getLang().get("gui.ranking.empty")));
        }

        inv.setItem(49, createItem(Material.ARROW,
                plugin.getLang().get("gui.common.back_gold"),
                plugin.getLang().get("gui.common.back_to_main_lore")));

        player.openInventory(inv);
    }

    private static ItemStack createTab(Material material, String key, boolean active, HotelsPlugin plugin) {
        ItemStack item = createItem(material,
                plugin.getLang().get(active ? key + "_active" : key),
                plugin.getLang().get("gui.ranking.tab_click"));
        return item;
    }

    private static Material medalIcon(int rank) {
        if (rank == 1) return Material.GOLD_BLOCK;
        if (rank == 2) return Material.IRON_BLOCK;
        if (rank == 3) return Material.COPPER_BLOCK;
        return Material.STONE;
    }

    private static String rankColor(int rank) {
        switch (rank) {
            case 1: return "§6";
            case 2: return "§7";
            case 3: return "§c";
            default: return "§8";
        }
    }

    private static String getStatusDisplay(HotelRoom.RoomStatus status) {
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        switch (status) {
            case AVAILABLE: return plugin.getLang().get("command.status.available");
            case OCCUPIED: return plugin.getLang().get("command.status.occupied");
            case MAINTENANCE: return plugin.getLang().get("command.status.maintenance");
            default: return plugin.getLang().get("command.status.unknown");
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
