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

import java.util.*;

/**
 * 房间合集（酒店）
 */
public class RoomCollection {

    private String id;
    private String name;
    private String description;
    private UUID owner;
    private String ownerName;
    private List<String> roomIds; // 包含的房间ID列表
    private List<String> admins;  // 管理员UUID列表
    private int durationMinutes;  // 使用时长（分钟），0=不限时
    private long createdTime;

    public RoomCollection() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.roomIds = new ArrayList<>();
        this.admins = new ArrayList<>();
        this.durationMinutes = 0;
        this.createdTime = System.currentTimeMillis();
    }

    // ===== 序列化 =====

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("description", description != null ? description : "");
        map.put("owner", owner.toString());
        map.put("ownerName", ownerName);
        map.put("roomIds", roomIds);
        map.put("admins", admins);
        map.put("durationMinutes", durationMinutes);
        map.put("createdTime", createdTime);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static RoomCollection deserialize(Map<String, Object> map) {
        RoomCollection col = new RoomCollection();
        col.id = (String) map.get("id");
        col.name = (String) map.get("name");
        col.description = (String) map.get("description");
        if (col.description.isEmpty()) col.description = null;
        col.owner = UUID.fromString((String) map.get("owner"));
        col.ownerName = (String) map.get("ownerName");
        col.roomIds = (List<String>) map.get("roomIds");
        col.admins = (List<String>) map.getOrDefault("admins", new ArrayList<>());
        col.durationMinutes = ((Number) map.getOrDefault("durationMinutes", 0)).intValue();
        col.createdTime = ((Number) map.get("createdTime")).longValue();
        return col;
    }

    // ===== Getter / Setter =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public List<String> getRoomIds() { return roomIds; }
    public void setRoomIds(List<String> roomIds) { this.roomIds = roomIds; }

    public List<String> getAdmins() { return admins; }
    public void setAdmins(List<String> admins) { this.admins = admins; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = Math.max(0, durationMinutes); }

    /**
     * 获取时长显示文本
     */
    public String getDurationDisplay() {
        if (durationMinutes <= 0) return "§a不限时";
        if (durationMinutes < 60) return "§e" + durationMinutes + " 分钟";
        int hours = durationMinutes / 60;
        int mins = durationMinutes % 60;
        if (mins == 0) return "§e" + hours + " 小时";
        return "§e" + hours + " 小时 " + mins + " 分钟";
    }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    /**
     * 添加房间到合集
     */
    public boolean addRoom(String roomId) {
        if (roomIds.contains(roomId)) return false;
        return roomIds.add(roomId);
    }

    /**
     * 从合集移除房间
     */
    public boolean removeRoom(String roomId) {
        return roomIds.remove(roomId);
    }

    public int getRoomCount() { return roomIds.size(); }

    // ===== 管理员操作 =====

    /**
     * 判断玩家是否是房主或管理员
     */
    public boolean canManage(UUID playerUUID) {
        if (owner.equals(playerUUID)) return true;
        return admins.contains(playerUUID.toString());
    }

    /**
     * 添加管理员
     */
    public boolean addAdmin(UUID playerUUID) {
        String uuidStr = playerUUID.toString();
        if (admins.contains(uuidStr)) return false;
        if (owner.equals(playerUUID)) return false; // 房主不能加自己为管理员
        return admins.add(uuidStr);
    }

    /**
     * 移除管理员
     */
    public boolean removeAdmin(UUID playerUUID) {
        return admins.remove(playerUUID.toString());
    }

    public int getAdminCount() { return admins.size(); }
}
