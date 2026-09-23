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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ShopDelivery 模型单元测试 —— 覆盖待领取队列记录的序列化与金额边界。
 */
class ShopDeliveryTest {

    private ShopDelivery buildDelivery() {
        ShopDelivery d = new ShopDelivery();
        d.setPlayerUUID("buyer-uuid");
        d.setPlayerName("买家乙");
        d.setShopId("shop-abc");
        d.setMaterial("DIAMOND");
        d.setAmount(32);
        d.setPrice(100.0);
        return d;
    }

    // ─── 序列化 ───────────────────────────────────────────────

    @Nested
    @DisplayName("YAML 序列化与反序列化")
    class SerializationTest {

        @Test
        @DisplayName("完整字段往返保持一致")
        void fullRoundTrip() {
            ShopDelivery original = buildDelivery();
            Map<String, Object> map = original.serialize();
            ShopDelivery restored = ShopDelivery.deserialize(map);

            assertEquals(original.getId(), restored.getId());
            assertEquals("buyer-uuid", restored.getPlayerUUID());
            assertEquals("买家乙", restored.getPlayerName());
            assertEquals("shop-abc", restored.getShopId());
            assertEquals("DIAMOND", restored.getMaterial());
            assertEquals(32, restored.getAmount());
            assertEquals(100.0, restored.getPrice(), 0.0001);
            assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        }

        @Test
        @DisplayName("旧数据缺少字段时使用默认值")
        void deserializeDefaults() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", "dlv-001");
            map.put("playerUUID", "u1");

            ShopDelivery d = ShopDelivery.deserialize(map);
            assertEquals("dlv-001", d.getId());
            assertEquals("u1", d.getPlayerUUID());
            assertEquals("", d.getPlayerName());
            assertEquals(0, d.getAmount());
            assertEquals(0.0, d.getPrice(), 0.0001);
            assertNotNull(d.getCreatedAt());
        }

        @Test
        @DisplayName("默认构造器生成唯一 ID")
        void defaultIdUnique() {
            ShopDelivery a = new ShopDelivery();
            ShopDelivery b = new ShopDelivery();
            assertEquals(12, a.getId().length());
            assertTrue(!a.getId().equals(b.getId()), "每次创建的交付记录 ID 应不同");
        }
    }

    // ─── 金额边界 ─────────────────────────────────────────────

    @Nested
    @DisplayName("数量边界")
    class AmountTest {

        @Test
        @DisplayName("负数量被钳制为 0")
        void clampNegativeAmount() {
            ShopDelivery d = buildDelivery();
            d.setAmount(-5);
            assertEquals(0, d.getAmount());
        }

        @Test
        @DisplayName("正常数量原样保留")
        void keepPositiveAmount() {
            ShopDelivery d = buildDelivery();
            d.setAmount(7);
            assertEquals(7, d.getAmount());
        }
    }
}