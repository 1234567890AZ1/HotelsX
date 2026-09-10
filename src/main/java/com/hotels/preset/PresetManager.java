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
package com.hotels.preset;

import com.hotels.HotelsPlugin;
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomPreset;
import com.hotels.model.RoomPreset.SnapshotLine;
import com.hotels.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间装修预设核心逻辑。
 *
 * 功能：
 * - 保存：将某个房间区域的完整装修（含容器内容物、告示牌文字）快照为预设
 * - 应用：仅当目标房间的长/宽/高与预设完全一致时，清空房间并按预设重建
 *
 * 设计要点：
 * - 尺寸校验：三个维度（X/Y/Z）逐一相等才允许应用
 * - 性能：应用按 chunk 分批执行，避免大房间导致主线程卡顿
 * - Folia 兼容：方块操作通过 SchedulerCompat 在所在区域线程上执行
 * - 安全：保存预设需要 hotels.admin 权限（容器内容物会被复制，防止刷物品）
 */
public class PresetManager {

    /** 保存预设时快照的类型（容器类） */
    private static final Set<String> CONTAINER_TYPES = Set.of(
            "chest", "trapped_chest", "barrel", "furnace", "blast_furnace",
            "smoker", "hopper", "dispenser", "dropper", "brewing_stand",
            "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
            "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box",
            "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box",
            "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box",
            "brown_shulker_box", "green_shulker_box", "red_shulker_box",
            "black_shulker_box");

    /** 保存预设时快照的类型（告示牌） */
    private static final Set<String> SIGN_TYPES = Set.of(
            "oak_sign", "oak_wall_sign", "spruce_sign", "spruce_wall_sign",
            "birch_sign", "birch_wall_sign", "jungle_sign", "jungle_wall_sign",
            "acacia_sign", "acacia_wall_sign", "dark_oak_sign", "dark_oak_wall_sign",
            "mangrove_sign", "mangrove_wall_sign", "cherry_sign", "cherry_wall_sign",
            "bamboo_sign", "bamboo_wall_sign", "crimson_sign", "crimson_wall_sign",
            "warped_sign", "warped_wall_sign");

    /** 正在应用预设的玩家，防止重复触发 */
    private final Set<Player> applying = ConcurrentHashMap.newKeySet();

    /** 应用预设前的房间状态备份（roomId -> 快照），用于 /ht preset undo 回滚 */
    private final Map<String, RoomPreset> undoBackups = new ConcurrentHashMap<>();

    private final HotelsPlugin plugin;

