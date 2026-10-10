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
package com.hotels;

import com.hotels.command.HotelsCommand;
import com.hotels.i18n.Lang;
import com.hotels.listener.ChatInputHandler;
import com.hotels.listener.DoorGuardListener;
import com.hotels.listener.ElevatorListener;
import com.hotels.listener.GUIListener;
import com.hotels.listener.RoomGuardListener;
import com.hotels.listener.RoomProtectListener;
import com.hotels.listener.SelectionListener;
import com.hotels.listener.ShopContainerListener;
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import com.hotels.preset.PresetManager;
import com.hotels.selection.SelectionManager;
import com.hotels.shop.ShopService;
import com.hotels.storage.EscrowStorage;
import com.hotels.storage.PresetStorage;
import com.hotels.storage.RatingStorage;
import com.hotels.storage.RoomStorage;
import com.hotels.storage.ShopStorage;
import com.hotels.storage.TransactionStorage;
import com.hotels.util.SchedulerCompat;
import com.hotels.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * HotelsX - 酒店房间管理插件
 */
public class HotelsPlugin extends JavaPlugin {

    private static HotelsPlugin instance;

    private RoomStorage roomStorage;
    private TransactionStorage transactionStorage;
    private RatingStorage ratingStorage;
    private EscrowStorage escrowStorage;
    private PresetStorage presetStorage;
    private ShopStorage shopStorage;
    private PresetManager presetManager;
    private SelectionManager selectionManager;
    private EconomyManager economyManager;
    private CheckinHandler checkinHandler;
    private ChatInputHandler chatInputHandler;
    private WebServer webServer;
    private ShopService shopService;
    private ShopContainerListener shopContainerListener;
    private Lang lang;
    private boolean debugMode;
    private boolean elevatorEnabled;
    private SchedulerCompat.CancellableTask autoCheckoutTask;
    private SchedulerCompat.CancellableTask reminderTask;

    // 已发送过临期警告的房间（roomId -> true），避免重复刷屏；剩余时间恢复后自动重置
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> alertedRooms = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化语言包：必须早于其它管理器，因为它们的构造可能就要用到文案
        this.lang = new Lang(this);
        this.lang.load();

        // 初始化管理器
        this.roomStorage = new RoomStorage(this);
        this.transactionStorage = new TransactionStorage(this);
        this.ratingStorage = new RatingStorage(this);
        this.escrowStorage = new EscrowStorage(this);
        this.presetStorage = new PresetStorage(this);
        this.shopStorage = new ShopStorage(this);
        this.presetManager = new PresetManager(this);
        this.selectionManager = new SelectionManager();
        this.economyManager = new EconomyManager(this);
        this.checkinHandler = new CheckinHandler(this);
        this.chatInputHandler = new ChatInputHandler(this);
        this.shopService = new ShopService(this);
        this.shopContainerListener = new ShopContainerListener(this);
        this.elevatorEnabled = getConfig().getBoolean("elevator.enabled", false);

        // 加载数据
        roomStorage.loadAll();
        transactionStorage.loadAll();
        ratingStorage.loadAll();
        escrowStorage.loadAll();
        presetStorage.loadAll();
        shopStorage.loadAll();

        // 检查超时入住（重启后恢复定时任务）
        checkOverdueCheckins();

        // 全局周期任务：每 30 秒扫描一次已入住房间，到期自动退房（重启后依然有效）
        autoCheckoutTask = SchedulerCompat.runTaskTimer(this, this::checkOverdueCheckins, 600L, 600L);

        // 全局周期任务：每 5 秒推送入住倒计时 ActionBar，并在临期时发送提醒
        reminderTask = SchedulerCompat.runTaskTimer(this, this::sendCheckinReminders, 100L, 100L);

        // 注册监听器
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
        getServer().getPluginManager().registerEvents(chatInputHandler, this);
        getServer().getPluginManager().registerEvents(new RoomGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new RoomProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new ElevatorListener(this), this);
        getServer().getPluginManager().registerEvents(new DoorGuardListener(this), this);
        // 店面容器自动发现 + 上线补发（仅当店面系统启用时注册）
        if (getConfig().getBoolean("shop.enabled", true)) {
            getServer().getPluginManager().registerEvents(shopContainerListener, this);
        }

        // 注册命令
        HotelsCommand hotelsCommand = new HotelsCommand(this);
        getCommand("hotels").setExecutor(hotelsCommand);
        getCommand("hotels").setTabCompleter(hotelsCommand);

        // 启动 Web 面板
        this.webServer = new WebServer(this);
        webServer.start();

