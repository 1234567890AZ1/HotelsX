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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 房间评分记录
 */
public class Rating {

    private String id;
    private String roomId;      // 房间ID
    private String roomName;    // 房间名（快照）
    private String guestUuid;   // 评分玩家UUID
    private String guestName;   // 评分玩家名
    private int score;          // 分数 1-5
    private String comment;     // 评语（可空）
    private long time;          // 评分时间

    public Rating() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.time = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getGuestUuid() { return guestUuid; }
    public void setGuestUuid(String guestUuid) { this.guestUuid = guestUuid; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }

    /**
     * 获取星星显示文本（如 ★★★☆☆）
     */
    public String getStars() {
        int s = Math.max(1, Math.min(5, score));
        return "★".repeat(s) + "☆".repeat(5 - s);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("roomId", roomId);
        map.put("roomName", roomName != null ? roomName : "");
        map.put("guestUuid", guestUuid);
        map.put("guestName", guestName != null ? guestName : "");
        map.put("score", score);
        map.put("comment", comment != null ? comment : "");
        map.put("time", time);
        return map;
    }

    public static Rating deserialize(Map<String, Object> map) {
        Rating r = new Rating();
        r.id = (String) map.get("id");
        r.roomId = (String) map.get("roomId");
        r.roomName = (String) map.get("roomName");
        r.guestUuid = (String) map.get("guestUuid");
        r.guestName = (String) map.get("guestName");
        r.score = ((Number) map.getOrDefault("score", 5)).intValue();
        r.comment = (String) map.get("comment");
        r.time = ((Number) map.getOrDefault("time", System.currentTimeMillis())).longValue();
        return r;
    }
}
