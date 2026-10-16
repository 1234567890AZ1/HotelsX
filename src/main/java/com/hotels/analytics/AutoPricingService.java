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
import com.hotels.model.HotelRoom;
import com.hotels.model.Transaction;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 时段自动定价：按高峰时段 / 周末上浮、长时间空置自动打折。
 *
 * <p>复用房已有的 {@code discountPrice / discountExpire} 字段：上浮时把
 * discountPrice 设置为高于原价的数值即可通过 {@link HotelRoom#getCurrentPrice()} 生效。
 * 自动写入的折扣会打上 {@code autoDiscount} 标记，避免覆盖房主手动设置的折扣。
 */
public class AutoPricingService {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private final HotelsPlugin plugin;

    public AutoPricingService(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 周期性执行：刷新所有已启用自动定价房间的价格
     */
    public void applyAutoPricing() {
        boolean globalEnabled = plugin.getConfig().getBoolean("auto-pricing.enabled", true);
        int peakStart = plugin.getConfig().getInt("auto-pricing.peak-start", 18);
        int peakEnd = plugin.getConfig().getInt("auto-pricing.peak-end", 23);
        double peakMult = plugin.getConfig().getDouble("auto-pricing.peak-multiplier", 1.2);
        double weekendMult = plugin.getConfig().getDouble("auto-pricing.weekend-multiplier", 1.2);
        int idleDays = plugin.getConfig().getInt("auto-pricing.idle-days", 7);
        double idleDiscount = plugin.getConfig().getDouble("auto-pricing.idle-discount", 0.8);
        int duration = Math.max(1, plugin.getConfig().getInt("auto-pricing.duration-minutes", 120));

        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        boolean isPeak = peakStart <= peakEnd
                ? (hour >= peakStart && hour < peakEnd)
                : (hour >= peakStart || hour < peakEnd); // 跨零点时段
        DayOfWeek dow = now.getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

        double multiplier = 1.0;
        if (isPeak) multiplier *= peakMult;
        if (isWeekend) multiplier *= weekendMult;

        Map<String, Long> lastCheckin = lastCheckinTimes();
        long nowMs = System.currentTimeMillis();

        boolean changed = false;
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (!room.isAutoPricingEnabled(globalEnabled)) continue;

            // 已入住房间不改价（当前住客按入住时价格结算）
            if (room.getStatus() != HotelRoom.RoomStatus.AVAILABLE) continue;

            // 房主手动折扣优先，不被自动定价覆盖
            if (room.hasActiveDiscount() && !room.isAutoDiscount()) continue;

            Long last = lastCheckin.get(room.getId());
            long idleSince = last != null ? last : room.getCreatedTime();
            boolean idle = idleDays > 0 && nowMs - idleSince > idleDays * DAY_MS;

            if (multiplier > 1.0) {
                // 高峰/周末上浮优先于空置折扣
                double surge = round2(room.getPrice() * multiplier);
                if (Math.abs(surge - room.getPrice()) < 0.01) continue; // 倍数无效
                if (!room.isAutoDiscount() || !room.hasActiveDiscount()
                        || Math.abs(room.getDiscountPrice() - surge) > 0.01) {
                    room.setAutoDiscount(surge, duration);
                    changed = true;
                }
            } else if (idle && idleDiscount > 0 && idleDiscount < 1.0) {
                double discounted = round2(room.getPrice() * idleDiscount);
                if (!room.isAutoDiscount() || !room.hasActiveDiscount()
                        || Math.abs(room.getDiscountPrice() - discounted) > 0.01) {
                    room.setAutoDiscount(discounted, duration);
                    changed = true;
                }
            } else if (room.isAutoDiscount()) {
                // 无上浮也无空置折扣：清理自动折扣
                room.clearDiscount();
                changed = true;
            }
        }

        if (changed) {
            plugin.getRoomStorage().saveAll();
        }
    }

    /**
     * 每个房间最近一次入住付款的时间戳（用于判断空置时长）
     */
    private Map<String, Long> lastCheckinTimes() {
        Map<String, Long> map = new HashMap<>();
        for (Transaction tx : plugin.getTransactionStorage().getAll()) {
            if (tx.getType() != Transaction.TxType.CHECKIN_PAY || tx.getRoomId() == null) continue;
            Long cur = map.get(tx.getRoomId());
            if (cur == null || tx.getTimestamp() > cur) {
                map.put(tx.getRoomId(), tx.getTimestamp());
            }
        }
        return map;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
