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
package com.hotels.booking;

import com.hotels.EconomyManager;
import com.hotels.HotelsPlugin;
import com.hotels.model.HotelRoom;
import com.hotels.model.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 预订服务：创建预订、到点自动入住、超时自动释放与退款。
 *
 * <p>预订采用预付费模式：下单时按房主设定的「提前预订折扣」扣款并计入房主收益，
 * 到点时若房间可用则自动入住；房间不可用或客人爽约则按规则退款（爽约退款比例可配置）。
 */
public class BookingService {

    private final HotelsPlugin plugin;

    public BookingService(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 创建一个预订
     *
     * @param delayMinutes    距离开始还有多少分钟
     * @param durationMinutes 预订入住时长（分钟）
     */
    public boolean book(Player player, HotelRoom room, int delayMinutes, int durationMinutes) {
        if (!plugin.getConfig().getBoolean("booking.enabled", true)) {
            plugin.getLang().send(player, "booking.disabled");
            return false;
        }
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return false;
        }
        if (!room.isBookable()) {
            plugin.getLang().send(player, "booking.room_not_bookable");
            return false;
        }
        if (room.getOwner().equals(player.getUniqueId())) {
            plugin.getLang().send(player, "booking.own_room");
            return false;
        }
        if (!room.isAvailable()) {
            plugin.getLang().send(player, "booking.room_unavailable");
            return false;
        }
        if (plugin.getBookingStorage().hasOpenBooking(room.getId())) {
            plugin.getLang().send(player, "booking.room_has_booking");
            return false;
        }
        int maxAdvanceDays = plugin.getConfig().getInt("booking.max-advance-days", 7);
        if (delayMinutes < 1) {
            plugin.getLang().send(player, "booking.delay_positive");
            return false;
        }
        if (delayMinutes > maxAdvanceDays * 1440) {
            plugin.getLang().send(player, "booking.too_far", "days", maxAdvanceDays);
            return false;
        }
        if (durationMinutes < 1) {
            plugin.getLang().send(player, "booking.duration_positive");
            return false;
        }

        int percent = room.getBookingDiscountPercent();
        double price = Math.round(room.getPrice() * percent / 100.0 * 100.0) / 100.0;
        EconomyManager economy = plugin.getEconomyManager();
        if (economy.isEnabled()) {
            double balance = economy.getBalance(player);
            if (balance < price) {
                plugin.getLang().send(player, "booking.insufficient_funds",
                        "need", economy.format(price), "bal", economy.format(balance));
                return false;
            }
            if (price > 0 && !economy.withdraw(player, price)) {
                plugin.getLang().send(player, "booking.withdraw_failed");
                return false;
            }
            // 客人预订付款流水
            recordTx(Transaction.TxType.BOOKING_PAY, player.getUniqueId().toString(), player.getName(),
                    price, room, "预订付款: " + room.getName());
            // 房主收款
            creditOwner(room, price, "预订收款: 客人 " + player.getName() + " 预订 " + room.getName());
        }

        Booking b = new Booking();
        b.setRoomId(room.getId());
        b.setRoomName(room.getName());
        b.setOwnerUuid(room.getOwner().toString());
        b.setOwnerName(room.getOwnerName());
        b.setGuestUuid(player.getUniqueId().toString());
        b.setGuestName(player.getName());
        b.setStartTime(System.currentTimeMillis() + delayMinutes * 60L * 1000L);
        b.setDurationMinutes(durationMinutes);
        b.setPaidAmount(price);
        plugin.getBookingStorage().add(b);

        plugin.getLang().send(player, "booking.success",
                "id", b.getId(), "room", room.getName(),
                "time", formatTime(b.getStartTime()), "minutes", durationMinutes,
                "amount", economy.format(price));
        return true;
    }

