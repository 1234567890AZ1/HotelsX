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

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.*;

/**
 * 酒店房间数据模型
 */
@SerializableAs("HotelRoom")
public class HotelRoom implements ConfigurationSerializable {

    private String id;                  // 唯一ID
    private String name;                // 房间名称
    private UUID owner;                 // 房主UUID
    private String ownerName;           // 房主名称（显示用）

    // 区域坐标
    private String worldName;
    private double x1, y1, z1;
    private double x2, y2, z2;

    // 入口坐标（玩家传送点）
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;

    private double price;               // 入住价格
    private String password;            // 密码（空表示无密码）
    private boolean locked;             // 是否上锁

    private RoomStatus status;          // 房间状态
    private UUID currentGuest;          // 当前入住的客人
    private String currentGuestName;    // 客人名称

    private long createdTime;           // 创建时间
    private long checkinTime;           // 入住时间（客人入住的时间戳）
    private int durationMinutes;        // 使用时长（分钟），0=不限时，-1=使用合集默认
    private List<String> tags;          // 标签列表
    private double discountPrice;       // 折扣价（-1=无折扣）
    private long discountExpire;        // 折扣到期时间戳（0=无折扣）

    public HotelRoom() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.status = RoomStatus.AVAILABLE;
        this.locked = false;
        this.durationMinutes = -1;
        this.tags = new ArrayList<>();
        this.discountPrice = -1;
        this.discountExpire = 0;
        this.createdTime = System.currentTimeMillis();
    }

    public enum RoomStatus {
        AVAILABLE,      // 空闲
        OCCUPIED,       // 已入住
        MAINTENANCE     // 维护中
    }

    // ===== 区域检测方法 =====

    /**
     * 判断坐标是否在房间区域内
     */
    public boolean containsLocation(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    /**
     * 判断两个区域是否重叠
     */
    public boolean overlaps(double ox1, double oy1, double oz1,
                            double ox2, double oy2, double oz2) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        double ominX = Math.min(ox1, ox2);
        double omaxX = Math.max(ox1, ox2);
        double ominY = Math.min(oy1, oy2);
        double omaxY = Math.max(oy1, oy2);
        double ominZ = Math.min(oz1, oz2);
        double omaxZ = Math.max(oz1, oz2);

        return maxX >= ominX && minX <= omaxX &&
               maxY >= ominY && minY <= omaxY &&
               maxZ >= ominZ && minZ <= omaxZ;
    }

    // ===== 序列化 =====

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("owner", owner.toString());
        map.put("ownerName", ownerName);
        map.put("world", worldName);
        map.put("x1", x1);
        map.put("y1", y1);
        map.put("z1", z1);
        map.put("x2", x2);
        map.put("y2", y2);
        map.put("z2", z2);
        map.put("spawnX", spawnX);
        map.put("spawnY", spawnY);
        map.put("spawnZ", spawnZ);
        map.put("spawnYaw", spawnYaw);
        map.put("spawnPitch", spawnPitch);
        map.put("price", price);
        map.put("password", password != null ? password : "");
        map.put("locked", locked);
        map.put("status", status.name());
        map.put("currentGuest", currentGuest != null ? currentGuest.toString() : "");
        map.put("currentGuestName", currentGuestName != null ? currentGuestName : "");
        map.put("createdTime", createdTime);
        map.put("checkinTime", checkinTime);
        map.put("durationMinutes", durationMinutes);
        map.put("tags", tags);
        map.put("discountPrice", discountPrice);
        map.put("discountExpire", discountExpire);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static HotelRoom deserialize(Map<String, Object> map) {
        HotelRoom room = new HotelRoom();
        room.id = (String) map.get("id");
        room.name = (String) map.get("name");
        room.owner = UUID.fromString((String) map.get("owner"));
        room.ownerName = (String) map.get("ownerName");
        room.worldName = (String) map.get("world");
        room.x1 = ((Number) map.getOrDefault("x1", 0)).doubleValue();
        room.y1 = ((Number) map.getOrDefault("y1", 0)).doubleValue();
        room.z1 = ((Number) map.getOrDefault("z1", 0)).doubleValue();
        room.x2 = ((Number) map.getOrDefault("x2", 0)).doubleValue();
        room.y2 = ((Number) map.getOrDefault("y2", 0)).doubleValue();
        room.z2 = ((Number) map.getOrDefault("z2", 0)).doubleValue();
        room.spawnX = ((Number) map.getOrDefault("spawnX", 0)).doubleValue();
        room.spawnY = ((Number) map.getOrDefault("spawnY", 0)).doubleValue();
        room.spawnZ = ((Number) map.getOrDefault("spawnZ", 0)).doubleValue();
        room.spawnYaw = ((Number) map.getOrDefault("spawnYaw", 0)).floatValue();
        room.spawnPitch = ((Number) map.getOrDefault("spawnPitch", 0)).floatValue();
        room.price = ((Number) map.getOrDefault("price", 0)).doubleValue();
        room.password = (String) map.get("password");
        if (room.password == null || room.password.isEmpty()) room.password = null;
        room.locked = map.get("locked") != null && (boolean) map.get("locked");
        Object statusObj = map.get("status");
        room.status = statusObj != null ? RoomStatus.valueOf((String) statusObj) : RoomStatus.AVAILABLE;
        String guestStr = (String) map.get("currentGuest");
        room.currentGuest = guestStr == null || guestStr.isEmpty() ? null : UUID.fromString(guestStr);
        room.currentGuestName = (String) map.get("currentGuestName");
        if (room.currentGuestName != null && room.currentGuestName.isEmpty()) room.currentGuestName = null;
        room.createdTime = ((Number) map.getOrDefault("createdTime", System.currentTimeMillis())).longValue();
        room.checkinTime = ((Number) map.getOrDefault("checkinTime", 0)).longValue();
        room.durationMinutes = ((Number) map.getOrDefault("durationMinutes", -1)).intValue();
        room.tags = (List<String>) map.getOrDefault("tags", new ArrayList<>());
        room.discountPrice = ((Number) map.getOrDefault("discountPrice", -1)).doubleValue();
        room.discountExpire = ((Number) map.getOrDefault("discountExpire", 0)).longValue();
        return room;
    }

    // ===== Getter / Setter =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public double getX1() { return x1; }
    public void setX1(double x1) { this.x1 = x1; }
    public double getY1() { return y1; }
    public void setY1(double y1) { this.y1 = y1; }
    public double getZ1() { return z1; }
    public void setZ1(double z1) { this.z1 = z1; }
    public double getX2() { return x2; }
    public void setX2(double x2) { this.x2 = x2; }
    public double getY2() { return y2; }
    public void setY2(double y2) { this.y2 = y2; }
    public double getZ2() { return z2; }
    public void setZ2(double z2) { this.z2 = z2; }

    public double getSpawnX() { return spawnX; }
    public void setSpawnX(double spawnX) { this.spawnX = spawnX; }
    public double getSpawnY() { return spawnY; }
    public void setSpawnY(double spawnY) { this.spawnY = spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public void setSpawnZ(double spawnZ) { this.spawnZ = spawnZ; }
    public float getSpawnYaw() { return spawnYaw; }
    public void setSpawnYaw(float spawnYaw) { this.spawnYaw = spawnYaw; }
    public float getSpawnPitch() { return spawnPitch; }
    public void setSpawnPitch(float spawnPitch) { this.spawnPitch = spawnPitch; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getPassword() { return password; }
    /** 设置密码（自动 PBKDF2 哈希存储） */
    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            this.password = null;
        } else {
            String hashed = com.hotels.util.PasswordUtil.hash(password);
            this.password = hashed != null ? hashed : password; // 哈希失败时兜底明文
        }
    }
    /** 验证密码（兼容旧版明文存储，首次明文验证成功后自动迁移为哈希） */
    public boolean checkPassword(String plain) {
        if (password == null || password.isEmpty()) return plain == null || plain.isEmpty();
        boolean ok = com.hotels.util.PasswordUtil.verify(plain, password);
        if (ok && com.hotels.util.PasswordUtil.isPlain(password)) {
            // 自动迁移旧版明文密码
            String hashed = com.hotels.util.PasswordUtil.hash(plain);
            if (hashed != null) this.password = hashed;
        }
        return ok;
    }
    public boolean hasPassword() { return password != null && !password.isEmpty(); }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }

    public UUID getCurrentGuest() { return currentGuest; }
    public void setCurrentGuest(UUID currentGuest) { this.currentGuest = currentGuest; }

    public String getCurrentGuestName() { return currentGuestName; }
    public void setCurrentGuestName(String currentGuestName) { this.currentGuestName = currentGuestName; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    public long getCheckinTime() { return checkinTime; }
    public void setCheckinTime(long checkinTime) { this.checkinTime = checkinTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    /**
     * 获取实际使用时长（分钟），0=不限时
     * @param collectionDuration 合集默认时长
     */
    public int getEffectiveDurationMinutes(int collectionDuration) {
        if (durationMinutes == -1) return collectionDuration;
        return durationMinutes;
    }

    /**
     * 获取时长显示文本
     */
    public String getDurationDisplay(int collectionDuration) {
        int effective = getEffectiveDurationMinutes(collectionDuration);
        if (effective <= 0) return "§a不限时";
        if (effective < 60) return "§e" + effective + " 分钟";
        int hours = effective / 60;
        int mins = effective % 60;
        if (mins == 0) return "§e" + hours + " 小时";
        return "§e" + hours + " 小时 " + mins + " 分钟";
    }

    public boolean isOccupied() { return status == RoomStatus.OCCUPIED; }
    public boolean isAvailable() { return status == RoomStatus.AVAILABLE; }

    // ===== 标签操作 =====

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    /**
     * 添加标签
     */
    public boolean addTag(String tag) {
        if (tags.size() >= 3) return false;
        if (tags.contains(tag)) return false;
        return tags.add(tag);
    }

    /**
     * 移除标签
     */
    public boolean removeTag(String tag) {
        return tags.remove(tag);
    }

    /**
     * 是否有指定标签
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * 获取标签显示文本
     */
    public String getTagsDisplay() {
        if (tags.isEmpty()) return "§7无";
        return "§e" + String.join("§7, §e", tags);
    }

    // ===== 折扣操作 =====

    public double getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(double discountPrice) { this.discountPrice = discountPrice; }

    public long getDiscountExpire() { return discountExpire; }
    public void setDiscountExpire(long discountExpire) { this.discountExpire = discountExpire; }

    /**
     * 是否有有效折扣
     */
    public boolean hasActiveDiscount() {
        if (discountPrice < 0) return false;
        if (discountExpire <= 0) return false;
        return System.currentTimeMillis() < discountExpire;
    }

    /**
     * 获取当前实际价格
     */
    public double getCurrentPrice() {
        if (hasActiveDiscount()) return discountPrice;
        return price;
    }

    /**
     * 获取折扣显示文本
     */
    public String getDiscountDisplay() {
        if (!hasActiveDiscount()) return "";
        long remaining = discountExpire - System.currentTimeMillis();
        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        String timeStr;
        if (hours > 0) {
            timeStr = hours + "小时" + (minutes > 0 ? minutes + "分" : "");
        } else {
            timeStr = minutes + "分钟";
        }
        return "§c§l折 扣 §7原价 §f" + price + " §7→ §a" + discountPrice + " §7(剩" + timeStr + ")";
    }

    /**
     * 设置折扣
     * @param discountPrice 折扣价
     * @param durationMinutes 持续分钟
     */
    public void setDiscount(double discountPrice, int durationMinutes) {
        this.discountPrice = discountPrice;
        this.discountExpire = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
    }

    /**
     * 取消折扣
     */
    public void clearDiscount() {
        this.discountPrice = -1;
        this.discountExpire = 0;
    }

    /**
     * 获取区域体积（方块数）
     */
    public long getVolume() {
        long dx = Math.abs((long) Math.ceil(x1) - (long) Math.ceil(x2)) + 1;
        long dy = Math.abs((long) Math.ceil(y1) - (long) Math.ceil(y2)) + 1;
        long dz = Math.abs((long) Math.ceil(z1) - (long) Math.ceil(z2)) + 1;
        return dx * dy * dz;
    }
}
