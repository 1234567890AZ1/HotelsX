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
import com.hotels.gui.ElevatorGUI;
import com.hotels.model.HotelRoom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class ElevatorListener implements Listener {

    private final HotelsPlugin plugin;

    public ElevatorListener(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.isElevatorEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.isElevatorEnabledFor(player)) {
            return;
        }

        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) return;

        if (to.getY() > from.getY() + 0.2) {
            org.bukkit.Location feetLoc = from.clone().add(0, -1, 0);
            if (feetLoc.getBlock().getType() == Material.IRON_BLOCK) {
                if (!canAccessBlock(player, feetLoc)) {
                    return;
                }
                int maxDistance = plugin.getConfig().getInt("elevator.max-distance", 96);
                org.bukkit.Location target = findTargetBlock(player.getLocation(), 1, maxDistance);
                if (target != null) {
                    com.hotels.util.SchedulerCompat.teleport(player, target);
                    player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                    plugin.log(player, "使用电梯向上: " + player.getName());
                } else {
                    player.sendMessage("§c上方没有找到铁块");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!plugin.isElevatorEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.isElevatorEnabledFor(player)) {
            return;
        }

        if (!event.isSneaking()) {
            return;
        }

        org.bukkit.Location feetLoc = player.getLocation().clone().add(0, -1, 0);
        if (feetLoc.getBlock().getType() != Material.IRON_BLOCK) {
            return;
        }

        if (!canAccessBlock(player, feetLoc)) {
            return;
        }

        int maxDistance = plugin.getConfig().getInt("elevator.max-distance", 96);
        org.bukkit.Location target = findTargetBlock(player.getLocation(), -1, maxDistance);

        if (target != null) {
            com.hotels.util.SchedulerCompat.teleport(player, target);
            player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
            plugin.log(player, "使用电梯向下: " + player.getName());
        } else {
            player.sendMessage("§c下方没有找到铁块");
        }
    }

    /**
     * 右键铁块打开选层 GUI（不潜行时）
     * 潜行右键仍会触发 onPlayerSneak 向下传送，不做拦截
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerRightClickIronBlock(PlayerInteractEvent event) {
        if (!plugin.isElevatorEnabled()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.IRON_BLOCK) {
            return;
        }
        // 潜行时交给原版蹲下事件（向下一层），不打开 GUI
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        if (!plugin.isElevatorEnabledFor(player)) {
            return;
        }
        Location clicked = event.getClickedBlock().getLocation();
        if (!canAccessBlock(player, clicked)) {
            player.sendMessage("§c你无法使用此处的电梯");
            return;
        }

        event.setCancelled(true);
        plugin.log(player, "打开电梯选层菜单 @ (" + clicked.getBlockX() + "," + clicked.getBlockY() + "," + clicked.getBlockZ() + ")");
        ElevatorGUI.open(player, clicked.getBlockX(), clicked.getBlockY(), clicked.getBlockZ(),
                clicked.getWorld() != null ? clicked.getWorld().getName() : player.getWorld().getName());
    }

    private org.bukkit.Location findTargetBlock(org.bukkit.Location start, int direction, int maxDistance) {
        org.bukkit.World world = start.getWorld();
        if (world == null) return null;

        int startY = (int) (start.getY() - 1);
        int endY = direction > 0 ? world.getMaxHeight() : world.getMinHeight();
        int step = direction > 0 ? 1 : -1;

        for (int y = startY + step * 2; direction > 0 ? y < endY : y > endY; y += step) {
            if (Math.abs(y - startY) > maxDistance) {
                break;
            }

            org.bukkit.Location checkLoc = new org.bukkit.Location(world, start.getX(), y, start.getZ());
            if (checkLoc.getBlock().getType() == Material.IRON_BLOCK) {
                org.bukkit.Location teleportLoc = new org.bukkit.Location(world, start.getX(), y + 1, start.getZ(),
                        start.getYaw(), start.getPitch());

                if (isSafeTeleport(teleportLoc)) {
                    return teleportLoc;
                }
            }
        }

        return null;
    }

    private boolean isSafeTeleport(org.bukkit.Location loc) {
        if (loc.getWorld() == null) return false;

        org.bukkit.Location headLoc = loc.clone().add(0, 1, 0);
        org.bukkit.Location aboveLoc = loc.clone().add(0, 2, 0);

        return loc.getBlock().isEmpty() &&
               headLoc.getBlock().isEmpty() &&
               aboveLoc.getBlock().isEmpty();
    }

    private boolean canAccessBlock(Player player, org.bukkit.Location loc) {
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(loc);
        if (room == null) {
            return true;
        }

        if (player.hasPermission("hotels.bypass") || player.hasPermission("hotels.admin")) {
            return true;
        }

        if (room.getOwner().equals(player.getUniqueId())) {
            return true;
        }

        if (room.getCurrentGuest() != null && room.getCurrentGuest().equals(player.getUniqueId())) {
            return true;
        }

        return false;
    }
}