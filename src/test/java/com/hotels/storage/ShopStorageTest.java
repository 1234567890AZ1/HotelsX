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
package com.hotels.storage;

import com.hotels.model.Shop;
import com.hotels.model.ShopDelivery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ShopStorage 数据读写单元测试 —— 使用临时目录验证真实 YAML 落盘
 * 与 UTF-8 中文编码，以及容器唯一性、待领取队列等业务规则。
 */
class ShopStorageTest {

    @TempDir
    File tempDir;

    private ShopStorage newStorage() {
        return new ShopStorage(tempDir, Logger.getLogger("ShopStorageTest")); 
    }

    private Shop buildShop(String id, String world, int x, int y, int z) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setOwnerUUID(id + "-owner");
        shop.setOwnerName("店主");
        shop.setDisplayName("中文店名-" + id);
        shop.setWorld(world);
        shop.setX(x);
        shop.setY(y);
        shop.setZ(z);
        shop.setMaterial("DIAMOND");
        shop.setPrice(100.0);
        shop.setMode(Shop.ShopMode.FIXED);
        shop.setStock(10);
        return shop;
    }

    // ─── 店面 ────────────────────────────────────────────────

    @Nested
    @DisplayName("店面创建与唯一性")
    class ShopCrudTest {

        @Test
        @DisplayName("同一容器只能创建一个店面")
        void containerUniqueness() {
            ShopStorage storage = newStorage();
            assertTrue(storage.createShop(buildShop("A", "world", 1, 2, 3)));
            assertFalse(storage.createShop(buildShop("B", "world", 1, 2, 3)), "同一容器不允许重复开店");
            assertEquals(1, storage.getShopCount());
            assertNull(storage.getShop("B"), "被拒绝的店铺不应出现在数据中");
        }

        @Test
        @DisplayName("坐标不同的容器可分别开店")
        void differentContainers() {
            ShopStorage storage = newStorage();
            assertTrue(storage.createShop(buildShop("A", "world", 1, 2, 3)));
            assertTrue(storage.createShop(buildShop("B", "world", 1, 2, 4)));
            assertEquals(2, storage.getShopCount());
        }

        @Test
        @DisplayName("按位置查找与按店主查找")
        void lookups() {
            ShopStorage storage = newStorage();
            storage.createShop(buildShop("A", "world", 1, 2, 3));
            Shop found = storage.getShopAt("world", 1, 2, 3);
            assertNotNull(found);
            assertEquals("A", found.getId());
            assertEquals(1, storage.getShopsByOwner("A-owner").size());
            assertEquals(0, storage.getShopsByOwner("nobody").size());
        }

        @Test
        @DisplayName("删除店面后位置索引同步移除")
        void removeShop() {
            ShopStorage storage = newStorage();
            storage.createShop(buildShop("A", "world", 1, 2, 3));
            storage.removeShop("A");
            assertNull(storage.getShop("A"));
            assertNull(storage.getShopAt("world", 1, 2, 3));
            // 删除后同一容器可再次开店
            assertTrue(storage.createShop(buildShop("B", "world", 1, 2, 3)));
        }
    }

    // ─── 持久化 ──────────────────────────────────────────────

    @Nested
    @DisplayName("YAML 落盘与重载")
    class PersistenceTest {

        @Test
        @DisplayName("saveAll/loadAll 后数据完整恢复（含中文）")
        void roundTripWithChinese() {
            ShopStorage storage = newStorage();
            storage.createShop(buildShop("A", "world", 1, 2, 3));
            storage.addContainer("world:5:5:5", "player-uuid");

            ShopDelivery d = new ShopDelivery();
            d.setPlayerUUID("buyer");
            d.setPlayerName("买家丙");
            d.setShopId("A");
            d.setMaterial("DIAMOND");
            d.setAmount(8);
            storage.addDelivery(d);

            // 重新打开（模拟重启服务器）
            ShopStorage reloaded = new ShopStorage(tempDir, Logger.getLogger("ShopStorageTest"));
            reloaded.loadAll();

            Shop shop = reloaded.getShopAt("world", 1, 2, 3);
            assertNotNull(shop);
            assertEquals("A", shop.getId());
            assertEquals("中文店名-A", shop.getDisplayName());
            assertEquals(Shop.ShopMode.FIXED, shop.getMode());
            assertEquals(10, shop.getStock());

            assertEquals("player-uuid", reloaded.getContainerOwner("world:5:5:5"));

            List<ShopDelivery> dels = reloaded.getDeliveriesForPlayer("buyer");
            assertEquals(1, dels.size());
            assertEquals("买家丙", dels.get(0).getPlayerName());
            assertEquals(8, dels.get(0).getAmount());
        }

        @Test
        @DisplayName("shops.yml 以 UTF-8 编码保存（中文不乱码）")
        void utf8Encoding() throws Exception {
            ShopStorage storage = newStorage();
            storage.createShop(buildShop("A", "world", 1, 2, 3));

            String content = Files.readString(
                    new File(tempDir, "shops.yml").toPath(), StandardCharsets.UTF_8);
            assertTrue(content.contains("中文店名-A"), "YAML 文件应包含 UTF-8 中文店名");
            // 不应出现乱码标记（如 ??
            assertFalse(content.contains("\\uFFFD"));
        }

        @Test
        @DisplayName("文件夹不存在时自动创建")
        void createsDataDir() throws Exception {
            File nested = new File(tempDir, "a/b/c");
            ShopStorage storage = new ShopStorage(nested, Logger.getLogger("ShopStorageTest"));
            storage.createShop(buildShop("A", "world", 1, 2, 3));
            assertTrue(new File(nested, "shops.yml").exists(), "自动创建数据目录并落盘");
        }
    }

    // ─── 待领取队列 ──────────────────────────────────────────

    @Nested
    @DisplayName("待领取交付队列")
    class DeliveryQueueTest {

        @Test
        @DisplayName("按玩家查询、更新与移除")
        void queueLifecycle() {
            ShopStorage storage = newStorage();
            ShopDelivery d = new ShopDelivery();
            d.setPlayerUUID("buyer-1");
            d.setMaterial("STONE");
            d.setAmount(16);
            storage.addDelivery(d);

            List<ShopDelivery> list = storage.getDeliveriesForPlayer("buyer-1");
            assertEquals(1, list.size());

            // 部分领取（模拟背包满场景）
            ShopDelivery cur = list.get(0);
            cur.setAmount(10);
            storage.updateDelivery(cur);
            assertEquals(10, storage.getDelivery(cur.getId()).getAmount());

            // 全部领取后移除
            storage.removeDelivery(cur.getId());
            assertTrue(storage.getDeliveriesForPlayer("buyer-1").isEmpty());
            assertEquals(0, storage.getPendingDeliveryCount());
        }

        @Test
        @DisplayName("不同玩家的交付互不干扰")
        void isolatedByPlayer() {
            ShopStorage storage = newStorage();
            ShopDelivery a = new ShopDelivery();
            a.setPlayerUUID("p1");
            ShopDelivery b = new ShopDelivery();
            b.setPlayerUUID("p2");
            storage.addDelivery(a);
            storage.addDelivery(b);

            assertEquals(1, storage.getDeliveriesForPlayer("p1").size());
            assertEquals(1, storage.getDeliveriesForPlayer("p2").size());
            assertEquals(2, storage.getPendingDeliveryCount());
        }
    }

    // ─── 容器注册表 ──────────────────────────────────────────

    @Nested
    @DisplayName("容器自动发现注册表")
    class ContainerRegistryTest {

        @Test
        @DisplayName("登记、按主人查询与移除")
        void registryLifecycle() {
            ShopStorage storage = newStorage();
            assertTrue(storage.addContainer("world:1:1:1", "owner-1"));
            assertFalse(storage.addContainer("world:1:1:1", "owner-2"), "重复登记返回 false");
            assertEquals("owner-1", storage.getContainerOwner("world:1:1:1"));

            Map<String, String> owned = storage.getContainersByOwner("owner-1");
            assertEquals(1, owned.size());

            storage.removeContainer("world:1:1:1");
            assertFalse(storage.hasContainer("world:1:1:1"));
        }

        @Test
        @DisplayName("getAllContainers 返回快照不被外部修改影响")
        void snapshotIsolation() {
            ShopStorage storage = newStorage();
            storage.addContainer("world:1:1:1", "owner-1");
            storage.getAllContainers().clear();
            assertTrue(storage.hasContainer("world:1:1:1"), "外部修改快照不应影响内部数据");
        }
    }
}