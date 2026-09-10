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
package com.hotels.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间装修预设数据模型。
 *
 * 预设保存某个房间区域的完整装修快照，包括：
 * - 尺寸（sizeX/sizeY/sizeZ）：用于校验目标房间是否与预设大小完全一致
 * - 旋转角度（rotation）：围绕 Y 轴顺时针旋转（0/90/180/270），仅影响 X/Z 维度
 * - 方块快照列表：每行形状为 "relX,relY,relZ|blockData字符串"（跳过空气方块）
 * - 附加数据（extras）：容器内容物、告示牌文字等无法仅靠 BlockData 表达的内容
 *
 * 应用时支持旋转：仅当目标房间的长/宽/高与旋转后的预设尺寸完全一致时才允许应用。
 */
@SuppressWarnings("unchecked")
public class RoomPreset {

    /** 预设名称（唯一标识） */
    private String name;
    /** 预设来源世界（用于展示与校验） */
    private String world;
    /** 旋转角度（围绕 Y 轴顺时针），0/90/180/270 */
    private int rotation;

    /** 预设的长/宽/高（方块数，含两端） */
    private int sizeX;
    private int sizeY;
    private int sizeZ;

    /**
     * 方块快照行，格式: "relX,relY,relZ|blockData"
     * relX/relY/relZ 为相对预设最小角的偏移
     */
    private List<String> blocks;

    /**
     * 附加数据:
     * key  = "relX:relY:relZ"
     * value = Map: {type: "container"|"sign", ...}
     */
    private Map<String, Map<String, Object>> extras;

    public RoomPreset() {
        this.blocks = new ArrayList<>();
        this.extras = new LinkedHashMap<>();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public int getRotation() { return rotation; }
    public void setRotation(int rotation) {
        // 归一化到 [0, 360)，并校验是否为 90 度整数倍
        int norm = (rotation % 360 + 360) % 360;
        if (norm % 90 != 0) {
            throw new IllegalArgumentException("旋转角度必须是 0、90、180 或 270，收到: " + rotation);
        }
        this.rotation = norm;
    }

    public int getSizeX() { return sizeX; }
    public void setSizeX(int sizeX) { this.sizeX = sizeX; }
    public int getSizeY() { return sizeY; }
    public void setSizeY(int sizeY) { this.sizeY = sizeY; }
    public int getSizeZ() { return sizeZ; }
    public void setSizeZ(int sizeZ) { this.sizeZ = sizeZ; }

    public List<String> getBlocks() { return blocks; }
    public void setBlocks(List<String> blocks) { this.blocks = blocks; }

    public Map<String, Map<String, Object>> getExtras() { return extras; }
    public void setExtras(Map<String, Map<String, Object>> extras) { this.extras = extras; }

    /**
     * 尺寸是否与目标房间完全一致（支持旋转后校验）。
     * 旋转围绕 Y 轴顺时针，仅交换 X/Z 维度，Y 不变。
     */
    public boolean matchesSize(int width, int height, int depth) {
        if (rotation == 0 || rotation == 180) {
            return sizeX == width && sizeY == height && sizeZ == depth;
        } else {
            // 90° 或 270°：X/Z 互换
            return sizeZ == width && sizeY == height && sizeX == depth;
        }
    }

    /**
     * 获取旋转后的尺寸显示
     */
    public String getSizeDisplay() {
        // 90° 和 270° 时 X/Z 互换，0° 和 180° 时保持不变
        if (rotation == 90 || rotation == 270) {
            return sizeZ + " x " + sizeY + " x " + sizeX;
        } else {
            return sizeX + " x " + sizeY + " x " + sizeZ;
        }
    }

    /**
     * 创建指定旋转角度的应用副本（浅拷贝：blocks/extras 共享引用）。
     * 用于命令层以不同角度应用预设时，避免修改存储中的原预设。
     */
    public RoomPreset copyWithRotation(int rotation) {
        RoomPreset copy = new RoomPreset();
        copy.name = this.name;
        copy.world = this.world;
        copy.rotation = rotation;
        copy.sizeX = this.sizeX;
        copy.sizeY = this.sizeY;
        copy.sizeZ = this.sizeZ;
        copy.blocks = this.blocks;
        copy.extras = this.extras;
        return copy;
    }

    // ===== YAML 序列化 =====

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("world", world);
        map.put("rotation", rotation);
        map.put("sizeX", sizeX);
        map.put("sizeY", sizeY);
        map.put("sizeZ", sizeZ);
        map.put("blocks", blocks);
        if (!extras.isEmpty()) {
            map.put("extras", extras);
        }
        return map;
    }

    public static RoomPreset deserialize(Map<String, Object> map) {
        RoomPreset preset = new RoomPreset();
        preset.name = (String) map.get("name");
        preset.world = (String) map.get("world");
        preset.rotation = ((Number) map.getOrDefault("rotation", 0)).intValue();
        preset.sizeX = ((Number) map.getOrDefault("sizeX", 0)).intValue();
        preset.sizeY = ((Number) map.getOrDefault("sizeY", 0)).intValue();
        preset.sizeZ = ((Number) map.getOrDefault("sizeZ", 0)).intValue();
        Object blocksObj = map.get("blocks");
        if (blocksObj instanceof List) {
            preset.blocks = new ArrayList<>((List<String>) blocksObj);
        }
        Object extrasObj = map.get("extras");
        if (extrasObj instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) extrasObj).entrySet()) {
                if (e.getValue() instanceof Map) {
                    preset.extras.put(e.getKey(), (Map<String, Object>) e.getValue());
                }
            }
        }
        return preset;
    }

    // ===== 方块快照行解析 =====

    /**
     * 获取旋转后的相对坐标（围绕 Y 轴顺时针旋转）
     * 用于在应用预设时将本地相对坐标转换为旋转后的坐标
     */
    public int[] getRotatedRelCoords(int rx, int ry, int rz) {
        if (rotation == 0) {
            return new int[]{rx, ry, rz};
        } else if (rotation == 90) {
            // 顺时针 90°：(x, z) -> (z, sizeX - 1 - x)
            return new int[]{rz, ry, sizeX - 1 - rx};
        } else if (rotation == 180) {
            // 180°：(x, z) -> (sizeX - 1 - x, sizeZ - 1 - z)
            return new int[]{sizeX - 1 - rx, ry, sizeZ - 1 - rz};
        } else { // 270
            // 逆时针 90°：(x, z) -> (sizeZ - 1 - z, x)
            return new int[]{sizeZ - 1 - rz, ry, rx};
        }
    }

    public static final class SnapshotLine {
        public final int x, y, z;
        public final String data;

        public SnapshotLine(int x, int y, int z, String data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }

        public String encode() {
            return x + "," + y + "," + z + "|" + data;
        }
    }

    public static SnapshotLine parseLine(String line) {
        int sep = line.indexOf('|');
        if (sep <= 0) return null;
        String[] parts = line.substring(0, sep).split(",");
        if (parts.length != 3) return null;
        try {
            return new SnapshotLine(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    line.substring(sep + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}