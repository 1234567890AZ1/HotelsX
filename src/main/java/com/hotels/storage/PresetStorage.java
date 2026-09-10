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

import com.hotels.model.RoomPreset;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 房间装修预设存储（保存到 presets.yml）
 * 线程安全：写入使用 ReentrantLock 保护（适配 Folia 多线程环境）
 */
public class PresetStorage {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<String, RoomPreset> presets;
    private final ReentrantLock writeLock = new ReentrantLock();

    public PresetStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.presets = new ConcurrentHashMap<>();
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "presets.yml");
    }

    @SuppressWarnings("unchecked")
    public void loadAll() {
        presets.clear();
        if (!dataFile.exists()) {
            saveAll();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "加载装修预设失败", e);
            return;
        }
        Map<String, Object> section = config.getValues(false);
        for (Map.Entry<String, Object> e : section.entrySet()) {
            try {
                if (!(e.getValue() instanceof Map)) continue;
                RoomPreset preset = RoomPreset.deserialize((Map<String, Object>) e.getValue());
                preset.setName(e.getKey());
                presets.put(e.getKey().toLowerCase(), preset);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "读取装修预设 [" + e.getKey() + "] 出错", ex);
            }
        }
        plugin.getLogger().info("已加载 " + presets.size() + " 个装修预设");
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        for (RoomPreset preset : presets.values()) {
            config.set(preset.getName(), preset.serialize());
        }
        try {
            java.nio.file.Files.write(dataFile.toPath(),
                    config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存装修预设失败", e);
        }
    }

    /**
     * 保存/覆盖一个预设
     */
    public void savePreset(RoomPreset preset) {
        writeLock.lock();
        try {
            presets.put(preset.getName().toLowerCase(), preset);
            saveAll();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 删除预设
     */
    public boolean deletePreset(String name) {
        writeLock.lock();
        try {
            RoomPreset removed = presets.remove(name.toLowerCase());
            if (removed != null) {
                saveAll();
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 获取预设（大小写不敏感）
     */
    public RoomPreset getPreset(String name) {
        return presets.get(name.toLowerCase());
    }

    /**
     * 获取全部预设（按名称字典序）
     */
    public List<RoomPreset> getAllPresets() {
        List<RoomPreset> list = new ArrayList<>(presets.values());
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    public boolean hasPreset(String name) {
        return presets.containsKey(name.toLowerCase());
    }

    public int size() {
        return presets.size();
    }
}