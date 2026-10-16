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
package com.hotels.analytics;

import com.hotels.HotelsPlugin;
import com.hotels.model.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营数据统计：从已有的交易流水与评分记录中汇总房主经营指标。
 *
 * <p>所有数据均来源于 {@link com.hotels.storage.TransactionStorage} 与
 * {@link com.hotels.storage.RatingStorage}，不新增额外持久化文件。
 */
public class RoomAnalytics {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int OCCUPANCY_WINDOW_DAYS = 30;

    private final HotelsPlugin plugin;

    public RoomAnalytics(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 单个房间的经营数据快照
     */
    public static class RoomStats {
        public double revenue;         // 房主累计收益
        public double guestSpend;      // 客人累计消费
        public int checkinCount;       // 累计入住次数
        public double avgRating;       // 平均评分（0 表示暂无）
        public int ratingCount;        // 评分数
        public double occupancyRate;   // 近 30 天入住率（0.0 - 1.0）
        public int stayMinutes30d;     // 近 30 天累计入住分钟
        public Map<String, Integer> topGuests = new LinkedHashMap<>(); // 客源 TOP3
        public int[] last7Days = new int[7]; // 索引 0 = 6 天前，6 = 今天

        // 汇总过程中的临时数据，供 computeAll 一次性统计
        private final Map<String, Integer> guestCounts = new HashMap<>();
        private double ratingSum;
        private int ratingTotal;

        public boolean hasData() {
            return checkinCount > 0 || ratingCount > 0 || revenue > 0;
        }
    }

    /**
     * 把一条交易流水累加进统计对象
     */
    private void applyTx(RoomStats stats, Transaction tx, long now, long since30, long since7) {
        boolean recent30 = tx.getTimestamp() >= since30;
        switch (tx.getType()) {
            case CHECKIN_RECV:
                stats.revenue += tx.getAmount();
                break;
            case CHECKIN_PAY:
                stats.guestSpend += tx.getAmount();
                stats.checkinCount++;
                if (tx.getPlayerName() != null && !tx.getPlayerName().isEmpty()) {
                    stats.guestCounts.merge(tx.getPlayerName(), 1, Integer::sum);
                }
                if (recent30) stats.stayMinutes30d += Math.max(0, tx.getStayMinutes());
                break;
            case EXTEND_PAY:
                stats.guestSpend += tx.getAmount();
                if (recent30) stats.stayMinutes30d += Math.max(0, tx.getStayMinutes());
                break;
            default:
                break;
        }

        if (tx.getType() == Transaction.TxType.CHECKIN_PAY && tx.getTimestamp() >= since7) {
            int dayAgo = (int) ((now - tx.getTimestamp()) / DAY_MS);
            if (dayAgo >= 0 && dayAgo < 7) {
                stats.last7Days[6 - dayAgo]++;
            }
        }
    }

    /**
     * 汇总过程中的收尾：客源 TOP3 与入住率换算
     */
    private void finalize(RoomStats stats) {
        stats.avgRating = stats.ratingTotal > 0
                ? Math.round((stats.ratingSum / stats.ratingTotal) * 10) / 10.0 : 0;
        stats.ratingCount = stats.ratingTotal;

        double windowMinutes = OCCUPANCY_WINDOW_DAYS * 24.0 * 60.0;
        stats.occupancyRate = Math.min(1.0, stats.stayMinutes30d / windowMinutes);

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(stats.guestCounts.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < ranked.size() && i < 3; i++) {
            stats.topGuests.put(ranked.get(i).getKey(), ranked.get(i).getValue());
        }
    }

    /**
     * 汇总指定房间的经营数据
     */
    public RoomStats compute(String roomId) {
        long now = System.currentTimeMillis();
        long since30 = now - OCCUPANCY_WINDOW_DAYS * DAY_MS;
        long since7 = now - 7L * DAY_MS;

        RoomStats stats = new RoomStats();
        for (Transaction tx : plugin.getTransactionStorage().getByRoom(roomId)) {
            applyTx(stats, tx, now, since30, since7);
        }
        for (com.hotels.model.Rating rating : plugin.getRatingStorage().getByRoom(roomId)) {
            stats.ratingSum += rating.getScore();
            stats.ratingTotal++;
        }
        finalize(stats);
        return stats;
    }

    /**
     * 一次性汇总所有房间的经营数据（用于全服榜单，避免逐房间重复扫描流水）
     */
    public Map<String, RoomStats> computeAll() {
        long now = System.currentTimeMillis();
        long since30 = now - OCCUPANCY_WINDOW_DAYS * DAY_MS;
        long since7 = now - 7L * DAY_MS;

        Map<String, RoomStats> map = new HashMap<>();
        for (Transaction tx : plugin.getTransactionStorage().getAll()) {
            if (tx.getRoomId() == null) continue;
            RoomStats stats = map.computeIfAbsent(tx.getRoomId(), k -> new RoomStats());
            applyTx(stats, tx, now, since30, since7);
        }
        for (com.hotels.model.Rating rating : plugin.getRatingStorage().getAll()) {
            if (rating.getRoomId() == null) continue;
            RoomStats stats = map.computeIfAbsent(rating.getRoomId(), k -> new RoomStats());
            stats.ratingSum += rating.getScore();
            stats.ratingTotal++;
        }
        for (RoomStats stats : map.values()) {
            finalize(stats);
        }
        return map;
    }
}
