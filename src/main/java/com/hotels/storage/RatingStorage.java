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

import com.hotels.model.Rating;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 房间评分存储（保存到 ratings.yml）
 */
public class RatingStorage {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Deque<Rating> ratings;
    private final ReentrantLock writeLock = new ReentrantLock();
    private static final int MAX_RATINGS = 2000; // 最多保留2000条

    public RatingStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.ratings = new ConcurrentLinkedDeque<>();
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "ratings.yml");
    }

    @SuppressWarnings("unchecked")
    public void loadAll() {
        ratings.clear();
        if (!dataFile.exists()) {
            saveAll();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "加载评分记录失败", e);
            return;
        }
        List<Map<?, ?>> list = config.getMapList("ratings");
        for (Map<?, ?> raw : list) {
            try {
                Rating r = Rating.deserialize((Map<String, Object>) raw);
                ratings.addLast(r);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "读取评分记录出错", e);
            }
        }
        plugin.getLogger().info("已加载 " + ratings.size() + " 条评分记录");
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Rating r : ratings) {
            list.add(r.serialize());
        }
        config.set("ratings", list);
        try {
            java.nio.file.Files.write(dataFile.toPath(),
                    config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存评分记录失败", e);
        }
    }

    /**
     * 添加评分（同玩家同房间会覆盖旧评分）
     */
    public void add(Rating r) {
        writeLock.lock();
        try {
            ratings.removeIf(x -> x.getRoomId() != null && x.getRoomId().equals(r.getRoomId())
                    && x.getGuestUuid() != null && x.getGuestUuid().equals(r.getGuestUuid()));
            ratings.addLast(r);
            while (ratings.size() > MAX_RATINGS) {
                ratings.removeFirst();
            }
            saveAll();
        } finally { writeLock.unlock(); }
    }

    /**
     * 获取全部评分（从新到旧）
     */
    public List<Rating> getAll() {
        List<Rating> list = new ArrayList<>(ratings);
        Collections.reverse(list);
        return list;
    }

    /**
     * 获取指定房间的评分（从新到旧）
     */
    public List<Rating> getByRoom(String roomId) {
        List<Rating> list = new ArrayList<>();
        for (Rating r : ratings) {
            if (roomId != null && roomId.equals(r.getRoomId())) {
                list.add(r);
            }
        }
        Collections.reverse(list);
        return list;
    }

    /**
     * 玩家是否已给该房间评分
     */
    public boolean hasRated(String roomId, String guestUuid) {
        for (Rating r : ratings) {
            if (roomId != null && roomId.equals(r.getRoomId())
                    && guestUuid != null && guestUuid.equals(r.getGuestUuid())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取房间平均分（0 表示无评分）
     */
    public double getAverage(String roomId) {
        List<Rating> list = getByRoom(roomId);
        if (list.isEmpty()) return 0;
        double sum = 0;
        for (Rating r : list) {
            sum += r.getScore();
        }
        return Math.round((sum / list.size()) * 10) / 10.0;
    }

    /**
     * 获取房间评分数
     */
    public int countByRoom(String roomId) {
        return getByRoom(roomId).size();
    }

    public int size() {
        return ratings.size();
    }
}
