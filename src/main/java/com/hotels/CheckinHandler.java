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
package com.hotels;

import com.hotels.model.HotelRoom;
import com.hotels.model.Transaction;
import com.hotels.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 入住处理器 - 处理玩家入住房间的逻辑
 */
public class CheckinHandler {

    private final HotelsPlugin plugin;

    public CheckinHandler(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 尝试入住房间
     */
    public void attemptCheckin(Player player, HotelRoom room) {
        // 检查房间状态
        if (!room.isAvailable()) {
            plugin.getLang().send(player, "checkin.room_unavailable");
            return;
        }

        if (room.isLocked()) {
            plugin.getLang().send(player, "checkin.room_locked");
            return;
        }

        // 检查是否自己的房间
        if (room.getOwner().equals(player.getUniqueId())) {
            plugin.getLang().send(player, "checkin.own_room");
            return;
        }

        // 检查 bypass 权限
        boolean bypass = player.hasPermission("hotels.bypass");

        // 检查密码
        if (room.hasPassword() && !bypass) {
            // 需要输入密码
            plugin.getLang().send(player, "checkin.need_password", "id", room.getId());
            return;
        }

        // 本次入住时长（分钟），0 表示不限时；用于流水统计与到期检测
        int stayDuration = plugin.resolveStayDuration(room);

        // 检查经济
        EconomyManager economy = plugin.getEconomyManager();
        double depositAmount = plugin.getDepositService().resolveAmount(room);
        if (economy.isEnabled()) {
            double currentPrice = room.getCurrentPrice();
            double total = currentPrice + depositAmount;
            double balance = economy.getBalance(player);
            if (balance < total) {
                plugin.getLang().send(player, "checkin.insufficient_funds",
                        "need", plugin.getEconomyManager().format(total),
                        "bal", plugin.getEconomyManager().format(balance));
                return;
            }

            // 扣款（房费 + 押金）
            if (!economy.withdraw(player, total)) {
                plugin.getLang().send(player, "checkin.withdraw_failed");
                return;
            }

            // 客人付款流水
            recordTransaction(Transaction.TxType.CHECKIN_PAY,
                    player.getUniqueId().toString(), player.getName(),
                    currentPrice, room.getId(), room.getName(),
                    "入住付款: " + room.getName(), stayDuration);

            // 押金：记录流水并做房间快照，退房时结算
            if (depositAmount > 0) {
                recordTransaction(Transaction.TxType.DEPOSIT_PAY,
                        player.getUniqueId().toString(), player.getName(),
                        depositAmount, room.getId(), room.getName(),
                        "押金扣押: " + room.getName());
                plugin.getDepositService().hold(room, depositAmount);
                plugin.getLang().send(player, "checkin.deposit_held",
                        "amount", economy.format(depositAmount));
            }

            // 给房主付款（支持托管模式：收益进入待提现余额）
            OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(room.getOwner());
            boolean escrowMode = plugin.getConfig().getBoolean("economy.escrow-mode", false);
            if (escrowMode) {
                // 托管模式：租金进入房主待提现余额，需通过 Web 面板 / /ht claim 提现
                plugin.getEscrowStorage().deposit(room.getOwner().toString(), room.getOwnerName(), currentPrice);

                // 房主收款流水（待提现）
                recordTransaction(Transaction.TxType.CHECKIN_RECV,
                        room.getOwner().toString(), room.getOwnerName(),
                        currentPrice, room.getId(), room.getName(),
                        "收租(待提现): 客人 " + player.getName() + " 入住 " + room.getName(), stayDuration);
            } else {
                // 实时到账模式
                economy.deposit(ownerPlayer, currentPrice);

                // 房主收款流水
                recordTransaction(Transaction.TxType.CHECKIN_RECV,
                        room.getOwner().toString(), room.getOwnerName(),
                        currentPrice, room.getId(), room.getName(),
                        "收租: 客人 " + player.getName() + " 入住 " + room.getName(), stayDuration);
            }

            plugin.log(player, "经济交易: 支付 " + currentPrice + " 给房主 " + room.getOwnerName());
        }

        // 执行入住
        room.setCurrentGuest(player.getUniqueId());
        room.setCurrentGuestName(player.getName());
        room.setStatus(HotelRoom.RoomStatus.OCCUPIED);
        room.setCheckinTime(System.currentTimeMillis());
        room.setCurrentStayMinutes(stayDuration);
        plugin.getRoomStorage().saveRoom(room);

        // 传送玩家到房间
        Location loc = new Location(
                Bukkit.getWorld(room.getWorldName()),
                room.getSpawnX(), room.getSpawnY(), room.getSpawnZ(),
                room.getSpawnYaw(), room.getSpawnPitch()
        );
        com.hotels.util.SchedulerCompat.teleport(player, loc);

        plugin.log(player, "成功入住房间: " + room.getName() + " (ID: " + room.getId() + ")");
        plugin.getLang().send(player, "checkin.success", "room", room.getName());
        plugin.getLang().send(player, "checkin.checkout_hint");

        // 欢迎效果（标题 / ActionBar / 音效，可在 config.yml 关闭）
        sendWelcomeEffects(player, room);

        // 检查时长限制（使用本次入住固定下来的时长）
        int duration = stayDuration;

        if (duration > 0) {
            long durationMs = duration * 60 * 1000L;
            long checkinTime = room.getCheckinTime();
            long expireTime = checkinTime + durationMs;
            plugin.getLang().send(player, "checkin.duration", "minutes", duration);
            plugin.getLang().send(player, "checkin.expire_at", "time",
                    java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                            .format(new java.util.Date(expireTime)));

            // 定时任务检查
            SchedulerCompat.runTaskLater(plugin, () -> {
                // 检查玩家是否还在这个房间
                HotelRoom current = plugin.getRoomStorage().getRoom(room.getId());
                if (current != null && current.isOccupied()
                        && current.getCurrentGuest() != null
                        && current.getCurrentGuest().equals(player.getUniqueId())) {
                    Player p = Bukkit.getPlayer(player.getUniqueId());
                    if (p != null && p.isOnline()) {
                        plugin.getLang().send(p, "checkin.expired");
                    }
                    checkout(player);
                }
            }, duration * 60 * 20L); // duration分钟 * 60秒 * 20tick
        }

        // 通知房主
        Player owner = Bukkit.getPlayer(room.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.getLang().get("checkin.notify_owner",
                    "player", player.getName(), "room", room.getName()));
        }
    }

