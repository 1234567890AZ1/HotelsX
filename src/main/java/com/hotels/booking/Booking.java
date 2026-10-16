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
package com.hotels.booking;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 房间预订记录
 */
public class Booking {

    public enum Status {
        PENDING,    // 等待开始
        ACTIVE,     // 已按时入住
        COMPLETED,  // 已完成
        CANCELLED,  // 已取消（退款）
        NO_SHOW,    // 到点未到场（视为放弃，退款按规则）
        FAILED      // 到点时房间不可用（全额退款）
    }

    private String id;
    private String roomId;
    private String roomName;      // 房间名快照
    private String ownerUuid;     // 房主 UUID（快照，便于退款冲正）
    private String ownerName;     // 房主名（快照）
    private String guestUuid;
    private String guestName;
    private long startTime;       // 预订的开始时间
    private int durationMinutes;  // 预订时长
    private double paidAmount;    // 实际支付的预订金额
    private Status status;
    private long createdTime;

    public Booking() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.status = Status.PENDING;
        this.createdTime = System.currentTimeMillis();
    }

    public long getEndTime() {
        return startTime + durationMinutes * 60L * 1000L;
    }

    // ===== Getter / Setter =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getGuestUuid() { return guestUuid; }
    public void setGuestUuid(String guestUuid) { this.guestUuid = guestUuid; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("roomId", roomId);
        map.put("roomName", roomName);
        map.put("ownerUuid", ownerUuid);
        map.put("ownerName", ownerName);
        map.put("guestUuid", guestUuid);
        map.put("guestName", guestName);
        map.put("startTime", startTime);
        map.put("durationMinutes", durationMinutes);
        map.put("paidAmount", paidAmount);
        map.put("status", status.name());
        map.put("createdTime", createdTime);
        return map;
    }

    public static Booking deserialize(Map<String, Object> map) {
        Booking b = new Booking();
        b.id = (String) map.get("id");
        b.roomId = (String) map.get("roomId");
        b.roomName = (String) map.get("roomName");
        b.ownerUuid = (String) map.get("ownerUuid");
        b.ownerName = (String) map.get("ownerName");
        b.guestUuid = (String) map.get("guestUuid");
        b.guestName = (String) map.get("guestName");
        b.startTime = ((Number) map.get("startTime")).longValue();
        b.durationMinutes = ((Number) map.getOrDefault("durationMinutes", 0)).intValue();
        b.paidAmount = ((Number) map.getOrDefault("paidAmount", 0)).doubleValue();
        b.status = Status.valueOf((String) map.getOrDefault("status", "PENDING"));
        b.createdTime = ((Number) map.getOrDefault("createdTime", 0L)).longValue();
        return b;
    }
}
