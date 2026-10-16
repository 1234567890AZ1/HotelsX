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

import com.hotels.booking.Booking;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 预订存储（独立 bookings.yml，避免撑大 rooms.yml）
 */
public class BookingStorage {

    private static final long TERMINAL_KEEP_MS = 3L * 24 * 60 * 60 * 1000; // 终态记录保留 3 天
    private static final int MAX_BOOKINGS = 500;

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Deque<Booking> bookings;
    private final ReentrantLock writeLock = new ReentrantLock();

    public BookingStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.bookings = new ConcurrentLinkedDeque<>();
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "bookings.yml");
    }

    @SuppressWarnings("unchecked")
    public void loadAll() {
        bookings.clear();
        if (!dataFile.exists()) {
            saveAll();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "加载预订记录失败", e);
            return;
        }
        List<Map<?, ?>> list = config.getMapList("bookings");
        for (Map<?, ?> raw : list) {
            try {
                bookings.addLast(Booking.deserialize((Map<String, Object>) raw));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "读取预订记录出错", e);
            }
        }
        plugin.getLogger().info("已加载 " + bookings.size() + " 条预订记录");
    }

    public void saveAll() {
        prune();
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Booking b : bookings) {
            list.add(b.serialize());
        }
        config.set("bookings", list);
        try {
            java.nio.file.Files.write(dataFile.toPath(),
                    config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存预订记录失败", e);
        }
    }

    /** 清理过期的终态记录，避免文件无限增长 */
    private void prune() {
        long now = System.currentTimeMillis();
        bookings.removeIf(b -> b.getStatus() != Booking.Status.PENDING
                && b.getStatus() != Booking.Status.ACTIVE
                && now - b.getCreatedTime() > TERMINAL_KEEP_MS);
        while (bookings.size() > MAX_BOOKINGS) {
            bookings.removeFirst();
        }
    }

    public void add(Booking b) {
        writeLock.lock();
        try {
            bookings.addLast(b);
            saveAll();
        } finally {
            writeLock.unlock();
        }
    }

    public void update(Booking b) {
        writeLock.lock();
        try {
            saveAll();
        } finally {
            writeLock.unlock();
        }
    }

    public Booking getById(String id) {
        if (id == null) return null;
        for (Booking b : bookings) {
            if (id.equals(b.getId())) return b;
        }
        return null;
    }

    public List<Booking> getAll() {
        return new ArrayList<>(bookings);
    }

    public List<Booking> getByGuest(String guestUuid) {
        List<Booking> list = new ArrayList<>();
        for (Booking b : bookings) {
            if (guestUuid != null && guestUuid.equals(b.getGuestUuid())) list.add(b);
        }
        return list;
    }

    public List<Booking> getByRoom(String roomId) {
        List<Booking> list = new ArrayList<>();
        for (Booking b : bookings) {
            if (roomId != null && roomId.equals(b.getRoomId())) list.add(b);
        }
        return list;
    }

    /** 是否存在未结束的预订（等待开始或进行中） */
    public boolean hasOpenBooking(String roomId) {
        for (Booking b : bookings) {
            if (roomId != null && roomId.equals(b.getRoomId())
                    && (b.getStatus() == Booking.Status.PENDING || b.getStatus() == Booking.Status.ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return bookings.size();
    }
}
