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
package com.hotels.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ShopService 纯逻辑单元测试 —— 不依赖 Bukkit 服务器运行时，
 * 覆盖容器类型识别与 Material 匹配等可独立验证的静态/工具逻辑。
 * （购买/补货/交付等依赖 Economy/Vault/容器的流程在集成测试中覆盖）
 */
class ShopServiceTest {

    // ─── isContainerMaterial ─────────────────────────────────

    @Nested
    @DisplayName("isContainerMaterial 容器类型识别")
    class ContainerTypeTest {

        @Test
        @DisplayName("箱子/陷阱箱/木桶属于容器")
        void basicContainers() {
            assertTrue(ShopService.isContainerMaterial(Material.CHEST));
            assertTrue(ShopService.isContainerMaterial(Material.TRAPPED_CHEST));
            assertTrue(ShopService.isContainerMaterial(Material.BARREL));
        }

        @Test
        @DisplayName("投掷器/发射器/漏斗属于容器")
        void functionalContainers() {
            assertTrue(ShopService.isContainerMaterial(Material.DISPENSER));
            assertTrue(ShopService.isContainerMaterial(Material.DROPPER));
            assertTrue(ShopService.isContainerMaterial(Material.HOPPER));
        }

        @Test
        @DisplayName("所有潜影盒变种属于容器")
        void shulkerBoxes() {
            assertTrue(ShopService.isContainerMaterial(Material.SHULKER_BOX));
            assertTrue(ShopService.isContainerMaterial(Material.BLACK_SHULKER_BOX));
            assertTrue(ShopService.isContainerMaterial(Material.RED_SHULKER_BOX));
            assertTrue(ShopService.isContainerMaterial(Material.WHITE_SHULKER_BOX));
        }

        @Test
        @DisplayName("普通方块不属于容器")
        void nonContainers() {
            assertFalse(ShopService.isContainerMaterial(Material.STONE));
            assertFalse(ShopService.isContainerMaterial(Material.OAK_PLANKS));
            assertFalse(ShopService.isContainerMaterial(Material.CRAFTING_TABLE));
            assertFalse(ShopService.isContainerMaterial(Material.ENDER_CHEST));
        }

        @Test
        @DisplayName("null 输入返回 false")
        void nullSafe() {
            assertFalse(ShopService.isContainerMaterial(null));
        }
    }

    // ─── matchMaterial ───────────────────────────────────────

    @Nested
    @DisplayName("matchMaterial 商品类型匹配")
    class MaterialMatchTest {

        @Test
        @DisplayName("大写/小写/混合大小写均可匹配")
        void caseInsensitive() {
            assertEquals(Material.DIAMOND, ShopService.matchMaterial("DIAMOND"));
            assertEquals(Material.DIAMOND, ShopService.matchMaterial("diamond"));
            assertEquals(Material.DIAMOND, ShopService.matchMaterial("Diamond"));
        }

        @Test
        @DisplayName("首尾空白会被修剪")
        void trimsWhitespace() {
            assertEquals(Material.DIAMOND, ShopService.matchMaterial(" diamond "));
        }

        @Test
        @DisplayName("非法类型返回 null")
        void invalidMaterialNull() {
            assertNull(ShopService.matchMaterial("NOT_A_REAL_MATERIAL"));
            assertNull(ShopService.matchMaterial(""));
            assertNull(ShopService.matchMaterial(null));
        }
    }
}