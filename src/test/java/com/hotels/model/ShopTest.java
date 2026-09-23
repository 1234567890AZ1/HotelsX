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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shop 模型单元测试 —— 覆盖展示名、定位键、库存边界与 YAML 序列化。
 */
class ShopTest {

    /** 构建一个已绑定容器坐标的店铺 */
    private Shop buildShop() {
        Shop shop = new Shop();
        shop.setWorld("world");
        shop.setX(10);
        shop.setY(60);
        shop.setZ(-5);
        shop.setMaterial("DIAMOND");
        shop.setPrice(100.0);
        shop.setOwnerUUID("owner-uuid");
        shop.setOwnerName("店主甲");
        return shop;
    }

    // ─── 展示名 ───────────────────────────────────────────────

    @Nested
    @DisplayName("getDisplayNameSafe 展示名")
    class DisplayNameTest {

        @Test
        @DisplayName("无店名时使用容器坐标作为展示名")
        void defaultIsCoordinate() {
            Shop shop = buildShop();
            assertEquals("world:10:60:-5", shop.getDisplayNameSafe());
        }

        @Test
        @DisplayName("有店名时优先使用店名")
        void preferDisplayName() {
            Shop shop = buildShop();
            shop.setDisplayName("云端杂货铺");
            assertEquals("云端杂货铺", shop.getDisplayNameSafe());
        }

        @Test
        @DisplayName("空字符串店名视为未设置")
        void emptyDisplayNameFallsBack() {
            Shop shop = buildShop();
            shop.setDisplayName("");
            assertEquals("world:10:60:-5", shop.getDisplayNameSafe());
        }
    }

    // ─── 定位键 ───────────────────────────────────────────────

    @Nested
    @DisplayName("getLocationKey 容器定位键")
    class LocationKeyTest {

        @Test
        @DisplayName("定位键由 世界:x:y:z 组成")
        void keyFormat() {
            Shop shop = buildShop();
            assertEquals("world:10:60:-5", shop.getLocationKey());
        }

        @Test
        @DisplayName("坐标不同则定位键不同（唯一性判断基础）")
        void keyDifferentByCoord() {
            Shop a = buildShop();
            Shop b = buildShop();
            b.setX(11);
            assertEquals("world:11:60:-5", b.getLocationKey());
        }
    }

    // ─── 库存边界 ─────────────────────────────────────────────

    @Nested
    @DisplayName("setStock 库存边界")
    class StockTest {

        @Test
        @DisplayName("负库存被钳制为 0")
        void clampNegativeStock() {
            Shop shop = buildShop();
            shop.setStock(-3);
            assertEquals(0, shop.getStock());
        }

        @Test
        @DisplayName("正常库存原样保留")
        void keepPositiveStock() {
            Shop shop = buildShop();
            shop.setStock(64);
            assertEquals(64, shop.getStock());
        }
    }

    // ─── 序列化 ───────────────────────────────────────────────

    @Nested
    @DisplayName("YAML 序列化与反序列化")
    class SerializationTest {

        @Test
        @DisplayName("完整字段往返保持一致")
        void fullRoundTrip() {
            Shop original = buildShop();
            original.setDisplayName("山城超市");
            original.setMode(Shop.ShopMode.FIXED);
            original.setStock(50);
            original.setEnabled(true);
            original.setRevenue(1234.56);
            original.setSoldCount(99);
            original.addRevenue(10.0);
            original.setSellShop(true);

            Map<String, Object> map = original.serialize();
            Shop restored = Shop.deserialize(map);

            assertEquals(original.getId(), restored.getId());
            assertEquals(original.getOwnerUUID(), restored.getOwnerUUID());
            assertEquals(original.getOwnerName(), restored.getOwnerName());
            assertEquals("山城超市", restored.getDisplayName());
            assertEquals(original.getWorld(), restored.getWorld());
            assertEquals(original.getX(), restored.getX());
            assertEquals(original.getY(), restored.getY());
            assertEquals(original.getZ(), restored.getZ());
            assertEquals("DIAMOND", restored.getMaterial());
            assertEquals(100.0, restored.getPrice(), 0.0001);
            assertEquals(Shop.ShopMode.FIXED, restored.getMode());
            assertEquals(50, restored.getStock());
            assertEquals(1244.56, restored.getRevenue(), 0.0001);
            assertEquals(99, restored.getSoldCount());
            assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        }

        @Test
        @DisplayName("旧数据缺少字段时使用默认值（兼容旧版 shops.yml）")
        void deserializeDefaults() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", "abc123");
            map.put("world", "world");
            map.put("x", 1);
            map.put("y", 2);
            map.put("z", 3);
            map.put("material", "STONE");
            map.put("price", 5.5);

            Shop shop = Shop.deserialize(map);
            assertEquals("abc123", shop.getId());
            assertEquals("", shop.getOwnerUUID());
            assertEquals(Shop.ShopMode.AUTO, shop.getMode());
            assertEquals(0, shop.getStock());
            assertEquals(0.0, shop.getRevenue(), 0.0001);
            assertEquals(1, shop.getX());
            assertNotNull(shop.getCreatedAt());
        }

        @Test
        @DisplayName("非法的 mode 字符串回退为 AUTO")
        void invalidModeFallsBack() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("mode", "INVALID_MODE");
            Shop shop = Shop.deserialize(map);
            assertEquals(Shop.ShopMode.AUTO, shop.getMode());
        }

        @Test
        @DisplayName("默认构造器生成唯一 ID 与 AUTO 模式")
        void defaultsOnCreate() {
            Shop shop = buildShop();
            assertEquals(Shop.ShopMode.AUTO, shop.getMode());
            assertNotNull(shop.getId());
            assertEquals(12, shop.getId().length());
        }
    }

    // ─── 补充：ID 唯一性 ──────────────────────────────────────

    @Nested
    @DisplayName("ID 唯一性")
    class IdUniquenessTest {

        @Test
        @DisplayName("每次创建的店铺 ID 均不同")
        void idsUnique() {
            Shop a = new Shop();
            Shop b = new Shop();
            Shop c = new Shop();
            assertEquals(12, a.getId().length());
            assertEquals(false, a.getId().equals(b.getId()));
            assertEquals(false, b.getId().equals(c.getId()));
        }
    }
}