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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
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

    /**
     * 方块爆炸（床、重生锚等）同样会破坏房间结构
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosion-protection", true)) {
            return;
        }

        event.blockList().removeIf(block ->
                plugin.getRoomStorage().getRoomAtLocation(block.getLocation()) != null);
    }

    /**
     * 活塞推拉可以把房间内的方块推出、或把外部方块塞进房间，绕过破坏/放置保护
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.getConfig().getBoolean("protection.piston-protection", true)) {
            return;
        }
        if (pistonAffectsUnattendedRoom(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.getConfig().getBoolean("protection.piston-protection", true)) {
            return;
        }
        if (pistonAffectsUnattendedRoom(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /**
     * 点火与燃烧会摧毁房间内的可燃方块
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.getConfig().getBoolean("protection.fire-protection", true)) {
            return;
        }

        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(event.getBlock().getLocation());
        if (room == null) return;

        Player player = event.getPlayer();
        if (player != null) {
            if (canAccessRoom(player, room)) return;
            event.setCancelled(true);
            plugin.getLang().send(player, "listener.protect.no_ignite");
            return;
        }
        // 自然/红石等无操作者的点火：附近没有有权限的玩家就拦截
        if (!hasAuthorizedPlayerNear(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.getConfig().getBoolean("protection.fire-protection", true)) {
            return;
        }

        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(event.getBlock().getLocation());
        if (room == null) return;
        if (hasAuthorizedPlayerNear(event.getBlock().getLocation())) return;
        event.setCancelled(true);
    }

    /**
     * 告示牌内容属于房间陈设，非授权玩家不能改写
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.sign-protection", true)) {
            return;
        }

        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(event.getBlock().getLocation());
        if (room == null) return;
        if (canAccessRoom(event.getPlayer(), room)) return;

        event.setCancelled(true);
        plugin.getLang().send(event.getPlayer(), "listener.protect.no_sign");
    }

    /**
     * 房间内的实体（物品展示框、盔甲架等）不允许非授权玩家交互或破坏
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protection.entity-protection", true)) {
            return;
        }

        Entity entity = event.getRightClicked();
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(entity.getLocation());
        if (room == null) return;
        if (canAccessRoom(event.getPlayer(), room)) return;

        event.setCancelled(true);
        plugin.getLang().send(event.getPlayer(), "listener.protect.no_entity");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protection.entity-protection", true)) {
            return;
        }
        if (!(event.getDamager() instanceof Player)) return;

        Entity entity = event.getEntity();
        // 只保护房间陈设实体（展示框/盔甲架/船/矿车等），不影响正常战斗
        if (entity instanceof Player || entity instanceof org.bukkit.entity.Mob) return;

        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(entity.getLocation());
        if (room == null) return;
        if (canAccessRoom((Player) event.getDamager(), room)) return;

        event.setCancelled(true);
    }

    /**
     * 判断活塞动作是否会影响没有人值守授权的房间区域
     */
    private boolean pistonAffectsUnattendedRoom(Block piston, List<Block> blocks, org.bukkit.block.BlockFace direction) {
        if (isUnattendedRoomLocation(piston.getLocation())) return true;
        for (Block b : blocks) {
            if (isUnattendedRoomLocation(b.getLocation())) return true;
            if (isUnattendedRoomLocation(b.getRelative(direction).getLocation())) return true;
        }
        return false;
    }

    private boolean isUnattendedRoomLocation(Location loc) {
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(loc);
        if (room == null) return false;
        return !hasAuthorizedPlayerNear(loc);
    }

    /**
     * 位置附近（8 格内）是否存在对该房间有操作权限的玩家。
     * 活塞、点火、燃烧等事件没有明确的"操作者"，用附近玩家近似判定。
     */
    private boolean hasAuthorizedPlayerNear(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(loc);
        if (room == null) return false;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) > 64) continue;
            if (canAccessRoom(p, room)) return true;
        }
        return false;
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
            case CHISELED_BOOKSHELF:
            case CRAFTER:
            case JUKEBOX:
                return true;
            default:
                return false;
        }
    }
}