    /**
     * 取消自己的预订（等待开始状态）
     */
    public boolean cancel(Player player, String bookingId) {
        Booking b = plugin.getBookingStorage().getById(bookingId);
        if (b == null) {
            plugin.getLang().send(player, "booking.not_found");
            return false;
        }
        if (!player.getUniqueId().toString().equals(b.getGuestUuid())) {
            plugin.getLang().send(player, "booking.not_yours");
            return false;
        }
        if (b.getStatus() != Booking.Status.PENDING) {
            plugin.getLang().send(player, "booking.cannot_cancel");
            return false;
        }
        refund(b, 100, Booking.Status.CANCELLED, "booking.notify_cancelled");
        return true;
    }

    /**
     * 周期任务：处理到点的预订
     */
    public void processDueBookings() {
        long now = System.currentTimeMillis();
        int noShowMinutes = plugin.getConfig().getInt("booking.no-show-minutes", 15);
        int noShowRefund = plugin.getConfig().getInt("booking.no-show-refund-percent", 0);

        for (Booking b : plugin.getBookingStorage().getAll()) {
            if (b.getStatus() != Booking.Status.PENDING) continue;
            if (now < b.getStartTime()) continue;

            HotelRoom room = plugin.getRoomStorage().getRoom(b.getRoomId());
            if (room == null) {
                refund(b, 100, Booking.Status.FAILED, "booking.notify_room_missing");
                continue;
            }
            // 客人迟迟未到：按配置比例退款后释放
            if (now - b.getStartTime() > noShowMinutes * 60L * 1000L) {
                refund(b, noShowRefund, Booking.Status.NO_SHOW, "booking.notify_no_show");
                continue;
            }
            // 房间被占用 / 锁定，无法入住
            if (!room.isAvailable() || room.isLocked()) {
                refund(b, 100, Booking.Status.FAILED, "booking.notify_failed");
                continue;
            }
            activate(b, room);
        }
    }