    /**
     * 入住成功后的欢迎效果：居中大标题 + ActionBar + 升级音效。
     * 各项均可在 config.yml 的 checkin 段独立开关。
     */
    private void sendWelcomeEffects(Player player, HotelRoom room) {
        try {
            if (plugin.getConfig().getBoolean("checkin.welcome-title", true)) {
                player.sendTitle(plugin.getLang().get("checkin.welcome_title"),
                        plugin.getLang().get("checkin.welcome_subtitle", "room", room.getName()),
                        10, 60, 20);
            }
            if (plugin.getConfig().getBoolean("checkin.welcome-actionbar", true)) {
                player.sendActionBar(plugin.getLang().get("checkin.welcome_actionbar",
                        "room", room.getName()));
            }
            if (plugin.getConfig().getBoolean("checkin.welcome-sound", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        } catch (Exception e) {
            // 个别服务端/版本不支持标题或音效时静默降级
            plugin.getLogger().warning("播放入住欢迎效果失败: " + e.getMessage());
        }
    }

    /**
     * 退房（退掉第一个入住的房间）
     */
    public void checkout(Player player) {
        if (player == null) return;

        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(player.getUniqueId())) {
                doCheckout(player, room);
                return;
            }
        }

        plugin.getLang().send(player, "checkin.not_checked_in");
    }

    /**
     * 按房间ID退房
     */
    public void checkout(Player player, String roomId) {
        if (player == null) return;

        HotelRoom room = plugin.getRoomStorage().getRoom(roomId);
        if (room == null) {
            plugin.getLang().send(player, "common.room_not_found");
            return;
        }

        if (room.getCurrentGuest() == null || !room.getCurrentGuest().equals(player.getUniqueId())) {
            plugin.getLang().send(player, "checkin.not_in_this_room");
            return;
        }

        doCheckout(player, room);
    }

