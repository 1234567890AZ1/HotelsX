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
 * 店面模型（Web 端商店系统）
 * <p>
 * 一个店面绑定一个容器坐标，出售或收购单一商品。
 * 完全通过 Web 面板管理，无游戏内交互入口，避免与其他商店插件（如 QuickShop）冲突。
 */
public class Shop {

    /** 补货模式 */
    public enum ShopMode {
        /** 自动从容器内同种物品补货/收货 */
        AUTO,
        /** 仅固定库存，不自动补货 */
        FIXED
    }

    private String id;              // 店面唯一 ID
    private String ownerUUID;       // 店主 UUID
    private String ownerName;       // 店主名字
    private String displayName;     // 店铺名称（可空，默认显示坐标）
    private String world;           // 容器所在世界名
    private int x;                  // 容器 X
    private int y;                  // 容器 Y
    private int z;                  // 容器 Z
    private String roomId;          // 所属房间 ID（可空，独立店面）
    private String material;        // 商品 Material 名
    private double price;           // 单价
    private ShopMode mode;          // 补货模式
    private boolean enabled;        // 营业状态
    private double revenue;         // 累计营收
    private long soldCount;         // 累计销量
    /** FIXED 模式固定库存（AUTO 模式库存以容器内物品实时计算，忽略此值） */
    private int stock;
    private long createdAt;         // 创建时间戳

    private boolean isSellShop;     // true=出售给玩家; false=向玩家收购

    public Shop() {
        this.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.createdAt = System.currentTimeMillis();
        this.mode = ShopMode.AUTO;
        this.enabled = false;
        this.isSellShop = true;
    }

    /** 容器的全局定位键，用于容器唯一性检查 */
    public String getLocationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(String ownerUUID) { this.ownerUUID = ownerUUID; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /** 展示名：有店名用店名，否则用容器坐标 */
    public String getDisplayNameSafe() {
        return displayName != null && !displayName.isEmpty()
                ? displayName
                : world + ":" + x + ":" + y + ":" + z;
    }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public ShopMode getMode() { return mode; }
    public void setMode(ShopMode mode) { this.mode = mode; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getRevenue() { return revenue; }
    public void setRevenue(double revenue) { this.revenue = revenue; }

    public void addRevenue(double amount) { this.revenue += amount; }

    public long getSoldCount() { return soldCount; }
    public void setSoldCount(long soldCount) { this.soldCount = soldCount; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = Math.max(0, stock); }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isSellShop() { return isSellShop; }
    public void setSellShop(boolean sellShop) { isSellShop = sellShop; }

    /** 序列化为 YAML 友好的 Map */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("ownerUUID", ownerUUID);
        map.put("ownerName", ownerName);
        map.put("displayName", displayName);
        map.put("world", world);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("roomId", roomId);
        map.put("material", material);
        map.put("price", price);
        map.put("mode", mode.name());
        map.put("enabled", enabled);
        map.put("sellShop", isSellShop);
        map.put("revenue", revenue);
        map.put("soldCount", soldCount);
        map.put("stock", stock);
        map.put("createdAt", createdAt);
        return map;
    }

    /** 从 YAML Map 还原 */
    @SuppressWarnings("unchecked")
    public static Shop deserialize(Map<String, Object> map) {
        Shop shop = new Shop();
        shop.setId((String) map.getOrDefault("id", UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        shop.setOwnerUUID((String) map.getOrDefault("ownerUUID", ""));
        shop.setOwnerName((String) map.getOrDefault("ownerName", ""));
        shop.setDisplayName((String) map.getOrDefault("displayName", null));
        shop.setWorld((String) map.getOrDefault("world", ""));
        shop.setX(((Number) map.getOrDefault("x", 0)).intValue());
        shop.setY(((Number) map.getOrDefault("y", 0)).intValue());
        shop.setZ(((Number) map.getOrDefault("z", 0)).intValue());
        shop.setRoomId((String) map.getOrDefault("roomId", null));
        shop.setMaterial((String) map.getOrDefault("material", null));
        shop.setPrice(((Number) map.getOrDefault("price", 0)).doubleValue());
        try {
            shop.setMode(ShopMode.valueOf((String) map.getOrDefault("mode", "AUTO")));
        } catch (IllegalArgumentException e) {
            shop.setMode(ShopMode.AUTO);
        }
        shop.setEnabled(Boolean.TRUE.equals(map.getOrDefault("enabled", false)));
        shop.setSellShop(Boolean.TRUE.equals(map.getOrDefault("sellShop", true)));
        shop.setRevenue(((Number) map.getOrDefault("revenue", 0)).doubleValue());
        shop.setSoldCount(((Number) map.getOrDefault("soldCount", 0)).longValue());
        shop.setStock(((Number) map.getOrDefault("stock", 0)).intValue());
        shop.setCreatedAt(((Number) map.getOrDefault("createdAt", System.currentTimeMillis())).longValue());
        return shop;
    }
}