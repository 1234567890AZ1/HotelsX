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
 * 待领取交付记录（Web 店铺购买但玩家不在线/背包满时的物品队列）
 * 玩家下次上线时自动补发到背包，也可在 Web 面板查看。
 */
public class ShopDelivery {

    private String id;
    private String playerUUID;      // 买家 UUID
    private String playerName;      // 买家名字
    private String shopId;          // 来源店铺 ID（店铺被删后仍可领取）
    private String material;        // 商品 Material 名
    private int amount;             // 未领取数量
    private double price;           // 单件成交价
    private long createdAt;         // 创建时间戳

    public ShopDelivery() {
        this.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerUUID() { return playerUUID; }
    public void setPlayerUUID(String playerUUID) { this.playerUUID = playerUUID; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = Math.max(0, amount); }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("playerUUID", playerUUID);
        map.put("playerName", playerName);
        map.put("shopId", shopId);
        map.put("material", material);
        map.put("amount", amount);
        map.put("price", price);
        map.put("createdAt", createdAt);
        return map;
    }

    public static ShopDelivery deserialize(Map<String, Object> map) {
        ShopDelivery d = new ShopDelivery();
        d.setId((String) map.getOrDefault("id", UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        d.setPlayerUUID((String) map.getOrDefault("playerUUID", ""));
        d.setPlayerName((String) map.getOrDefault("playerName", ""));
        d.setShopId((String) map.getOrDefault("shopId", null));
        d.setMaterial((String) map.getOrDefault("material", null));
        d.setAmount(((Number) map.getOrDefault("amount", 0)).intValue());
        d.setPrice(((Number) map.getOrDefault("price", 0)).doubleValue());
        d.setCreatedAt(((Number) map.getOrDefault("createdAt", System.currentTimeMillis())).longValue());
        return d;
    }
}