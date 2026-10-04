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
import com.hotels.model.RoomCollection;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class RoomProtectListener implements Listener {

    private final HotelsPlugin plugin;

    public RoomProtectListener(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canAccessRoom(Player player, HotelRoom room) {
        if (player == null) return false;
        if (player.hasPermission("hotels.bypass") || player.hasPermission("hotels.admin")) {
            return true;
        }
        UUID playerUUID = player.getUniqueId();
        if (room.getOwner().equals(playerUUID)) {
            return true;
        }
        if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(playerUUID)) {
            return true;
        }
        for (RoomCollection col : plugin.getRoomStorage().getAllCollections()) {
            if (col.getRoomIds().contains(room.getId()) && col.canManage(playerUUID)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-break-protection", true)) {
            return;
        }

        Player player = event.getPlayer();
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(event.getBlock().getLocation());

        if (room == null) return;
        if (canAccessRoom(player, room)) return;

        event.setCancelled(true);
        plugin.getLang().send(player, "listener.protect.no_break");
        plugin.debug(player, "尝试破坏房间方块被拒绝: " + player.getName() + " 房间: " + room.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-place-protection", true)) {
            return;
        }

        Player player = event.getPlayer();
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(event.getBlock().getLocation());

        if (room == null) return;
        if (canAccessRoom(player, room)) return;

        event.setCancelled(true);
        plugin.getLang().send(player, "listener.protect.no_place");
        plugin.debug(player, "尝试放置房间方块被拒绝: " + player.getName() + " 房间: " + room.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("protection.container-protection", true)) {
            return;
        }

        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(event.getClickedBlock().getLocation());

        if (room == null) return;

        Material material = event.getClickedBlock().getType();
        if (!isContainer(material)) return;

        if (canAccessRoom(player, room)) return;

        event.setCancelled(true);
        plugin.getLang().send(player, "listener.protect.no_container");
        plugin.debug(player, "尝试打开房间容器被拒绝: " + player.getName() + " 房间: " + room.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosion-protection", true)) {
            return;
        }

        event.blockList().removeIf(block -> {
            HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(block.getLocation());
            return room != null;
        });
    }

    private boolean isContainer(Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
            case SHULKER_BOX:
            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case BREWING_STAND:
            case ENCHANTING_TABLE:
            case ANVIL:
            case GRINDSTONE:
            case LECTERN:
            case COMPOSTER:
            case BEACON:
            case ENDER_CHEST:
            case CRAFTING_TABLE:
            case CARTOGRAPHY_TABLE:
            case LOOM:
            case STONECUTTER:
            case FLETCHING_TABLE:
            case SMITHING_TABLE:
                return true;
            default:
                return false;
        }
    }
}