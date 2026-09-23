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
package com.hotels.listener;

import com.hotels.HotelsPlugin;
import com.hotels.model.Shop;
import com.hotels.shop.ShopService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 店面容器监听器
 * <ul>
 *   <li>静默记置放容器：玩家放置商店容器时登记到注册表（不消费事件，避免与其他插件冲突）</li>
 *   <li>破坏保护：已绑定店面的容器禁止拆除；普通容器被拆时清理登记记录</li>
 *   <li>上线补发：玩家上线时自动发放待领取的店铺购买物品</li>
 * </ul>
 */
public class ShopContainerListener implements Listener {

    private final HotelsPlugin plugin;

    public ShopContainerListener(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** 静默登记放置的商店容器（MONITOR 优先级，不修改事件结果） */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Material type = event.getBlockPlaced().getType();
        if (!ShopService.isContainerMaterial(type)) return;
        Block b = event.getBlockPlaced();
        String key = b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        plugin.getShopStorage().addContainer(key, player.getUniqueId().toString());
    }

    /** 破坏保护：绑定店面的容器不可拆除；普通容器拆除时清理登记 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        String world = b.getWorld().getName();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        Shop shop = plugin.getShopStorage().getShopAt(world, x, y, z);
        if (shop != null) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            if (p != null) {
                p.sendMessage("§c此容器已绑定为 Web 店面「" + shop.getDisplayNameSafe()
                        + "」，无法拆除。请先在 Web 面板中删除该店铺");
            }
            return;
        }
        String key = world + ":" + x + ":" + y + ":" + z;
        plugin.getShopStorage().removeContainer(key);
    }

    /** 上线补发待领取物品 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getShopService().claimDeliveries(event.getPlayer());
    }
}