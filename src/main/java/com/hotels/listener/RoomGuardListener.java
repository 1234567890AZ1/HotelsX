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
package com.hotels.listener;

import com.hotels.HotelsPlugin;
import com.hotels.model.HotelRoom;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * 房间守卫 - 只有入住的客人或房主才能进入房间区域
 */
public class RoomGuardListener implements Listener {

    private final HotelsPlugin plugin;

    public RoomGuardListener(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 有 bypass 权限的直接放行
        if (player.hasPermission("hotels.bypass") || player.hasPermission("hotels.admin")) {
            return;
        }

        Location to = event.getTo();
        if (to == null) return;

        // 检查玩家是否进入了某个房间的区域
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (!room.containsLocation(to)) continue;

            // 进入了房间区域，检查是否有权限
            // 房主可以进
            if (room.getOwner().equals(player.getUniqueId())) return;

            // 当前入住的客人可以进
            if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(player.getUniqueId())) return;

            // 房间是空闲状态且没上锁 -> 允许进入（方便看房）
            if (room.isAvailable() && !room.isLocked()) return;

            // 其他情况：推出去
            Location from = event.getFrom();
            plugin.log(player, "尝试进入房间被拒绝: " + room.getName() + " (权限不足)");

            // 落点必须在房间区域之外：优先退回进入前的位置，
            // 若玩家本来就在区域内（例如在房间里登录），则推到最近的房间边界外
            Location exit = safeExit(room, from, to);
            com.hotels.util.SchedulerCompat.teleport(player, exit);
            plugin.getLang().send(player, "listener.guard.no_enter");

            plugin.debug(player, "玩家被拒绝进入房间: " + player.getName() + " 尝试进入 " + room.getName());

            event.setCancelled(true);
            return;
        }
    }

    /**
     * 计算房间区域外的安全落点，避免把入侵者传送到房间内部
     */
    private Location safeExit(HotelRoom room, Location from, Location to) {
        if (from != null && from.getWorld() != null && !room.containsLocation(from)) {
            return from.clone();
        }
        double minX = Math.min(room.getX1(), room.getX2());
        double maxX = Math.max(room.getX1(), room.getX2());
        double minY = Math.min(room.getY1(), room.getY2());
        double maxY = Math.max(room.getY1(), room.getY2());
        double minZ = Math.min(room.getZ1(), room.getZ2());
        double maxZ = Math.max(room.getZ1(), room.getZ2());

        double px = from != null ? from.getX() : (minX + maxX) / 2;
        double py = from != null ? from.getY() : minY;
        double pz = from != null ? from.getZ() : (minZ + maxZ) / 2;

        double dMinX = px - minX, dMaxX = maxX - px, dMinZ = pz - minZ, dMaxZ = maxZ - pz;
        double nearest = Math.min(Math.min(dMinX, dMaxX), Math.min(dMinZ, dMaxZ));
        double x = px, z = pz;
        if (nearest == dMinX) x = minX - 1.5;
        else if (nearest == dMaxX) x = maxX + 1.5;
        else if (nearest == dMinZ) z = minZ - 1.5;
        else z = maxZ + 1.5;

        py = Math.max(minY - 1, Math.min(py, maxY + 1));
        return new Location(to.getWorld(), x, py, z, to.getYaw(), to.getPitch());
    }
}
