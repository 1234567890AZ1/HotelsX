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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 房主经营面板：展示单个房间的入住率、累计收益、平均评分、客源与近 7 天趋势。
 */
public class RoomStatsGUI {

    public static final String GUI_NAME = "room_stats";

    public static void open(Player player, HotelRoom room, HotelsPlugin plugin) {
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME, room), 27,
                plugin.getLang().get("gui.room_stats.title"));

        RoomAnalytics.RoomStats stats = new RoomAnalytics(plugin).compute(room.getId());
        String revenue = plugin.getEconomyManager().format(stats.revenue);
        String spend = plugin.getEconomyManager().format(stats.guestSpend);
        String rate = String.format("%.1f%%", stats.occupancyRate * 100);

        // 顶部：房间经营总览
        inv.setItem(4, createItem(Material.GOLD_BLOCK,
                plugin.getLang().get("gui.room_stats.overview_name", "room", room.getName()),
                plugin.getLang().get("gui.room_stats.overview_line1", "id", room.getId()),
                plugin.getLang().get("gui.room_stats.overview_line2", "status", getStatusDisplay(room.getStatus())),
                plugin.getLang().get("gui.room_stats.overview_line3", "revenue", revenue),
                plugin.getLang().get("gui.room_stats.overview_line4", "checkins", stats.checkinCount),
                plugin.getLang().get("gui.room_stats.overview_line5", "avg", stats.avgRating, "count", stats.ratingCount),
                plugin.getLang().get("gui.room_stats.overview_line6", "rate", rate)));

        // 收益
        inv.setItem(10, createItem(Material.EMERALD,
                plugin.getLang().get("gui.room_stats.revenue_name"),
                plugin.getLang().get("gui.room_stats.revenue_lore", "revenue", revenue),
                plugin.getLang().get("gui.room_stats.revenue_detail", "spend", spend)));

        // 入住次数
        inv.setItem(11, createItem(Material.IRON_DOOR,
                plugin.getLang().get("gui.room_stats.checkin_name"),
                plugin.getLang().get("gui.room_stats.checkin_lore", "count", stats.checkinCount)));

        // 评分
        if (stats.ratingCount > 0) {
            inv.setItem(12, createItem(Material.NETHER_STAR,
                    plugin.getLang().get("gui.room_stats.rating_name"),
                    plugin.getLang().get("gui.room_stats.rating_lore", "avg", stats.avgRating, "count", stats.ratingCount)));
        } else {
            inv.setItem(12, createItem(Material.GLASS,
                    plugin.getLang().get("gui.room_stats.rating_name"),
                    plugin.getLang().get("gui.room_stats.rating_none")));
        }

        // 入住率
        inv.setItem(13, createItem(Material.CLOCK,
                plugin.getLang().get("gui.room_stats.occupancy_name"),
                plugin.getLang().get("gui.room_stats.occupancy_lore", "rate", rate),
                plugin.getLang().get("gui.room_stats.occupancy_hint")));

        // 客源 TOP3
        List<String> guestLore = new ArrayList<>();
        if (stats.topGuests.isEmpty()) {
            guestLore.add(plugin.getLang().get("gui.room_stats.guest_none"));
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> e : stats.topGuests.entrySet()) {
                guestLore.add(plugin.getLang().get("gui.room_stats.guest_line",
                        "rank", rank++, "name", e.getKey(), "count", e.getValue()));
            }
        }
        inv.setItem(14, createItem(Material.PLAYER_HEAD,
                plugin.getLang().get("gui.room_stats.guest_name"),
                guestLore.toArray(new String[0])));

        // 近 7 天趋势
        List<String> trendLore = new ArrayList<>();
        SimpleDateFormat dayFormat = new SimpleDateFormat("MM-dd");
        int maxCount = 1;
        for (int c : stats.last7Days) {
            if (c > maxCount) maxCount = c;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            int count = stats.last7Days[i];
            long ts = now - (6 - i) * 24L * 60L * 60L * 1000L;
            String bar = buildBar(count, maxCount);
            trendLore.add(plugin.getLang().get("gui.room_stats.trend_line",
                    "day", dayFormat.format(new Date(ts)), "bar", bar, "count", count));
        }
        inv.setItem(15, createItem(Material.OAK_SIGN,
                plugin.getLang().get("gui.room_stats.trend_name"),
                trendLore.toArray(new String[0])));

        // 返回
        inv.setItem(26, createItem(Material.ARROW,
                plugin.getLang().get("gui.common.back_gold"),
                plugin.getLang().get("gui.room_stats.back_lore")));

        player.openInventory(inv);
    }

    /** 用方块字符画一条简单的横向柱状条 */
    private static String buildBar(int count, int maxCount) {
        if (count <= 0) return "§8▏";
        int len = Math.max(1, (int) Math.round(count * 10.0 / maxCount));
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < len; i++) sb.append('▮');
        return sb.toString();
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
