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
import com.hotels.listener.ChatInputHandler;
import com.hotels.listener.ElevatorListener;
import com.hotels.listener.GUIListener;
import com.hotels.listener.RoomGuardListener;
import com.hotels.listener.RoomProtectListener;
import com.hotels.listener.SelectionListener;
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import com.hotels.selection.SelectionManager;
import com.hotels.storage.RoomStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * HotelsX - 酒店房间管理插件
 */
public class HotelsPlugin extends JavaPlugin {

    private static HotelsPlugin instance;

    private RoomStorage roomStorage;
    private SelectionManager selectionManager;
    private EconomyManager economyManager;
    private CheckinHandler checkinHandler;
    private ChatInputHandler chatInputHandler;
    private boolean debugMode;
    private boolean elevatorEnabled;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();

        // 初始化管理器
        this.roomStorage = new RoomStorage(this);
        this.selectionManager = new SelectionManager();
        this.economyManager = new EconomyManager(this);
        this.checkinHandler = new CheckinHandler(this);
        this.chatInputHandler = new ChatInputHandler(this);
        this.elevatorEnabled = getConfig().getBoolean("elevator.enabled", false);

        // 加载数据
        roomStorage.loadAll();

        // 检查超时入住（重启后恢复定时任务）
        checkOverdueCheckins();

        // 注册监听器
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
        getServer().getPluginManager().registerEvents(chatInputHandler, this);
        getServer().getPluginManager().registerEvents(new RoomGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new RoomProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new ElevatorListener(this), this);

        // 注册命令
        HotelsCommand hotelsCommand = new HotelsCommand(this);
        getCommand("hotels").setExecutor(hotelsCommand);
        getCommand("hotels").setTabCompleter(hotelsCommand);

        getLogger().info("Hotels 已启用 - 酒店房间管理系统");
        getLogger().info("已加载 " + roomStorage.getRoomCount() + " 个房间");
    }

    @Override
    public void onDisable() {
        if (roomStorage != null) {
            roomStorage.saveAll();
        }
        getLogger().info("Hotels 已禁用");
    }

    public static HotelsPlugin getInstance() {
        return instance;
    }

    public RoomStorage getRoomStorage() { return roomStorage; }
    public SelectionManager getSelectionManager() { return selectionManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public CheckinHandler getCheckinHandler() { return checkinHandler; }
    public ChatInputHandler getChatInputHandler() { return chatInputHandler; }

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
     * 检查所有已入住的房间，如果超时则自动退房，未超时则重新注册定时任务
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
                    guest.sendMessage("§c入住时间已到，自动退房");
                    checkinHandler.checkout(guest);
                } else {
                    checkinHandler.forceCheckout(room.getCurrentGuest());
                }
                expired++;
            } else {
                // 未超时，重新注册定时任务
                long remaining = expireTime - now;
                long remainingTicks = remaining / 50L;
                if (remainingTicks > 0) {
                    Player finalGuest = Bukkit.getPlayer(room.getCurrentGuest());
                    if (finalGuest != null) {
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            HotelRoom current = roomStorage.getRoom(room.getId());
                            if (current != null && current.isOccupied()
                                    && current.getCurrentGuest() != null
                                    && current.getCurrentGuest().equals(finalGuest.getUniqueId())) {
                                if (finalGuest.isOnline()) {
                                    finalGuest.sendMessage("§c入住时间已到，自动退房");
                                }
                                checkinHandler.checkout(finalGuest);
                            }
                        }, remainingTicks);
                    }
                }
            }
        }

        if (checked > 0) {
            getLogger().info("检查 " + checked + " 个入住房间，已自动退房 " + expired + " 个");
        }
    }
}
