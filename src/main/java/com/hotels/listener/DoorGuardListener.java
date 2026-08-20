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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 门禁监听器 - 房间内的门/活板门/栅栏门只有房主和入住客人可以打开
 */
public class DoorGuardListener implements Listener {

    private final HotelsPlugin plugin;

    public DoorGuardListener(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getConfig().getBoolean("protection.door-protection", true)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        String name = block.getType().name();
        // 仅处理门/活板门/栅栏门
        if (!name.endsWith("_DOOR") && !name.endsWith("_TRAPDOOR") && !name.endsWith("_FENCE_GATE")) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("hotels.bypass")) return;

        Location loc = block.getLocation();
        for (HotelRoom room : plugin.getRoomStorage().getAllRooms()) {
            if (!room.containsLocation(loc)) continue;

            boolean isOwner = room.getOwner() != null && room.getOwner().equals(player.getUniqueId());
            boolean isGuest = room.getCurrentGuest() != null && room.getCurrentGuest().equals(player.getUniqueId());
            if (isOwner || isGuest) return;

            event.setCancelled(true);
            player.sendMessage("§c该房间的门已上锁，只有房主和入住客人可以打开");
            return;
        }
    }
}
