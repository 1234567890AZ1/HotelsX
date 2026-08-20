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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hotels.storage;

import com.hotels.model.Transaction;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 经济流水存储（保存到 transactions.yml）
 */
public class TransactionStorage {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Deque<Transaction> transactions;
    private final ReentrantLock writeLock = new ReentrantLock();
    private static final int MAX_TRANSACTIONS = 5000; // 最多保留5000条

    public TransactionStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.transactions = new ConcurrentLinkedDeque<>();
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "transactions.yml");
    }

    @SuppressWarnings("unchecked")
    public void loadAll() {
        transactions.clear();
        if (!dataFile.exists()) {
            saveAll();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "加载交易记录失败", e);
            return;
        }
        List<Map<?, ?>> list = config.getMapList("transactions");
        // 按时间正序存储（List中是从旧到新），Deque插入到尾
        for (Map<?, ?> raw : list) {
            try {
                Transaction tx = Transaction.deserialize((Map<String, Object>) raw);
                transactions.addLast(tx);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "读取交易记录出错", e);
            }
        }
        plugin.getLogger().info("已加载 " + transactions.size() + " 条交易记录");
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Transaction tx : transactions) {
            list.add(tx.serialize());
        }
        config.set("transactions", list);
        try {
            java.nio.file.Files.write(dataFile.toPath(),
                    config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存交易记录失败", e);
        }
    }

    public void add(Transaction tx) {
        writeLock.lock();
        try {
            transactions.addLast(tx);
            // 超过上限就移除最旧的
            while (transactions.size() > MAX_TRANSACTIONS) {
                transactions.removeFirst();
            }
            saveAll();
        } finally { writeLock.unlock(); }
    }

    /**
     * 获取全部交易记录（从新到旧）
     */
    public List<Transaction> getAll() {
        List<Transaction> list = new ArrayList<>(transactions);
        Collections.reverse(list);
        return list;
    }

    /**
     * 获取指定玩家的交易记录（从新到旧）
     */
    public List<Transaction> getByPlayer(UUID playerUUID) {
        String uuidStr = playerUUID.toString();
        List<Transaction> list = new ArrayList<>();
        for (Transaction tx : transactions) {
            if (uuidStr.equals(tx.getPlayerUUID())) {
                list.add(tx);
            }
        }
        Collections.reverse(list);
        return list;
    }

    /**
     * 获取指定房间ID的交易记录（从新到旧）
     */
    public List<Transaction> getByRoom(String roomId) {
        List<Transaction> list = new ArrayList<>();
        for (Transaction tx : transactions) {
            if (roomId != null && roomId.equals(tx.getRoomId())) {
                list.add(tx);
            }
        }
        Collections.reverse(list);
        return list;
    }

    public int size() {
        return transactions.size();
    }
}