    /**
     * 按房间名称退房
     */
    public void checkoutByName(Player player, String roomName) {
        if (player == null) return;

        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getCurrentGuest() != null
                    && room.getCurrentGuest().equals(player.getUniqueId())
                    && room.getName() != null
                    && room.getName().equalsIgnoreCase(roomName)) {
                doCheckout(player, room);
                return;
            }
        }

        plugin.getLang().send(player, "checkin.not_in_named_room", "room", roomName);
    }

    /**
     * 执行退房操作
     */
    private void doCheckout(Player player, HotelRoom room) {
        // 提前退房退款策略：仅在未到期时按房间/全局比例退还部分房费
        long checkinTime = room.getCheckinTime();
        int duration = plugin.resolveStayDuration(room);
        double refund = 0;
        if (duration > 0 && checkinTime > 0) {
            long expireTime = checkinTime + duration * 60 * 1000L;
            if (System.currentTimeMillis() < expireTime) {
                refund = computeEarlyRefund(player, room);
            }
        }

        // 押金结算：对比入住快照，房间被改动则押金赔付房主，否则全额退还
        plugin.getDepositService().settle(player.getUniqueId(), player.getName(), room);

        room.setCurrentGuest(null);
        room.setCurrentGuestName(null);
        room.setStatus(HotelRoom.RoomStatus.AVAILABLE);
        room.setCheckinTime(0);
        room.setCurrentStayMinutes(-1);
        plugin.getRoomStorage().saveRoom(room);

        plugin.log(player, "成功退房: " + room.getName() + " (ID: " + room.getId() + ")");
        plugin.getLang().send(player, "checkin.checkout_success", "room", room.getName());
        if (refund > 0) {
            plugin.getLang().send(player, "checkin.early_refund", "amount", plugin.getEconomyManager().format(refund));
        }
        plugin.getLang().send(player, "checkin.rate_hint", "id", room.getId());

        Player owner = Bukkit.getPlayer(room.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.getLang().get("checkin.notify_owner_checkout",
                    "player", player.getName(), "room", room.getName()));
        }
    }

    /**
     * 计算并发放提前退房退款：按房间（或全局）退款比例退还本次入住已付房费的一部分。
     * 退款金额从房主收益中扣回（实时到账扣钱包，托管模式扣待提现余额）。
     *
     * @return 实际退还给客人的金额
     */
    private double computeEarlyRefund(Player player, HotelRoom room) {
        int percent = room.getEarlyRefundPercent();
        if (percent < 0) {
            percent = plugin.getConfig().getInt("checkout.early-refund-percent", 0);
        }
        if (percent <= 0) return 0;

        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.isEnabled()) return 0;

        // 查找本次入住该客人的付款金额
        double paid = 0;
        for (Transaction tx : plugin.getTransactionStorage().getByRoom(room.getId())) {
            if (tx.getType() == Transaction.TxType.CHECKIN_PAY
                    && player.getUniqueId().toString().equals(tx.getPlayerUUID())) {
                paid = tx.getAmount();
                break; // getByRoom 从新到旧，取最近一次
            }
        }
        if (paid <= 0) return 0;

        double refund = Math.round(paid * percent / 100.0 * 100.0) / 100.0;
        if (refund <= 0) return 0;

        // 从房主侧扣回（金额不足时按可扣额度封顶）
        boolean escrowMode = plugin.getConfig().getBoolean("economy.escrow-mode", false);
        if (escrowMode) {
            double taken = plugin.getEscrowStorage().reverse(room.getOwner().toString(), refund);
            refund = taken;
        } else {
            OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(room.getOwner());
            double balance = economy.getBalance(ownerPlayer);
            refund = Math.min(refund, balance);
            if (refund > 0 && !economy.withdraw(ownerPlayer, refund)) {
                refund = 0;
            }
        }
        if (refund <= 0) return 0;

        economy.deposit(player, refund);
        recordTransaction(Transaction.TxType.EARLY_REFUND,
                player.getUniqueId().toString(), player.getName(),
                refund, room.getId(), room.getName(),
                "提前退房退款(" + percent + "%): " + room.getName());
        plugin.log(player, "提前退房退款: 退还 " + refund + " 给 " + player.getName());
        return refund;
    }

    /**
     * 获取玩家当前入住的所有房间
     */
    public java.util.List<HotelRoom> getPlayerRooms(Player player) {
        java.util.List<HotelRoom> rooms = new java.util.ArrayList<>();
        if (player == null) return rooms;

        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(player.getUniqueId())) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    /**
     * 强制退房（用于自动退房，玩家可能离线）
     */
    public void forceCheckout(UUID guestUUID) {
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(guestUUID)) {
                String guestName = room.getCurrentGuestName();
                // 押金结算（到期自动退房同样检测房间是否被改动）
                plugin.getDepositService().settle(guestUUID, guestName, room);
                room.setCurrentGuest(null);
                room.setCurrentGuestName(null);
                room.setStatus(HotelRoom.RoomStatus.AVAILABLE);
                room.setCheckinTime(0);
                room.setCurrentStayMinutes(-1);
                plugin.getRoomStorage().saveRoom(room);

                plugin.log(null, "自动退房: " + guestName + " 从房间 " + room.getName() + " (ID: " + room.getId() + ")");

                Player owner = Bukkit.getPlayer(room.getOwner());
                if (owner != null && owner.isOnline()) {
                    plugin.getLang().send(owner, "checkin.notify_owner_expired", "player", guestName);
                }
                return;
            }
        }
    }

    /**
     * 记录经济流水
     */
    private void recordTransaction(Transaction.TxType type,
                                   String playerUUID, String playerName,
                                   double amount,
                                   String roomId, String roomName,
                                   String remark) {
        recordTransaction(type, playerUUID, playerName, amount, roomId, roomName, remark, 0);
    }

    /**
     * 记录经济流水（带入住时长，用于入住率统计）
     */
    private void recordTransaction(Transaction.TxType type,
                                   String playerUUID, String playerName,
                                   double amount,
                                   String roomId, String roomName,
                                   String remark,
                                   int stayMinutes) {
        try {
            Transaction tx = new Transaction();
            tx.setType(type);
            tx.setPlayerUUID(playerUUID);
            tx.setPlayerName(playerName != null ? playerName : "未知");
            tx.setAmount(amount);
            tx.setRoomId(roomId);
            tx.setRoomName(roomName);
            tx.setRemark(remark);
            tx.setStayMinutes(stayMinutes);
            plugin.getTransactionStorage().add(tx);
        } catch (Exception e) {
            plugin.getLogger().warning("记录交易流水失败: " + e.getMessage());
        }
    }
}
