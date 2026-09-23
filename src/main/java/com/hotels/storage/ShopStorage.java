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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 店面数据存储（存到 plugins/HotelsX/shops.yml，UTF-8 编码）
 * <p>
 * 使用内存缓存 + 全量落盘模式（与 RoomStorage 一致）。
 * 维护三个数据区：
 * <ul>
 *   <li>shops：店面主数据，locationIndex 保证「一个容器只能对应一个店面」</li>
 *   <li>deliveries：待领取交付队列（买家离线/背包满）</li>
 *   <li>containers：容器自动发现注册表（locationKey -> ownerUUID），供 Web 面板创建店铺时选择</li>
 * </ul>
 */
public class ShopStorage {

    private final java.util.logging.Logger logger;
    private final File dataFile;
    private final Map<String, Shop> shops;              // id -> shop
    private final Map<String, Shop> locationIndex;      // world:x:y:z -> shop
    private final Map<String, ShopDelivery> deliveries; // id -> delivery
    private final Map<String, String> containers;       // world:x:y:z -> ownerUUID
    private final ReentrantLock writeLock = new ReentrantLock();

    public ShopStorage(JavaPlugin plugin) {
        this(plugin.getDataFolder(), plugin.getLogger());
    }

    /** 测试友好构造器：直接指定数据目录与日志，不依赖插件运行时 */
    public ShopStorage(File dataDir, java.util.logging.Logger logger) {
        this.logger = logger;
        this.shops = new ConcurrentHashMap<>();
        this.locationIndex = new ConcurrentHashMap<>();
        this.deliveries = new ConcurrentHashMap<>();
        this.containers = new ConcurrentHashMap<>();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.dataFile = new File(dataDir, "shops.yml");
    }

    /** 从磁盘加载全部数据 */
    @SuppressWarnings("unchecked")
    public void loadAll() {
        shops.clear();
        locationIndex.clear();
        deliveries.clear();
        containers.clear();

        if (!dataFile.exists()) {
            saveAll();
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "加载店面数据失败", e);
            return;
        }

        // 店面
        List<Map<String, Object>> shopList = (List<Map<String, Object>>) config.getList("shops", new ArrayList<>());
        for (Map<String, Object> m : shopList) {
            try {
                Shop shop = Shop.deserialize(m);
                shops.put(shop.getId(), shop);
                locationIndex.put(shop.getLocationKey(), shop);
            } catch (Exception e) {
                logger.log(Level.WARNING, "店面数据解析失败，已跳过", e);
            }
        }

        // 待领取交付队列
        List<Map<String, Object>> delList = (List<Map<String, Object>>) config.getList("deliveries", new ArrayList<>());
        for (Map<String, Object> m : delList) {
            try {
                ShopDelivery d = ShopDelivery.deserialize(m);
                deliveries.put(d.getId(), d);
            } catch (Exception e) {
                logger.log(Level.WARNING, "待领取交付数据解析失败，已跳过", e);
            }
        }

        // 容器注册表
        List<Map<String, Object>> conList = (List<Map<String, Object>>) config.getList("containers", new ArrayList<>());
        for (Map<String, Object> m : conList) {
            try {
                String loc = (String) m.get("location");
                String owner = (String) m.get("owner");
                if (loc != null && owner != null) {
                    containers.put(loc, owner);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "容器注册数据解析失败，已跳过", e);
            }
        }