    /**
     * 到点入住：沿用预订指定的时长
     */
    private void activate(Booking b, HotelRoom room) {
        room.setCurrentGuest(UUID.fromString(b.getGuestUuid()));
        room.setCurrentGuestName(b.getGuestName());
        room.setStatus(HotelRoom.RoomStatus.OCCUPIED);
        room.setCheckinTime(System.currentTimeMillis());
        room.setCurrentStayMinutes(b.getDurationMinutes());
        plugin.getRoomStorage().saveRoom(room);

        b.setStatus(Booking.Status.ACTIVE);
        plugin.getBookingStorage().update(b);

        // 记一条 0 金额入住流水，用于经营统计的入住次数与入住率（房费已在预订时收取）
        Transaction tx = new Transaction();
        tx.setType(Transaction.TxType.CHECKIN_PAY);
        tx.setPlayerUUID(b.getGuestUuid());
        tx.setPlayerName(b.getGuestName());
        tx.setAmount(0);
        tx.setRoomId(room.getId());
        tx.setRoomName(room.getName());
        tx.setRemark("预订入住(已预付): " + room.getName());
        tx.setStayMinutes(b.getDurationMinutes());
        plugin.getTransactionStorage().add(tx);

        plugin.log(null, "预订到点入住: " + b.getGuestName() + " 入住 " + room.getName() + " (预订 " + b.getId() + ")");

        Player guest = Bukkit.getPlayer(UUID.fromString(b.getGuestUuid()));
        if (guest != null && guest.isOnline()) {
            Location loc = new Location(Bukkit.getWorld(room.getWorldName()),
                    room.getSpawnX(), room.getSpawnY(), room.getSpawnZ(),
                    room.getSpawnYaw(), room.getSpawnPitch());
            com.hotels.util.SchedulerCompat.teleport(guest, loc);
            plugin.getLang().send(guest, "booking.activated", "room", room.getName());
        }
        Player owner = Bukkit.getPlayer(room.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.getLang().get("booking.notify_owner_activated",
                    "player", b.getGuestName(), "room", room.getName()));
        }
    }

    /**
     * 退款并结束预订：按比例把已付金额退还给客人（从房主侧冲正）
     */
    private void refund(Booking b, int refundPercent, Booking.Status status, String notifyKey) {
        double refund = Math.round(b.getPaidAmount() * refundPercent / 100.0 * 100.0) / 100.0;
        EconomyManager economy = plugin.getEconomyManager();

        if (refund > 0 && economy.isEnabled()) {
            boolean escrowMode = plugin.getConfig().getBoolean("economy.escrow-mode", false);
            double clawed;
            if (escrowMode) {
                clawed = plugin.getEscrowStorage().reverse(b.getOwnerUuid(), refund);
            } else {
                OfflinePlayer owner = b.getOwnerUuid() != null ? Bukkit.getOfflinePlayer(UUID.fromString(b.getOwnerUuid())) : null;
                double balance = owner != null ? economy.getBalance(owner) : 0;
                clawed = Math.min(refund, balance);
                if (clawed > 0 && owner != null && !economy.withdraw(owner, clawed)) {
                    clawed = 0;
                }
            }
            refund = clawed;
            if (refund > 0) {
                Player guest = Bukkit.getPlayer(UUID.fromString(b.getGuestUuid()));
                OfflinePlayer guestOffline = Bukkit.getOfflinePlayer(UUID.fromString(b.getGuestUuid()));
                if (guest != null && guest.isOnline()) {
                    economy.deposit(guest, refund);
                } else {
                    economy.deposit(guestOffline, refund);
                }
                recordTx(Transaction.TxType.BOOKING_REFUND, b.getGuestUuid(), b.getGuestName(),
                        refund, null, b.getRoomName(), "预订退款(" + refundPercent + "%): " + b.getRoomName());
            }
        }

        b.setStatus(status);
        plugin.getBookingStorage().update(b);
        plugin.log(null, "预订结束(" + status.name() + "): " + b.getGuestName() + " - " + b.getRoomName()
                + "，退款 " + refund);

        Player guest = Bukkit.getPlayer(UUID.fromString(b.getGuestUuid()));
        if (guest != null && guest.isOnline()) {
            plugin.getLang().send(guest, notifyKey, "room", b.getRoomName(),
                    "amount", economy.format(refund));
        }
    }

    /**
     * 玩家未结束的预订列表
     */
    public List<Booking> getOpenBookings(Player player) {
        List<Booking> list = new ArrayList<>();
        for (Booking b : plugin.getBookingStorage().getByGuest(player.getUniqueId().toString())) {
            if (b.getStatus() == Booking.Status.PENDING || b.getStatus() == Booking.Status.ACTIVE) {
                list.add(b);
            }
        }
        return list;
    }

    private void creditOwner(HotelRoom room, double amount, String remark) {
        EconomyManager economy = plugin.getEconomyManager();
        boolean escrowMode = plugin.getConfig().getBoolean("economy.escrow-mode", false);
        if (escrowMode) {
            plugin.getEscrowStorage().deposit(room.getOwner().toString(), room.getOwnerName(), amount);
            recordTx(Transaction.TxType.CHECKIN_RECV, room.getOwner().toString(), room.getOwnerName(),
                    amount, room, remark + "(待提现)");
        } else {
            OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(room.getOwner());
            economy.deposit(ownerPlayer, amount);
            recordTx(Transaction.TxType.CHECKIN_RECV, room.getOwner().toString(), room.getOwnerName(),
                    amount, room, remark);
        }
    }

    private void recordTx(Transaction.TxType type, String uuid, String name, double amount,
                          HotelRoom room, String remark) {
        recordTx(type, uuid, name, amount, room != null ? room.getId() : null,
                room != null ? room.getName() : null, remark);
    }

    private void recordTx(Transaction.TxType type, String uuid, String name, double amount,
                          String roomId, String roomName, String remark) {
        try {
            Transaction tx = new Transaction();
            tx.setType(type);
            tx.setPlayerUUID(uuid);
            tx.setPlayerName(name != null ? name : "未知");
            tx.setAmount(amount);
            tx.setRoomId(roomId);
            tx.setRoomName(roomName);
            tx.setRemark(remark);
            plugin.getTransactionStorage().add(tx);
        } catch (Exception e) {
            plugin.getLogger().warning("记录预订流水失败: " + e.getMessage());
        }
    }

    private String formatTime(long time) {
        return new SimpleDateFormat("MM-dd HH:mm").format(new Date(time));
    }
}
