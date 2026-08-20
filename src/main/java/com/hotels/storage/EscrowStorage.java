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

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * 收益托管存储（保存到 escrow.yml）
 * 托管模式下，房主入住收益进入待提现余额，通过 Web 面板 / /ht claim 提现到钱包
 */
public class EscrowStorage {

    /**
     * 待提现余额信息
     */
    public static class PendingInfo {
        public final String uuid;
        public final String name;
        public final double pending;
        public final double totalWithdrawn;
        public final double totalEarned;

        public PendingInfo(String uuid, String name, double pending, double totalWithdrawn, double totalEarned) {
            this.uuid = uuid;
            this.name = name;
            this.pending = pending;
            this.totalWithdrawn = totalWithdrawn;
            this.totalEarned = totalEarned;
        }
    }

    /**
     * 提现记录
     */
    public static class Withdrawal {
        public String id;
        public String uuid;
        public String name;
        public double amount;
        public long time;

        public Withdrawal() {
            this.id = UUID.randomUUID().toString().substring(0, 12);
            this.time = System.currentTimeMillis();
        }

        public Map<String, Object> serialize() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("uuid", uuid);
            map.put("name", name != null ? name : "");
            map.put("amount", amount);
            map.put("time", time);
            return map;
        }

        public static Withdrawal deserialize(Map<String, Object> map) {
            Withdrawal w = new Withdrawal();
            w.id = (String) map.get("id");
            w.uuid = (String) map.get("uuid");
            w.name = (String) map.get("name");
            w.amount = ((Number) map.get("amount")).doubleValue();
            w.time = ((Number) map.get("time")).longValue();
            return w;
        }
    }

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<String, Double> pendingBalances;   // uuid -> 待提现余额
    private final Map<String, String> balanceNames;      // uuid -> 玩家名
    private final Deque<Withdrawal> withdrawals;
    private final ReentrantLock writeLock = new ReentrantLock();
    private static final int MAX_WITHDRAWALS = 2000;

    public EscrowStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pendingBalances = new ConcurrentHashMap<>();
        this.balanceNames = new ConcurrentHashMap<>();
        this.withdrawals = new ConcurrentLinkedDeque<>();
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dataFile = new File(dataDir, "escrow.yml");
    }

    public void loadAll() {
        pendingBalances.clear();
        balanceNames.clear();
        withdrawals.clear();
        if (!dataFile.exists()) {
            saveAll();
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new java.io.FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "加载收益托管数据失败", e);
            return;
        }
        ConfigurationSection bal = config.getConfigurationSection("balances");
        if (bal != null) {
            for (String key : bal.getKeys(false)) {
                try {
                    double pending = bal.getDouble(key + ".pending", 0);
                    String name = bal.getString(key + ".name", "");
                    if (pending > 0) {
                        pendingBalances.put(key, pending);
                        balanceNames.put(key, name);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "读取托管余额出错", e);
                }
            }
        }
        List<Map<?, ?>> list = config.getMapList("withdrawals");
        for (Map<?, ?> raw : list) {
            try {
                withdrawals.addLast(Withdrawal.deserialize((Map<String, Object>) raw));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "读取提现记录出错", e);
            }
        }
        plugin.getLogger().info("已加载 " + pendingBalances.size() + " 个待提现账户, " + withdrawals.size() + " 条提现记录");
    }

    public void saveAll() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Double> e : pendingBalances.entrySet()) {
            String name = balanceNames.get(e.getKey());
            config.set("balances." + e.getKey() + ".name", name != null ? name : "");
            config.set("balances." + e.getKey() + ".pending", e.getValue());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Withdrawal w : withdrawals) {
            list.add(w.serialize());
        }
        config.set("withdrawals", list);
        try {
            java.nio.file.Files.write(dataFile.toPath(),
                    config.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存收益托管数据失败", e);
        }
    }

    /**
     * 存入待提现余额（托管模式入住收益）
     */
    public void deposit(String uuid, String name, double amount) {
        if (uuid == null || amount <= 0) return;
        writeLock.lock();
        try {
            pendingBalances.merge(uuid, amount, Double::sum);
            if (name != null) balanceNames.put(uuid, name);
            saveAll();
        } finally { writeLock.unlock(); }
    }

    /**
     * 获取玩家待提现余额
     */
    public double getPending(String uuid) {
        return pendingBalances.getOrDefault(uuid, 0.0);
    }

    /**
     * 获取全部待提现账户列表
     */
    public List<PendingInfo> getPendingList() {
        List<PendingInfo> list = new ArrayList<>();
        for (Map.Entry<String, Double> e : pendingBalances.entrySet()) {
            String uuid = e.getKey();
            list.add(new PendingInfo(uuid, balanceNames.getOrDefault(uuid, ""),
                    e.getValue(), getTotalWithdrawn(uuid), e.getValue() + getTotalWithdrawn(uuid)));
        }
        list.sort((a, b) -> Double.compare(b.pending, a.pending));
        return list;
    }

    /**
     * 获取玩家累计已提现
     */
    public double getTotalWithdrawn(String uuid) {
        double sum = 0;
        for (Withdrawal w : withdrawals) {
            if (uuid.equals(w.uuid)) sum += w.amount;
        }
        return sum;
    }

    /**
     * 获取提现记录（从新到旧）
     */
    public List<Withdrawal> getWithdrawals() {
        List<Withdrawal> list = new ArrayList<>(withdrawals);
        Collections.reverse(list);
        return list;
    }

    /**
     * 完成提现：清零待提现余额并记录提现
     * @return true 表示成功（有待提现金额）
     */
    public boolean completeWithdrawal(String uuid, String name, double amount) {
        writeLock.lock();
        try {
            Double pending = pendingBalances.get(uuid);
            if (pending == null || pending <= 0) return false;
            double toPay = Math.min(pending, amount);
            if (toPay <= 0) return false;
            pendingBalances.put(uuid, pending - toPay);
            if (pendingBalances.get(uuid) <= 0.001) {
                pendingBalances.remove(uuid);
                balanceNames.remove(uuid);
            }
            Withdrawal w = new Withdrawal();
            w.uuid = uuid;
            w.name = name != null ? name : "";
            w.amount = toPay;
            withdrawals.addLast(w);
            while (withdrawals.size() > MAX_WITHDRAWALS) {
                withdrawals.removeFirst();
            }
            saveAll();
            return true;
        } finally { writeLock.unlock(); }
    }

    public int pendingCount() {
        return pendingBalances.size();
    }
}