        logger.info("已加载 " + shops.size() + " 个店面, "
                + deliveries.size() + " 条待领取, " + containers.size() + " 个已登记容器");
    }

    /** 全量保存到磁盘（UTF-8） */
    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();

        List<Map<String, Object>> shopList = new ArrayList<>();
        for (Shop shop : shops.values()) {
            shopList.add(shop.serialize());
        }
        config.set("shops", shopList);

        List<Map<String, Object>> delList = new ArrayList<>();
        for (ShopDelivery d : deliveries.values()) {
            delList.add(d.serialize());
        }
        config.set("deliveries", delList);

        List<Map<String, Object>> conList = new ArrayList<>();
        for (Map.Entry<String, String> e : containers.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("location", e.getKey());
            m.put("owner", e.getValue());
            conList.add(m);
        }
        config.set("containers", conList);

        try {
            java.nio.file.Files.write(dataFile.toPath(),
                    config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "保存店面数据失败", e);
        }
    }

    // ===== 店面 =====

    /**
     * 新增店面。同一容器已被占用时返回 false，不覆盖。
     */
    public boolean createShop(Shop shop) {
        writeLock.lock();
        try {
            if (locationIndex.containsKey(shop.getLocationKey())) {
                return false;
            }
            shops.put(shop.getId(), shop);
            locationIndex.put(shop.getLocationKey(), shop);
            saveAll();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /** 更新店面配置 */
    public void saveShop(Shop shop) {
        writeLock.lock();
        try {
            shops.put(shop.getId(), shop);
            locationIndex.put(shop.getLocationKey(), shop);
            saveAll();
        } finally {
            writeLock.unlock();
        }
    }

    /** 删除店面 */
    public void removeShop(String id) {
        writeLock.lock();
        try {
            Shop shop = shops.remove(id);
            if (shop != null) {
                locationIndex.remove(shop.getLocationKey());
                saveAll();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public Shop getShop(String id) {
        return shops.get(id);
    }

    /** 按容器位置查找店面 */
    public Shop getShopAt(String world, int x, int y, int z) {
        return locationIndex.get(world + ":" + x + ":" + y + ":" + z);
    }

    public Collection<Shop> getAllShops() {
        return shops.values();
    }

    public int getShopCount() {
        return shops.size();
    }

    /** 按店主查询（用于占用数上限校验） */
    public List<Shop> getShopsByOwner(String ownerUUID) {
        List<Shop> result = new ArrayList<>();
        for (Shop shop : shops.values()) {
            if (ownerUUID.equalsIgnoreCase(shop.getOwnerUUID())) {
                result.add(shop);
            }
        }
        return result;
    }

    // ===== 容器注册表 =====

    /** 登记一个容器（容器自动发现）。已登记则返回 false。 */
    public boolean addContainer(String locationKey, String ownerUUID) {
        writeLock.lock();
        try {
            String old = containers.putIfAbsent(locationKey, ownerUUID);
            if (old != null) {
                return false;
            }
            saveAll();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /** 移除容器登记（容器被破坏时调用） */
    public void removeContainer(String locationKey) {
        writeLock.lock();
        try {
            if (containers.remove(locationKey) != null) {
                saveAll();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public String getContainerOwner(String locationKey) {
        return containers.get(locationKey);
    }

    public boolean hasContainer(String locationKey) {
        return containers.containsKey(locationKey);
    }

    /** 全部容器（locationKey -> ownerUUID 快照） */
    public Map<String, String> getAllContainers() {
        return new LinkedHashMap<>(containers);
    }

    /** 某个玩家的容器（locationKey -> ownerUUID 快照） */
    public Map<String, String> getContainersByOwner(String ownerUUID) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : containers.entrySet()) {
            if (e.getValue() != null && e.getValue().equalsIgnoreCase(ownerUUID)) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    // ===== 待领取交付队列 =====

    public void addDelivery(ShopDelivery d) {
        writeLock.lock();
        try {
            deliveries.put(d.getId(), d);
            saveAll();
        } finally {
            writeLock.unlock();
        }
    }

    public void updateDelivery(ShopDelivery d) {
        writeLock.lock();
        try {
            deliveries.put(d.getId(), d);
            saveAll();
        } finally {
            writeLock.unlock();
        }
    }

    public void removeDelivery(String id) {
        writeLock.lock();
        try {
            if (deliveries.remove(id) != null) {
                saveAll();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public ShopDelivery getDelivery(String id) {
        return deliveries.get(id);
    }

    /** 某玩家的所有未领取交付 */
    public List<ShopDelivery> getDeliveriesForPlayer(String playerUUID) {
        List<ShopDelivery> result = new ArrayList<>();
        for (ShopDelivery d : deliveries.values()) {
            if (playerUUID.equalsIgnoreCase(d.getPlayerUUID())) {
                result.add(d);
            }
        }
        return result;
    }

    /** 全部未领取交付（供 Web 面板展示） */
    public Collection<ShopDelivery> getAllDeliveries() {
        return deliveries.values();
    }

    public int getPendingDeliveryCount() {
        return deliveries.size();
    }
}