        getLogger().info("Hotels 已启用 - 酒店房间管理系统");
        getLogger().info("已加载 " + roomStorage.getRoomCount() + " 个房间");
    }

    @Override
    public void onDisable() {
        // 取消自动退房周期任务
        if (autoCheckoutTask != null) {
            autoCheckoutTask.cancel();
            autoCheckoutTask = null;
        }
        // 取消倒计时提醒周期任务
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
        if (webServer != null) {
            webServer.stop();
        }
        if (roomStorage != null) {
            roomStorage.saveAll();
        }
        if (transactionStorage != null) {
            transactionStorage.saveAll();
        }
        if (ratingStorage != null) {
            ratingStorage.saveAll();
        }
        if (escrowStorage != null) {
            escrowStorage.saveAll();
        }
        if (shopStorage != null) {
            shopStorage.saveAll();
        }
        getLogger().info("Hotels 已禁用");
    }

    public static HotelsPlugin getInstance() {
        return instance;
    }

    public RoomStorage getRoomStorage() { return roomStorage; }
    public TransactionStorage getTransactionStorage() { return transactionStorage; }
    public RatingStorage getRatingStorage() { return ratingStorage; }
    public EscrowStorage getEscrowStorage() { return escrowStorage; }
    public PresetStorage getPresetStorage() { return presetStorage; }
    public ShopStorage getShopStorage() { return shopStorage; }
    public ShopService getShopService() { return shopService; }
    public PresetManager getPresetManager() { return presetManager; }
    public SelectionManager getSelectionManager() { return selectionManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public CheckinHandler getCheckinHandler() { return checkinHandler; }
    public ChatInputHandler getChatInputHandler() { return chatInputHandler; }
    public WebServer getWebServer() { return webServer; }
    public Lang getLang() { return lang; }

    public boolean isDebugMode() { return debugMode; }

    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public void debug(Player player, String message) {
        if (debugMode) {
            String prefix = player != null ? "[" + player.getName() + "] " : "";
            getLogger().info("[DEBUG] " + prefix + message);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) {
                    p.sendMessage("§d[DEBUG] §7" + prefix + message);
                }
            }
        }
    }

    public void debug(String message) {
        debug(null, message);
    }

    public void log(Player player, String message) {
        if (debugMode) {
            String prefix = player != null ? "[" + player.getName() + "] " : "";
            getLogger().info("[DEBUG] " + prefix + message);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) {
                    p.sendMessage("§d[DEBUG] §7" + prefix + message);
                }
            }
        }
    }

    public void log(String message) {
        log(null, message);
    }

    public boolean isElevatorEnabled() {
        return elevatorEnabled;
    }

    public boolean isElevatorEnabledFor(Player player) {
        return elevatorEnabled;
    }

    public void setElevatorEnabled(boolean enabled) {
        this.elevatorEnabled = enabled;
        getConfig().set("elevator.enabled", enabled);
        saveConfig();
    }

    /**
     * 扫描所有已入住的房间，超时的自动退房
     * 由全局周期任务每 30 秒调用一次，无需注册单次定时任务
     */
    private void checkOverdueCheckins() {
        long now = System.currentTimeMillis();
        int checked = 0;
        int expired = 0;

        for (HotelRoom room : roomStorage.getAllRooms()) {
            if (!room.isOccupied() || room.getCurrentGuest() == null) continue;
            checked++;

            // 获取实际时长
            int duration = room.getDurationMinutes();
            if (duration == -1) {
                // 从合集获取
                for (RoomCollection col : roomStorage.getAllCollections()) {
                    if (col.getRoomIds().contains(room.getId())) {
                        duration = col.getDurationMinutes();
                        break;
                    }
                }
                if (duration == -1) duration = 0;
            }

            if (duration <= 0) continue; // 不限时

            long checkinTime = room.getCheckinTime();
            long expireTime = checkinTime + (duration * 60 * 1000L);

            if (now >= expireTime) {
                Player guest = Bukkit.getPlayer(room.getCurrentGuest());
                if (guest != null && guest.isOnline()) {
                    lang.send(guest, "checkin.expired");
                    checkinHandler.checkout(guest);
                } else {
                    checkinHandler.forceCheckout(room.getCurrentGuest());
                }
                expired++;
            }
        }

        // 仅在真的有房间到期退房时才打印，避免每 30 秒刷屏
        if (expired > 0) {
            getLogger().info("检查 " + checked + " 个入住房间，已自动退房 " + expired + " 个");
        }
    }

    /**
     * 入住倒计时提醒：每 5 秒推送 ActionBar 剩余时间，临期（≤60秒）发送一次性续费警告
     */
    private void sendCheckinReminders() {
        long now = System.currentTimeMillis();
        for (HotelRoom room : roomStorage.getAllRooms()) {
            if (!room.isOccupied() || room.getCurrentGuest() == null) continue;

            int duration = room.getDurationMinutes();
            if (duration == -1) {
                for (RoomCollection col : roomStorage.getAllCollections()) {
                    if (col.getRoomIds().contains(room.getId())) {
                        duration = col.getDurationMinutes();
                        break;
                    }
                }
                if (duration == -1) duration = 0;
            }
            if (duration <= 0) continue; // 不限时

            Player guest = Bukkit.getPlayer(room.getCurrentGuest());
            if (guest == null || !guest.isOnline()) continue;

            long expireTime = room.getCheckinTime() + (duration * 60 * 1000L);
            long remainMs = expireTime - now;
            if (remainMs <= 0) continue; // 已到期，交给自动退房处理

            long remainSec = remainMs / 1000;
            boolean alerted = alertedRooms.getOrDefault(room.getId(), false);
            if (remainSec > 60) {
                alertedRooms.put(room.getId(), false);
            } else if (!alerted) {
                alertedRooms.put(room.getId(), true);
                lang.send(guest, "plugin.reminder.expiring",
                        "room", room.getName(), "time", formatRemain(remainSec));
                lang.send(guest, "plugin.reminder.extend_hint", "id", room.getId());
            }
            String color = remainSec <= 300 ? "§c" : "§f";
            guest.sendActionBar(lang.get("plugin.actionbar.remaining",
                    "room", room.getName(), "time", color + formatRemain(remainSec)));
        }
    }

    /**
     * 格式化剩余时间（秒）
     */
    private String formatRemain(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return lang.get("common.time.hm", "h", h, "m", m);
        if (m > 0) return lang.get("common.time.ms", "m", m, "s", s);
        return lang.get("common.time.s", "s", s);
    }

    /**
     * 获取已发送临期警告的房间集合（供续费后重置）
     */
    public java.util.concurrent.ConcurrentHashMap<String, Boolean> getAlertedRooms() {
        return alertedRooms;
    }
}
