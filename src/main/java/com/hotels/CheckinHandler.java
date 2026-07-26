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
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
            player.sendMessage("§c该房间当前不可用");
            return;
        }

        if (room.isLocked()) {
            player.sendMessage("§c该房间已上锁");
            return;
        }

        // 检查是否自己的房间
        if (room.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c你不能入住自己的房间");
            return;
        }

        // 检查 bypass 权限
        boolean bypass = player.hasPermission("hotels.bypass");

        // 检查密码
        if (room.hasPassword() && !bypass) {
            // 需要输入密码
            player.sendMessage("§e该房间需要密码才能入住，请输入 §6/ht checkin " + room.getId() + " <密码>");
            return;
        }

        // 检查经济
        EconomyManager economy = plugin.getEconomyManager();
        if (economy.isEnabled()) {
            double currentPrice = room.getCurrentPrice();
            double balance = economy.getBalance(player);
            if (balance < currentPrice) {
                player.sendMessage("§c余额不足！需要 " + plugin.getEconomyManager().format(currentPrice)
                        + "，你只有 " + plugin.getEconomyManager().format(balance));
                return;
            }

            // 扣款
            if (!economy.withdraw(player, currentPrice)) {
                player.sendMessage("§c扣款失败");
                return;
            }

            // 给房主付款
            economy.deposit(Bukkit.getOfflinePlayer(room.getOwner()), currentPrice);

            plugin.log(player, "经济交易: 支付 " + currentPrice + " 给房主 " + room.getOwnerName());
        }

        // 执行入住
        room.setCurrentGuest(player.getUniqueId());
        room.setCurrentGuestName(player.getName());
        room.setStatus(HotelRoom.RoomStatus.OCCUPIED);
        room.setCheckinTime(System.currentTimeMillis());
        plugin.getRoomStorage().saveRoom(room);

        // 传送玩家到房间
        Location loc = new Location(
                Bukkit.getWorld(room.getWorldName()),
                room.getSpawnX(), room.getSpawnY(), room.getSpawnZ(),
                room.getSpawnYaw(), room.getSpawnPitch()
        );
        player.teleport(loc);

        plugin.log(player, "成功入住房间: " + room.getName() + " (ID: " + room.getId() + ")");
        player.sendMessage("§a成功入住房间 §e" + room.getName() + "§a！");
        player.sendMessage("§7输入 §e/ht checkout §7退房");

        // 检查时长限制
        int duration = room.getDurationMinutes();
        if (duration == -1) {
            // 尝试从合集获取时长
            for (com.hotels.model.RoomCollection col : plugin.getRoomStorage().getAllCollections()) {
                if (col.getRoomIds().contains(room.getId())) {
                    duration = col.getDurationMinutes();
                    break;
                }
            }
            if (duration == -1) duration = 0;
        }

        if (duration > 0) {
            long durationMs = duration * 60 * 1000L;
            long checkinTime = room.getCheckinTime();
            long expireTime = checkinTime + durationMs;
            player.sendMessage("§e房间使用时限: " + duration + " 分钟");
            player.sendMessage("§7将在 §e" + java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                    .format(new java.util.Date(expireTime)) + " §7自动退房");

            // 定时任务检查
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 检查玩家是否还在这个房间
                HotelRoom current = plugin.getRoomStorage().getRoom(room.getId());
                if (current != null && current.isOccupied()
                        && current.getCurrentGuest() != null
                        && current.getCurrentGuest().equals(player.getUniqueId())) {
                    Player p = Bukkit.getPlayer(player.getUniqueId());
                    if (p != null && p.isOnline()) {
                        p.sendMessage("§c入住时间已到，自动退房");
                    }
                    checkout(player);
                }
            }, duration * 60 * 20L); // duration分钟 * 60秒 * 20tick
        }

        // 通知房主
        Player owner = Bukkit.getPlayer(room.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§e" + player.getName() + " §a已入住你的房间 §e" + room.getName());
        }
    }

    /**
     * 退房
     */
    public void checkout(Player player) {
        if (player == null) return;

        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(player.getUniqueId())) {
                room.setCurrentGuest(null);
                room.setCurrentGuestName(null);
                room.setStatus(HotelRoom.RoomStatus.AVAILABLE);
                room.setCheckinTime(0);
                plugin.getRoomStorage().saveRoom(room);

                plugin.log(player, "成功退房: " + room.getName() + " (ID: " + room.getId() + ")");
                player.sendMessage("§a已从房间 §e" + room.getName() + " §a退房");

                Player owner = Bukkit.getPlayer(room.getOwner());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage("§e" + player.getName() + " §c已从你的房间 §e" + room.getName() + " §c退房");
                }
                return;
            }
        }

        player.sendMessage("§c你没有入住任何房间");
    }

    /**
     * 强制退房（用于自动退房，玩家可能离线）
     */
    public void forceCheckout(UUID guestUUID) {
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(guestUUID)) {
                String guestName = room.getCurrentGuestName();
                room.setCurrentGuest(null);
                room.setCurrentGuestName(null);
                room.setStatus(HotelRoom.RoomStatus.AVAILABLE);
                room.setCheckinTime(0);
                plugin.getRoomStorage().saveRoom(room);

                plugin.log(null, "自动退房: " + guestName + " 从房间 " + room.getName() + " (ID: " + room.getId() + ")");

                Player owner = Bukkit.getPlayer(room.getOwner());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage("§e" + guestName + " §c入住时间已到，自动退房");
                }
                return;
            }
        }
    }
}
