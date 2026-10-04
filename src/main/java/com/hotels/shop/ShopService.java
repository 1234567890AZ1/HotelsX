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
package com.hotels.shop;

import com.hotels.HotelsPlugin;
import com.hotels.model.HotelRoom;
import com.hotels.model.Shop;
import com.hotels.model.ShopDelivery;
import com.hotels.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 店面交易核心逻辑
 * <p>
 * 纯 Web 面板驱动的商店系统：
 * <ul>
 *   <li>AUTO 模式：可售库存实时等于容器内同种物品数量，交易时直接从容器扣除</li>
 *   <li>FIXED 模式：店主在面板维护固定库存数字，容器仅作为「补货入库」通道；
 *       把物品放进容器后在面板点补货入库，物品离开容器折算进库存数字</li>
 * </ul>
 * 购买成功后物品直接发到买家背包；玩家不在线或背包满则进入待领取队列，上线自动补发。
 * 资金走 Vault，购买即时从买家钱包扣款、即时到账店主钱包（不接 escrow 托管）。
 */
public class ShopService {

    /** 商店容器类型（放置即自动登记的方块） */
    public static boolean isContainerMaterial(Material type) {
        if (type == null) return false;
        switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL:
            case DISPENSER, DROPPER, HOPPER:
                return true;
            default:
                return type == Material.SHULKER_BOX || type.name().endsWith("_SHULKER_BOX");
        }
    }

    public record ShopResult(boolean success, String message) {}

    private final HotelsPlugin plugin;

    public ShopService(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isShopEnabled() {
        return plugin.getConfig().getBoolean("shop.enabled", true);
    }

    public double getMaxPrice() {
        return plugin.getConfig().getDouble("shop.max-price", 1000000.0);
    }

    public int getMaxShopsPerPlayer() {
        return plugin.getConfig().getInt("shop.max-shops-per-player", 5);
    }

    public boolean isAllowInRooms() {
        return plugin.getConfig().getBoolean("shop.allow-in-rooms", true);
    }

    public boolean isTransactionMessages() {
        return plugin.getConfig().getBoolean("shop.transaction-messages", true);
    }

    public Location locationOf(Shop shop) {
        World world = Bukkit.getWorld(shop.getWorld());
        if (world == null) return null;
        return new Location(world, shop.getX(), shop.getY(), shop.getZ());
    }

    /**
     * 在区块所属区域线程上同步执行容器/世界操作并等待结果（保护主线程安全）。
     * 非主线程调用时会阻塞最多 5 秒；容器区块未加载时返回 null。
     */
    public <T> T syncContainer(Shop shop, Supplier<T> task) {
        Location loc = locationOf(shop);
        if (loc == null) return null;
        if (Bukkit.isPrimaryThread()) return task.get();
        CountDownLatch latch = new CountDownLatch(1);
        Object[] box = new Object[1];
        SchedulerCompat.runOnRegion(plugin, loc, () -> {
            try {
                box[0] = task.get();
            } catch (Throwable t) {
                plugin.getLogger().warning("店面容器操作异常 [" + shop.getLocationKey() + "]: " + t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("店面容器操作超时 [" + shop.getLocationKey() + "]");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        @SuppressWarnings("unchecked")
        T result = (T) box[0];
        return result;
    }

    /** 获取容器方块（必须在区域线程调用；区块未加载或方块不是容器时返回 null） */
    public org.bukkit.block.Container getBlockContainer(Shop shop) {
        Location loc = locationOf(shop);
        if (loc == null || loc.getWorld() == null) return null;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return null;
        var state = loc.getBlock().getState();
        return state instanceof org.bukkit.block.Container c ? c : null;
    }

    /** 容器内匹配商品的物品总数（AUTO 模式实时库存） */
    public int countContainerItems(Shop shop, Material mat) {
        Integer n = syncContainer(shop, () -> {
            var container = getBlockContainer(shop);
            if (container == null) return 0;
            int sum = 0;
            for (ItemStack it : container.getInventory().getContents()) {
                if (it != null && it.getType() == mat) sum += it.getAmount();
            }
            return sum;
        });
        return n == null ? 0 : n;
    }

    /** 从容器移除指定数量物品，全部移除成功返回 true */
    public boolean removeItemsFromContainer(Shop shop, Material mat, int amount) {
        Boolean ok = syncContainer(shop, () -> {
            var container = getBlockContainer(shop);
            if (container == null) return false;
            Inventory inv = container.getInventory();
            int remaining = amount;
            for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.getType() != mat) continue;
                int take = Math.min(it.getAmount(), remaining);
                it.setAmount(it.getAmount() - take);
                remaining -= take;
                if (it.getAmount() <= 0) inv.setItem(i, null);
            }
            return remaining == 0;
        });
        return Boolean.TRUE.equals(ok);
    }

    /** 放入玩家背包，返回成功入包的数量（在线玩家，须主线程/区域线程调用） */
    public int giveToPlayer(Player player, Material mat, int amount) {
        int accepted = 0;
        while (amount > 0) {
            int batch = Math.min(amount, mat.getMaxStackSize());
            ItemStack stack = new ItemStack(mat, batch);
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(stack);
            if (!left.isEmpty()) {
                break;
            }
            accepted += batch;
            amount -= batch;
        }
        return accepted;
    }

    /**
     * 购买商品（出售商店）
     */
    public ShopResult purchase(Shop shop, String buyerName, int amount) {
        if (!isShopEnabled()) return new ShopResult(false, "店面系统未启用");
        if (buyerName == null || buyerName.isEmpty()) return new ShopResult(false, "当前账号未关联游戏内玩家名，无法购买");
        if (amount < 1) return new ShopResult(false, "购买数量无效");
        if (shop == null) return new ShopResult(false, "店铺不存在");
        if (!shop.isEnabled()) return new ShopResult(false, "该店铺已停业");
        if (!plugin.getEconomyManager().isEnabled()) return new ShopResult(false, "经济系统未启用");
        Material mat = matchMaterial(shop.getMaterial());
        if (mat == null) return new ShopResult(false, "商品类型无效");

        OfflinePlayer buyer = Bukkit.getOfflinePlayer(buyerName);
        String buyerUuid = buyer.getUniqueId().toString();
        if (buyerUuid.equalsIgnoreCase(shop.getOwnerUUID())) {
            return new ShopResult(false, "不能购买自己店铺的商品");
        }
        if (isRoomBlocked(shop)) return new ShopResult(false, "店铺所在房间已锁定或维护中，暂时停业");

        double price = shop.getPrice();
        if (price <= 0) return new ShopResult(false, "商品价格无效");

        // 库存校验
        boolean takeFromContainer = shop.getMode() == Shop.ShopMode.AUTO;
        int stock = takeFromContainer ? countContainerItems(shop, mat) : shop.getStock();
        if (stock < amount) {
            return new ShopResult(false, "库存不足，当前仅剩 " + stock + " 个");
        }

        double total = price * amount;
        // 扣买家款
        if (!plugin.getEconomyManager().withdraw(buyer, total)) {
            return new ShopResult(false, "余额不足，需要 " + plugin.getEconomyManager().format(total));
        }
        // 店主收款（若失败则回滚买家）
        OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(shop.getOwnerUUID()));
        if (!plugin.getEconomyManager().deposit(owner, total)) {
            plugin.getEconomyManager().deposit(buyer, total);
            return new ShopResult(false, "店主人账失败，交易已取消");
        }

        // 扣库存（AUTO 从容器扣除）
        if (takeFromContainer) {
            boolean ok = removeItemsFromContainer(shop, mat, amount);
            if (!ok) {
                plugin.getEconomyManager().deposit(buyer, total);
                plugin.getEconomyManager().withdraw(owner, total);
                return new ShopResult(false, "容器物品同步失败（方块被破坏或区块未加载），交易已回滚");
            }
        } else {
            shop.setStock(shop.getStock() - amount);
        }

        // 交付物品
        Player online = Bukkit.getPlayer(buyer.getUniqueId());
        int delivered = 0;
        if (online != null && online.isOnline()) {
            Integer del = syncContainer(shop, () -> giveToPlayer(online, mat, amount));
            delivered = del == null ? 0 : del;
        }
        if (delivered < amount) {
            enqueueDelivery(shop.getId(), buyer.getName(), buyerUuid, mat.name(), amount - delivered, price);
        }

        // 统计
        shop.addRevenue(total);
        shop.setSoldCount(shop.getSoldCount() + amount);
        plugin.getShopStorage().saveShop(shop);

        // 交易提示
        String tip = delivered == amount
                ? "，物品已放入你的背包"
                : (delivered > 0
                    ? "，背包空间不足，剩余 " + (amount - delivered) + " 件已进入待领取队列"
                    : "，你当前不在线，" + amount + " 件已进入待领取队列");
        if (isTransactionMessages() && online != null && online.isOnline()) {
            String key = delivered == amount ? "shop.purchase.delivered"
                    : (delivered > 0 ? "shop.purchase.partial" : "shop.purchase.offline");
            online.sendActionBar(plugin.getLang().get(key,
                    "amount", amount, "material", mat.name(),
                    "total", plugin.getEconomyManager().format(total),
                    "remaining", amount - delivered));
        }
        plugin.getLogger().info("[Web商店] " + buyerName + " 在店铺 " + shop.getDisplayNameSafe()
                + " 购买了 " + amount + " x " + mat.name() + "，金额 " + plugin.getEconomyManager().format(total));
        return new ShopResult(true, "购买成功，共 " + plugin.getEconomyManager().format(total) + tip);
    }

    /**
     * FIXED 模式补货入库：把容器内所有同种物品移出容器，折算进固定库存
     */
    public ShopResult restock(Shop shop) {
        if (shop == null) return new ShopResult(false, "店铺不存在");
        if (shop.getMode() != Shop.ShopMode.FIXED) return new ShopResult(false, "AUTO 模式库存实时跟随容器，无需补货");
        Material mat = matchMaterial(shop.getMaterial());
        if (mat == null) return new ShopResult(false, "商品类型无效");

        Integer moved = syncContainer(shop, () -> {
            var container = getBlockContainer(shop);
            if (container == null) return 0;
            Inventory inv = container.getInventory();
            int n = 0;
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.getType() != mat) continue;
                n += it.getAmount();
                inv.setItem(i, null);
            }
            return n;
        });
        int count = moved == null ? 0 : moved;
        if (count <= 0) return new ShopResult(false, "容器内没有 " + mat.name() + " 可入库（区块可能未加载）");
        shop.setStock(shop.getStock() + count);
        plugin.getShopStorage().saveShop(shop);
        return new ShopResult(true, "已从容器入库 " + count + " 个 " + mat.name() + "，当前库存 " + shop.getStock());
    }

    /** 玩家上线补发待领取物品 */
    public void claimDeliveries(Player player) {
        String uuid = player.getUniqueId().toString();
        List<ShopDelivery> list = plugin.getShopStorage().getDeliveriesForPlayer(uuid);
        if (list.isEmpty()) return;
        boolean claimedAny = false;
        boolean stillWaiting = false;
        for (ShopDelivery d : list) {
            Material mat = matchMaterial(d.getMaterial());
            if (mat == null) {
                plugin.getShopStorage().removeDelivery(d.getId());
                continue;
            }
            int delivered = giveToPlayer(player, mat, d.getAmount());
            if (delivered > 0) {
                d.setAmount(d.getAmount() - delivered);
                if (d.getAmount() <= 0) {
                    plugin.getShopStorage().removeDelivery(d.getId());
                } else {
                    plugin.getShopStorage().updateDelivery(d);
                    stillWaiting = true;
                }
                claimedAny = true;
            } else {
                stillWaiting = true;
            }
        }
        if (claimedAny) {
            if (stillWaiting) {
                plugin.getLang().send(player, "shop.deliveries_partial");
            } else {
                plugin.getLang().send(player, "shop.deliveries_all");
            }
        }
    }

    /** 店铺所在房间是否锁定/维护中（联动停业） */
    public boolean isRoomBlocked(Shop shop) {
        if (shop.getRoomId() == null || shop.getRoomId().isEmpty()) return false;
        HotelRoom room = plugin.getRoomStorage().getRoom(shop.getRoomId());
        return room != null && (room.isLocked()
                || room.getStatus() == HotelRoom.RoomStatus.MAINTENANCE);
    }

    /** 解析商品 Material 名（大小写不敏感，含空白修剪） */
    public static Material matchMaterial(String name) {
        if (name == null || name.isEmpty()) return null;
        return Material.matchMaterial(name.trim().toUpperCase());
    }

    /** 店铺所在房间 ID（创建时自动关联） */
    public String resolveRoomId(Location loc) {
        HotelRoom room = plugin.getRoomStorage().getRoomAtLocation(loc);
        return room == null ? null : room.getId();
    }

    private void enqueueDelivery(String shopId, String playerName, String playerUuid, String material, int amount, double price) {
        ShopDelivery d = new ShopDelivery();
        d.setShopId(shopId);
        d.setPlayerName(playerName == null ? "" : playerName);
        d.setPlayerUUID(playerUuid);
        d.setMaterial(material);
        d.setAmount(amount);
        d.setPrice(price);
        plugin.getShopStorage().addDelivery(d);
    }
}