/*
 * HotelsX - 酒店房间管理插件
 * MIT License
 *
 * Copyright (c) 2024-2026 HotelsX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hotels.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 铁块电梯选层 GUI
 * 右键脚下铁块时打开，显示同一列所有铁块楼层，点击传送到目标层
 */
public class ElevatorGUI {

    public static final String GUI_NAME = "elevator_select";

    /**
     * 楼层数据：每个楼层的 Y 坐标（铁块位置 Y，即脚底方块 Y）
     */
    public static class FloorData {
        public final int x;
        public final int z;
        public final int y;
        public final String worldName;
        public final boolean safe;
        public final boolean isCurrent;

        public FloorData(int x, int y, int z, String worldName, boolean safe, boolean isCurrent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldName = worldName;
            this.safe = safe;
            this.isCurrent = isCurrent;
        }

        public Location getTeleportLocation(Player player) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;
            return new Location(w, x + 0.5, y + 1, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        }
    }

    /**
     * 打开选层菜单
     * @param player 玩家
     * @param blockX 铁块所在 X
     * @param blockY 当前铁块 Y（脚底方块）
     * @param blockZ 铁块所在 Z
     * @param worldName 世界名
     */
    public static void open(Player player, int blockX, int blockY, int blockZ, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // 扫描同一列所有铁块
        int maxDistance = 96; // 上下最多 96 格
        List<FloorData> floors = new ArrayList<>();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int startY = Math.max(minY, blockY - maxDistance);
        int endY = Math.min(maxY, blockY + maxDistance);

        for (int y = startY; y < endY; y++) {
            Location loc = new Location(world, blockX, y, blockZ);
            if (loc.getBlock().getType() == Material.IRON_BLOCK) {
                // 检查传送安全（上方 2 格为空气/可通行方块）
                Location tpLoc = new Location(world, blockX + 0.5, y + 1, blockZ + 0.5);
                boolean safe = isSafe(tpLoc);
                boolean current = (y == blockY);
                floors.add(new FloorData(blockX, y, blockZ, worldName, safe, current));
            }
        }

        if (floors.size() <= 1) {
            player.sendMessage("§c当前电梯列只有一个楼层，没有可选楼层");
            return;
        }

        // 按 Y 从高到低排序（高楼层在上）
        floors.sort((a, b) -> Integer.compare(b.y, a.y));

        // 当前层在列表中的索引
        int currentIdx = -1;
        for (int i = 0; i < floors.size(); i++) {
            if (floors.get(i).isCurrent) { currentIdx = i; break; }
        }

        // 只显示当前层附近最多 53 层保证当前层可见
        List<FloorData> visible = trimFloors(floors, currentIdx, 53);

        // 重新计算当前层在可见列表中的索引
        int visibleCurrent = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).isCurrent) { visibleCurrent = i; break; }
        }

        int size = Math.min(54, Math.max(9, ((visible.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME, visible), size, "§8§l电梯选层");

        // 从底部往顶部摆（低楼层在下，高楼层在上），符合电梯直觉
        // 先反转：把最高层放最后一行最右，最低层放第一行最左——不好
        // 更直观：从 GUI 底部开始往上放，即 slot = size - 1 - i
        for (int i = 0; i < visible.size(); i++) {
            FloorData floor = visible.get(i);
            int slot = size - 1 - i;
            inv.setItem(slot, createFloorItem(floor, blockY, visibleCurrent == i));
        }

        player.openInventory(inv);
    }

    /**
     * 截取当前层附近的楼层，确保当前层可见，且总数不超过 max
     */
    private static List<FloorData> trimFloors(List<FloorData> all, int currentIdx, int max) {
        if (all.size() <= max) return new ArrayList<>(all);
        int start = Math.max(0, currentIdx - max / 2);
        int end = Math.min(all.size(), start + max);
        if (end == all.size()) start = Math.max(0, end - max);
        return new ArrayList<>(all.subList(start, end));
    }

    private static ItemStack createFloorItem(FloorData floor, int currentY, boolean highlightCurrent) {
        Material mat;
        String name;
        List<String> lore = new ArrayList<>();

        int diff = floor.y - currentY; // 正数表示在上方

        if (floor.isCurrent) {
            mat = Material.GOLD_BLOCK;
            name = "§6§l当前层 §7(Y: " + floor.y + ")";
            lore.add("§7你正在这一层");
        } else if (!floor.safe) {
            mat = Material.REDSTONE_BLOCK;
            name = "§cY: " + floor.y;
            lore.add("§c此楼层空间不足，传送可能窒息");
        } else if (diff > 0) {
            mat = Material.IRON_BLOCK;
            name = "§fY: " + floor.y;
            lore.add("§a上方 " + diff + " 格");
        } else {
            mat = Material.IRON_BLOCK;
            name = "§fY: " + floor.y;
            lore.add("§7下方 " + Math.abs(diff) + " 格");
        }

        lore.add("");
        lore.add("§8点击传送到此层");

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static boolean isSafe(Location loc) {
        if (loc.getWorld() == null) return false;
        Location feet = loc.clone();
        Location head = loc.clone().add(0, 1, 0);
        return !feet.getBlock().getType().isSolid() && !head.getBlock().getType().isSolid();
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
