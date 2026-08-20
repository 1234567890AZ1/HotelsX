package com.hotels.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Folia 兼容工具类 — 运行时检测服务端类型，自动选择正确的调度器 API。
 * 一份代码同时兼容 Paper / Spigot / Purpur / Folia。
 */
public class SchedulerCompat {

    private static final boolean FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        FOLIA = folia;
    }

    /** 检测当前是否为 Folia 服务端 */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * 全局调度器 — 执行与特定区域无关的任务（如定时扫描所有房间）。
     * Paper: Bukkit.getScheduler().runTask()
     * Folia: Bukkit.getGlobalRegionScheduler().run()
     */
    public static void runTask(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 全局调度器 — 延迟执行。
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * 全局调度器 — 定时循环执行。
     * @return 可取消的任务句柄
     */
    public static CancellableTask runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            var c = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
            return c::cancel;
        } else {
            var b = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return () -> Bukkit.getScheduler().cancelTask(b.getTaskId());
        }
    }

    /**
     * 区域调度器 — 在指定位置的区域线程上执行任务。
     * 用于 teleport、block 操作、entity 操作等必须在所属区域执行的操作。
     * Paper: 直接在当前线程执行（主线程）
     * Folia: RegionScheduler.run()
     */
    public static void runOnRegion(Plugin plugin, Location loc, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, loc, t -> task.run());
        } else {
            // Paper 主线程：直接执行
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    /**
     * 实体调度器 — 在指定实体所在的区域线程上执行任务。
     * Paper: 直接在当前线程执行（主线程）
     * Folia: EntityScheduler.run()
     */
    public static void runOnEntity(Entity entity, Plugin plugin, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    /**
     * 跨平台实体传送。
     * Folia: 必须使用 teleportAsync（区域内禁止同步 teleport）
     * Paper/Spigot: 直接使用同步 teleport
     */
    public static void teleport(org.bukkit.entity.Player player, org.bukkit.Location loc) {
        if (FOLIA) {
            player.teleportAsync(loc);
        } else {
            player.teleport(loc);
        }
    }

    /** 跨平台可取消的任务句柄 */
    public interface CancellableTask {
        void cancel();
    }
}