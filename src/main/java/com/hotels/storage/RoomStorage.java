/*
 * Hotels - 酒店房间管理插件
 * MIT License
 *
 * Copyright (c) 2024-2026 Hotels
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

import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 房间 & 合集数据存储（存到 plugins/Hotels/rooms.yml）
 */
public class RoomStorage {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<String, HotelRoom> rooms;
    private final Map<String, RoomCollection> collections; // roomId -> room

    public RoomStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rooms = new ConcurrentHashMap<>();
        this.collections = new ConcurrentHashMap<>();
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.dataFile = new File(dataDir, "rooms.yml");
        plugin.getLogger().info("房间数据文件路径: " + dataFile.getAbsolutePath());
    }

    /**
     * 加载所有房间数据
     */
    @SuppressWarnings("unchecked")
    public void loadAll() {
        rooms.clear();
        collections.clear();

        plugin.getLogger().info("尝试加载房间数据，文件路径: " + dataFile.getAbsolutePath());
        plugin.getLogger().info("文件是否存在: " + dataFile.exists());

        if (!dataFile.exists()) {
            plugin.getLogger().info("未找到房间数据文件，将创建新文件");
            saveAll();
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "加载房间数据文件失败", e);
            return;
        }

        // 加载房间
        if (config.contains("rooms")) {
            List<Map<?, ?>> rawList = config.getMapList("rooms");
            for (Map<?, ?> raw : rawList) {
                try {
                    Map<String, Object> map = (Map<String, Object>) raw;
                    HotelRoom room = HotelRoom.deserialize(map);
                    rooms.put(room.getId(), room);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "读取房间数据时出错", e);
                }
            }
        }

        // 加载合集
        if (config.contains("collections")) {
            List<Map<?, ?>> rawList = config.getMapList("collections");
            for (Map<?, ?> raw : rawList) {
                try {
                    Map<String, Object> map = (Map<String, Object>) raw;
                    RoomCollection col = RoomCollection.deserialize(map);
                    collections.put(col.getId(), col);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "读取合集数据时出错", e);
                }
            }
        }

        plugin.getLogger().info("已加载 " + rooms.size() + " 个房间, " + collections.size() + " 个合集");
    }

    /**
     * 保存所有房间 & 合集数据
     */
    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();

        // 保存房间
        List<Map<String, Object>> roomList = new ArrayList<>();
        for (HotelRoom room : rooms.values()) {
            roomList.add(room.serialize());
        }
        config.set("rooms", roomList);

        // 保存合集
        List<Map<String, Object>> colList = new ArrayList<>();
        for (RoomCollection col : collections.values()) {
            colList.add(col.serialize());
        }
        config.set("collections", colList);

        try {
            java.nio.file.Files.write(dataFile.toPath(), config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存数据失败", e);
        }
    }

    /**
     * 保存单个房间（全量保存，简单可靠）
     */
    public void saveRoom(HotelRoom room) {
        rooms.put(room.getId(), room);
        saveAll();
    }

    /**
     * 删除房间
     */
    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        saveAll();
    }

    /**
     * 获取所有房间
     */
    public Collection<HotelRoom> getAllRooms() {
        return rooms.values();
    }

    /**
     * 根据 ID 获取房间
     */
    public HotelRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 获取房主的所有房间
     */
    public List<HotelRoom> getRoomsByOwner(UUID ownerUUID) {
        List<HotelRoom> result = new ArrayList<>();
        for (HotelRoom room : rooms.values()) {
            if (room.getOwner().equals(ownerUUID)) {
                result.add(room);
            }
        }
        return result;
    }

    /**
     * 获取空闲房间列表
     */
    public List<HotelRoom> getAvailableRooms() {
        List<HotelRoom> result = new ArrayList<>();
        for (HotelRoom room : rooms.values()) {
            if (room.isAvailable() && !room.isLocked()) {
                result.add(room);
            }
        }
        return result;
    }

    /**
     * 检查区域是否与其他房间重叠
     */
    public boolean isOverlapping(String worldName,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  String excludeRoomId) {
        for (HotelRoom room : rooms.values()) {
            if (!room.getWorldName().equals(worldName)) continue;
            if (excludeRoomId != null && room.getId().equals(excludeRoomId)) continue;
            if (room.overlaps(x1, y1, z1, x2, y2, z2)) {
                return true;
            }
        }
        return false;
    }

    public int getRoomCount() {
        return rooms.size();
    }

    // ===== 合集操作 =====

    public Collection<RoomCollection> getAllCollections() {
        return collections.values();
    }

    public RoomCollection getCollection(String id) {
        return collections.get(id);
    }

    public List<RoomCollection> getCollectionsByOwner(UUID ownerUUID) {
        List<RoomCollection> result = new ArrayList<>();
        for (RoomCollection col : collections.values()) {
            if (col.getOwner().equals(ownerUUID)) {
                result.add(col);
            }
        }
        return result;
    }

    public void saveCollection(RoomCollection col) {
        collections.put(col.getId(), col);
        saveAll();
    }

    public void removeCollection(String id) {
        collections.remove(id);
        saveAll();
    }

    public int getCollectionCount() {
        return collections.size();
    }

    /**
     * 根据客人UUID获取其入住的房间
     */
    public HotelRoom getRoomByGuest(UUID guestUUID) {
        for (HotelRoom room : rooms.values()) {
            if (room.isOccupied() && room.getCurrentGuest() != null && room.getCurrentGuest().equals(guestUUID)) {
                return room;
            }
        }
        return null;
    }

    /**
     * 获取合集内所有房间
     */
    public List<HotelRoom> getCollectionRooms(String collectionId) {
        RoomCollection col = collections.get(collectionId);
        if (col == null) return new ArrayList<>();

        List<HotelRoom> result = new ArrayList<>();
        for (String roomId : col.getRoomIds()) {
            HotelRoom room = rooms.get(roomId);
            if (room != null) {
                result.add(room);
            }
        }
        return result;
    }
}
