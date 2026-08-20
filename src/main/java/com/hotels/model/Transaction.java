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
 * 经济流水记录
 */
public class Transaction {

    public enum TxType {
        CHECKIN_PAY,    // 客人入住付款（扣客人）
        CHECKIN_RECV,   // 房主收到租金（加房主）
        EXTEND_PAY,     // 客人续费延长入住（扣客人）
        REFUND          // 退款（暂未使用）
    }

    private String id;
    private TxType type;
    private long timestamp;
    private String playerUUID;  // 操作方UUID
    private String playerName;  // 操作方名字
    private double amount;
    private String roomId;      // 关联房间ID
    private String roomName;    // 房间名（快照）
    private String remark;      // 备注

    public Transaction() {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public TxType getType() { return type; }
    public void setType(TxType type) { this.type = type; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getPlayerUUID() { return playerUUID; }
    public void setPlayerUUID(String playerUUID) { this.playerUUID = playerUUID; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("type", type.name());
        map.put("timestamp", timestamp);
        map.put("playerUUID", playerUUID);
        map.put("playerName", playerName);
        map.put("amount", amount);
        map.put("roomId", roomId);
        map.put("roomName", roomName);
        map.put("remark", remark);
        return map;
    }

    public static Transaction deserialize(Map<String, Object> map) {
        Transaction tx = new Transaction();
        tx.id = (String) map.get("id");
        tx.type = TxType.valueOf((String) map.get("type"));
        tx.timestamp = ((Number) map.get("timestamp")).longValue();
        tx.playerUUID = (String) map.get("playerUUID");
        tx.playerName = (String) map.get("playerName");
        tx.amount = ((Number) map.get("amount")).doubleValue();
        tx.roomId = (String) map.get("roomId");
        tx.roomName = (String) map.get("roomName");
        tx.remark = (String) map.get("remark");
        return tx;
    }
}