    public PresetManager(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== 工具：房间边界 =====

    /**
     * 计算房间区域的最小/最大整数坐标，返回 {minX, minY, minZ, maxX, maxY, maxZ}
     */
    public static int[] roomBounds(HotelRoom room) {
        int minX = (int) Math.floor(Math.min(room.getX1(), room.getX2()));
        int maxX = (int) Math.floor(Math.max(room.getX1(), room.getX2()));
        int minY = (int) Math.floor(Math.min(room.getY1(), room.getY2()));
        int maxY = (int) Math.floor(Math.max(room.getY1(), room.getY2()));
        int minZ = (int) Math.floor(Math.min(room.getZ1(), room.getZ2()));
        int maxZ = (int) Math.floor(Math.max(room.getZ1(), room.getZ2()));
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * 计算房间的三个维度尺寸（0=宽X, 1=高Y, 2=深Z）
     */
    public static int[] roomDimensions(HotelRoom room) {
        int[] b = roomBounds(room);
        return new int[]{b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1};
    }

    /**
     * 从 BlockData 字符串提取方块 id（如 "minecraft:chest[facing=north]" -> "chest"）
     */
    private static String blockId(String blockData) {
        int colon = blockData.indexOf(':');
        int bracket = blockData.indexOf('[');
        if (bracket < 0) bracket = blockData.length();
        return blockData.substring(colon + 1, bracket);
    }

    // ===== 保存预设 =====

    /**
     * 将房间保存为装修预设（需 hotels.admin 权限，由命令层校验）
     */
    public void savePreset(Player player, HotelRoom room, String name) {
        World world = Bukkit.getWorld(room.getWorldName());
        if (world == null) {
            player.sendMessage("§c房间所在世界不存在");
            return;
        }
        if (plugin.getPresetStorage().hasPreset(name)) {
            player.sendMessage("§c已存在同名预设 §e" + name + " §c，将覆盖旧预设");
        }
        player.sendMessage("§7正在快照房间装修，请稍候...");

        int[] b = roomBounds(room);
        Location center = new Location(world,
                (b[0] + b[3]) / 2.0, (b[1] + b[4]) / 2.0, (b[2] + b[5]) / 2.0);

        // 在房间所在区域线程上执行快照（Folia 下块操作必须在区域线程）
        SchedulerCompat.runOnRegion(plugin, center, () -> {
            try {
                RoomPreset preset = snapshot(room, world, name, b);
                plugin.getPresetStorage().savePreset(preset);
                plugin.log(player, "保存装修预设: " + name
                        + " (" + preset.getSizeDisplay() + ", " + preset.getBlocks().size() + " 个方块)");
                SchedulerCompat.runTask(plugin, () -> player.sendMessage(
                        "§a装修预设 §e" + name + " §a保存成功！"));
            } catch (Exception e) {
                plugin.getLogger().warning("保存装修预设失败: " + e.getMessage());
                SchedulerCompat.runTask(plugin, () ->
                        player.sendMessage("§c保存失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 快照房间区域为预设（必须在区域线程执行）
     */
    private RoomPreset snapshot(HotelRoom room, World world, String name, int[] b) {
        int minX = b[0], minY = b[1], minZ = b[2], maxX = b[3], maxY = b[4], maxZ = b[5];

        RoomPreset preset = new RoomPreset();
        preset.setName(name);
        preset.setWorld(world.getName());
        preset.setSizeX(maxX - minX + 1);
        preset.setSizeY(maxY - minY + 1);
        preset.setSizeZ(maxZ - minZ + 1);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    String data = block.getBlockData().getAsString();

                    int rx = x - minX;
                    int ry = y - minY;
                    int rz = z - minZ;
                    preset.getBlocks().add(new SnapshotLine(rx, ry, rz, data).encode());

                    // 附加数据（容器内容物 / 告示牌文字）
                    String id = blockId(data);
                    retrofitExtra(block, id, rx, ry, rz, preset);
                }
            }
        }
        return preset;
    }

    /**
     * 为特定类型方块采集附加数据
     */
    private void retrofitExtra(Block block, String id, int rx, int ry, int rz, RoomPreset preset) {
        if (CONTAINER_TYPES.contains(id)) {
            BlockState st = block.getState();
            if (st instanceof Container) {
                ItemStack[] items = ((Container) st).getInventory().getContents();
                List<Map<String, Object>> itemMaps = new ArrayList<>(items.length);
                for (ItemStack it : items) {
                    itemMaps.add(it == null ? null : it.serialize());
                }
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("type", "container");
                extra.put("items", itemMaps);
                preset.getExtras().put(rx + ":" + ry + ":" + rz, extra);
            }
        } else if (SIGN_TYPES.contains(id)) {
            BlockState st = block.getState();
            if (st instanceof Sign) {
                List<String> lines = new ArrayList<>(4);
                for (int i = 0; i < 4; i++) {
                    lines.add(((Sign) st).getLine(i));
                }
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("type", "sign");
                extra.put("lines", lines);
                preset.getExtras().put(rx + ":" + ry + ":" + rz, extra);
            }
        }
    }

    // ===== 应用预设 =====

    /**
     * 应用预设到房间。仅当房间尺寸（宽/高/深）与预设完全一致时允许。
     *
     * @param roomId 为 null 时使用"当前所在房间"
     */
    public void applyPreset(Player player, HotelRoom room, RoomPreset preset) {
        applyPreset(player, room, preset, false);
    }

    /**
     * 应用预设到房间（带冲突确认参数）。
     *
     * @param confirm true 时跳过"房间已有装修"的冲突提示，直接覆盖
     */
    public void applyPreset(Player player, HotelRoom room, RoomPreset preset, boolean confirm) {
        if (applying.contains(player)) {
            player.sendMessage("§c你正在应用装修预设，请稍候...");
            return;
        }
        if (room.isOccupied()) {
            player.sendMessage("§c房间当前有客人入住，无法应用装修预设");
            return;
        }
        if (!room.getWorldName().equals(preset.getWorld())) {
            player.sendMessage("§c预设来源世界（" + preset.getWorld() + "）与房间世界不一致");
            return;
        }

        int[] dims = roomDimensions(room);
        if (!preset.matchesSize(dims[0], dims[1], dims[2])) {
            player.sendMessage("§c尺寸不匹配，无法应用该预设！");
            player.sendMessage("§7预设尺寸: §e" + preset.getSizeDisplay()
                    + (preset.getRotation() > 0 ? " §7(旋转" + preset.getRotation() + "°)" : "")
                    + " §7| 房间尺寸: §e" + dims[0] + " x " + dims[1] + " x " + dims[2]);
            player.sendMessage("§7提示: 只有房间与预设大小完全一致才能应用");
            return;
        }

        World world = Bukkit.getWorld(room.getWorldName());
        if (world == null) {
            player.sendMessage("§c房间所在世界不存在");
            return;
        }

        // 将"可以使用功能"的判定回报给玩家
        player.sendMessage("§a尺寸匹配，开始应用预设 §e" + preset.getName() + " §a...");
        plugin.log(player, "应用装修预设: " + preset.getName() + " -> 房间 "
                + room.getName() + " (" + room.getId() + ")");

        int[] b = roomBounds(room);
        Location center = new Location(world,
                (b[0] + b[3]) / 2.0, (b[1] + b[4]) / 2.0, (b[2] + b[5]) / 2.0);

        // 在房间区域线程执行冲突检测与备份（Folia 下方块操作必须在区域线程）
        SchedulerCompat.runOnRegion(plugin, center, () -> {
            // 冲突检测：统计房间内现有非空气方块
            int nonAir = countNonAir(world, b);
            if (nonAir > 0 && !confirm) {
                player.sendMessage("§c检测到房间内已有 §e" + nonAir + " §c个方块，应用预设将覆盖现有装修！");
                player.sendMessage("§7如确认覆盖，请执行: §e/ht preset apply <名称> [角度] confirm");
                return;
            }

            // 备份当前房间状态，用于 /ht preset undo 回滚
            try {
                RoomPreset backup = snapshot(room, world, "__undo__" + room.getId(), b);
                undoBackups.put(room.getId(), backup);
                player.sendMessage("§7已备份当前装修，可用 §e/ht preset undo §7回滚");
            } catch (Exception e) {
                plugin.getLogger().warning("备份房间状态失败: " + e.getMessage());
            }

            if (SchedulerCompat.isFolia()) {
                applyFolia(world, b, preset, player);
            } else {
                applyNonFolia(world, b, preset, player);
            }
        });
    }

    /**
     * 回滚房间装修到应用预设前的状态。
     *
     * @return true 表示找到了备份并已启动回滚
     */
    public boolean undoPreset(Player player, HotelRoom room) {
        RoomPreset backup = undoBackups.get(room.getId());
        if (backup == null) {
            player.sendMessage("§c该房间没有可回滚的装修记录");
            return false;
        }
        if (applying.contains(player)) {
            player.sendMessage("§c你正在应用装修预设，请稍候...");
            return false;
        }
        if (room.isOccupied()) {
            player.sendMessage("§c房间当前有客人入住，无法回滚装修");
            return false;
        }
        World world = Bukkit.getWorld(room.getWorldName());
        if (world == null) {
            player.sendMessage("§c房间所在世界不存在");
            return false;
        }

        player.sendMessage("§7正在回滚房间装修到应用预设前的状态...");
        plugin.log(player, "回滚装修: " + room.getName() + " (" + room.getId() + ")");

        int[] b = roomBounds(room);
        undoBackups.remove(room.getId());

        if (SchedulerCompat.isFolia()) {
            applyFolia(world, b, backup, player);
        } else {
            applyNonFolia(world, b, backup, player);
        }
        return true;
    }

    /**
     * 统计房间区域内非空气方块数量（必须在区域线程/主线程执行）
     */
    private int countNonAir(World world, int[] b) {
        int count = 0;
        for (int y = b[1]; y <= b[4]; y++) {
            for (int x = b[0]; x <= b[3]; x++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 非 Folia 实现：每个 tick 处理一个 chunk，避免主线程卡顿
     */
    private void applyNonFolia(World world, int[] b, RoomPreset preset, Player player) {
        Deque<Long> chunkQueue = buildChunkQueue(b);
        int totalChunks = chunkQueue.size();
        int[] done = {0};
        applying.add(player);

        SchedulerCompat.CancellableTask[] taskHolder = new SchedulerCompat.CancellableTask[1];
        taskHolder[0] = SchedulerCompat.runTaskTimer(plugin, () -> {
            Long key = chunkQueue.poll();
            if (key == null) {
                taskHolder[0].cancel();
                applying.remove(player);
                player.sendMessage("§a装修预设应用完成！");
                return;
            }
            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            processChunk(world, b, preset, cx, cz);
            done[0]++;
            if (done[0] % 5 == 0 || done[0] == totalChunks) {
                player.sendMessage("§7进度: " + done[0] + "/" + totalChunks + " chunk");
            }
        }, 1L, 1L);
    }

    /**
     * Folia 实现：每个 chunk 调度到其所属区域线程并行执行
     */
    private void applyFolia(World world, int[] b, RoomPreset preset, Player player) {
        Deque<Long> chunkQueue = buildChunkQueue(b);
        int totalChunks = chunkQueue.size();
        int[] done = {0};

        applying.add(player);
        for (Long key : chunkQueue) {
            int cx = (int) (key & 0xFFFFFFFFL);
            int cz = (int) (key >>> 32);
            Location center = new Location(world, (cx << 4) + 7.5, b[1], (cz << 4) + 7.5);
            SchedulerCompat.runOnRegion(plugin, center, () -> {
                processChunk(world, b, preset, cx, cz);
                done[0]++;
                if (done[0] == totalChunks) {
                    applying.remove(player);
                    SchedulerCompat.runTask(plugin, () ->
                            player.sendMessage("§a装修预设应用完成！"));
                }
            });
        }
    }

    /**
     * 构建房间区域覆盖的 chunk 坐标队列（key = (cz << 32) | cx）
     */
    private Deque<Long> buildChunkQueue(int[] b) {
        int minCx = b[0] >> 4;
        int maxCx = b[3] >> 4;
        int minCz = b[2] >> 4;
        int maxCz = b[5] >> 4;
        Deque<Long> queue = new ArrayDeque<>();
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                queue.add(((long) cz << 32) | (cx & 0xFFFFFFFFL));
            }
        }
        return queue;
    }

    /**
     * 处理单个 chunk：先清空该 chunk 与房间区域的交集，再放置预设方块（区域线程/主线程内执行）
     */
    private void processChunk(World world, int[] b, RoomPreset preset, int cx, int cz) {
        int minX = b[0], minY = b[1], minZ = b[2], maxX = b[3], maxY = b[4], maxZ = b[5];

        int wx0 = Math.max(minX, cx << 4);
        int wx1 = Math.min(maxX, (cx << 4) + 15);
        int wz0 = Math.max(minZ, cz << 4);
        int wz1 = Math.min(maxZ, (cz << 4) + 15);

        // 1) 清空区域（覆盖旧装修）
        for (int y = minY; y <= maxY; y++) {
            for (int x = wx0; x <= wx1; x++) {
                for (int z = wz0; z <= wz1; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }

        // 2) 放置预设方块（应用旋转坐标变换）
        for (String line : preset.getBlocks()) {
            SnapshotLine sl = RoomPreset.parseLine(line);
            if (sl == null) continue;

            // 旋转后的相对坐标
            int[] rotated = preset.getRotatedRelCoords(sl.x, sl.y, sl.z);
            int ax = minX + rotated[0];
            int ay = minY + rotated[1];
            int az = minZ + rotated[2];

            // 只处理属于当前 chunk 的方块
            if (ax >> 4 != cx || az >> 4 != cz) continue;
            if (ax < wx0 || ax > wx1 || az < wz0 || az > wz1) continue;

            Block block = world.getBlockAt(ax, ay, az);
            BlockData bd = Bukkit.createBlockData(sl.data);
            if (preset.getRotation() > 0) {
                rotateBlockData(bd, preset.getRotation());
            }
            block.setBlockData(bd, false);
            // extras 使用原始快照坐标查找（保存时存入的 key 就是原始坐标）
            applyExtra(world, block, ax, ay, az, preset, sl.x, sl.y, sl.z);
        }
    }

    /**
     * 旋转方块数据，使方块朝向（箱子/熔炉/楼梯/告示牌等）与坐标旋转保持一致。
     *
     * 方向约定：本插件预设的坐标变换（RoomPreset#getRotatedRelCoords）从上方观察为
     * 逆时针旋转（北边→西边）。StructureRotation.COUNTERCLOCKWISE_90 同样是
     * 从上方看逆时针 90°，方向一致。270° 逆时针等价于 90° 顺时针。
     */
    private static void rotateBlockData(BlockData bd, int rotation) {
        StructureRotation sr = switch (rotation) {
            case 90 -> StructureRotation.COUNTERCLOCKWISE_90;
            case 180 -> StructureRotation.CLOCKWISE_180;
            case 270 -> StructureRotation.CLOCKWISE_90;
            default -> null;
        };
        if (sr == null) return;
        try {
            bd.rotate(sr);
        } catch (Exception e) {
            // 极少数无法旋转的方块数据保持原方向，不影响整体布局
        }
    }

    /**
     * 恢复容器内容物 / 告示牌文字。
     * extras 在保存时以原始快照坐标存储，直接按原始坐标查找即可。
     */
    private void applyExtra(World world, Block block, int ax, int ay, int az, RoomPreset preset, int origRx, int origRy, int origRz) {
        Map<String, Object> extra = preset.getExtras().get(origRx + ":" + origRy + ":" + origRz);
        if (extra == null) return;

        String type = (String) extra.get("type");
        BlockState st = world.getBlockAt(ax, ay, az).getState();
        try {
            if ("container".equals(type) && st instanceof Container) {
                List<?> itemMaps = (List<?>) extra.get("items");
                ItemStack[] contents = new ItemStack[itemMaps.size()];
                for (int i = 0; i < itemMaps.size(); i++) {
                    Object o = itemMaps.get(i);
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) o;
                        contents[i] = ItemStack.deserialize(m);
                    }
                }
                ((Container) st).getInventory().setContents(contents);
                st.update(true, false);
            } else if ("sign".equals(type) && st instanceof Sign) {
                List<?> lines = (List<?>) extra.get("lines");
                for (int i = 0; i < 4 && i < lines.size(); i++) {
                    ((Sign) st).setLine(i, String.valueOf(lines.get(i)));
                }
                st.update(true, false);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("恢复方块附加数据失败 @ "
                    + ax + "," + ay + "," + az + ": " + e.getMessage());
        }
    }
}