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

import com.hotels.model.HotelRoom;
import com.hotels.model.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 押金与损坏赔偿服务。
 *
 * <p>入住时扣押金并对房间区域做一次方块/容器快照；退房时对比快照，
 * 若房间被改动则把押金赔付给房主，否则全额退还客人。
 * 快照仅保存在内存中（服务器重启后当前住客的押金按全额退还处理），
 * 房间体积超过配置上限时跳过检测并直接退还，避免大范围扫描开销。
 */
public class DepositService {

    /** 单个房间的一次押金持有记录 */
    private static class Hold {
        double amount;
        Map<String, Material> blocks;   // null = 未做方块检测
        Map<String, Integer> items;     // null = 未做容器检测
    }

    private final HotelsPlugin plugin;
    private final Map<String, Hold> holds = new ConcurrentHashMap<>();

    public DepositService(HotelsPlugin plugin) {
        this.plugin = plugin;
    }

    /** 解析该房间应收取的押金金额（房间未设置时使用全局默认值），0 表示不启用 */
    public double resolveAmount(HotelRoom room) {
        if (room.hasDeposit()) return room.getDeposit();
        return Math.max(0, plugin.getConfig().getDouble("deposit.default-amount", 0));
    }

    /**
     * 记录押金持有并做快照（应在客人扣款成功后调用）
     */
    public void hold(HotelRoom room, double amount) {
        if (amount <= 0) return;
        Hold hold = new Hold();
        hold.amount = amount;
        long volume = room.getVolume();
        int maxVolume = plugin.getConfig().getInt("deposit.max-volume", 20000);
        World world = Bukkit.getWorld(room.getWorldName());
        if (world != null && volume <= maxVolume) {
            hold.blocks = new HashMap<>();
            hold.items = new HashMap<>();
            snapshot(room, world, hold);
        }
        holds.put(room.getId(), hold);
    }

    /**
     * 结算押金：无损坏则全额退还客人，有损坏则赔付给房主。
     *
     * @return 退还给客人的金额（赔付时为 0）
     */
    public double settle(UUID guestUuid, String guestName, HotelRoom room) {
        Hold hold = holds.remove(room.getId());
        if (hold == null || hold.amount <= 0) return 0;

        boolean damaged = hold.blocks != null && detectChanges(room, hold);
        EconomyManager economy = plugin.getEconomyManager();

        if (!damaged) {
            if (economy.isEnabled()) {
                OfflinePlayer guest = Bukkit.getOfflinePlayer(guestUuid);
                economy.deposit(guest, hold.amount);
                recordTx(Transaction.TxType.DEPOSIT_REFUND, guestUuid, guestName,
                        hold.amount, room, "押金退还");
            }
            Player online = Bukkit.getPlayer(guestUuid);
            if (online != null && online.isOnline()) {
                plugin.getLang().send(online, "deposit.refunded",
                        "amount", economy.format(hold.amount));
            }
            plugin.log(null, "押金退还: " + guestName + " " + hold.amount + " (" + room.getName() + ")");
            return hold.amount;
        }

        // 房间被改动：押金赔付房主
        if (economy.isEnabled()) {
            boolean escrowMode = plugin.getConfig().getBoolean("economy.escrow-mode", false);
            if (escrowMode) {
                plugin.getEscrowStorage().deposit(room.getOwner().toString(), room.getOwnerName(), hold.amount);
            } else {
                economy.deposit(Bukkit.getOfflinePlayer(room.getOwner()), hold.amount);
            }
            recordTx(Transaction.TxType.DEPOSIT_FORFEIT, guestUuid, guestName,
                    hold.amount, room, "押金赔付(房间被改动)");
        }
        Player online = Bukkit.getPlayer(guestUuid);
        if (online != null && online.isOnline()) {
            plugin.getLang().send(online, "deposit.forfeit",
                    "amount", economy.format(hold.amount));
        }
        Player owner = Bukkit.getPlayer(room.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.getLang().get("deposit.notify_owner_forfeit",
                    "player", guestName != null ? guestName : "?",
                    "room", room.getName(),
                    "amount", economy.format(hold.amount)));
        }
        plugin.log(null, "押金赔付: " + guestName + " 的 " + hold.amount + " 赔付给房主 (" + room.getName() + ")");
        return 0;
    }

    /** 房间销毁 / 无需检测时清理内存快照 */
    public void discard(String roomId) {
        holds.remove(roomId);
    }

    private void snapshot(HotelRoom room, World world, Hold hold) {
        int minX = (int) Math.floor(Math.min(room.getX1(), room.getX2()));
        int maxX = (int) Math.floor(Math.max(room.getX1(), room.getX2()));
        int minY = (int) Math.floor(Math.min(room.getY1(), room.getY2()));
        int maxY = (int) Math.floor(Math.max(room.getY1(), room.getY2()));
        int minZ = (int) Math.floor(Math.min(room.getZ1(), room.getZ2()));
        int maxZ = (int) Math.floor(Math.max(room.getZ1(), room.getZ2()));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    String key = key(x, y, z);
                    hold.blocks.put(key, block.getType());
                    if (block.getState() instanceof Container) {
                        hold.items.put(key, countItems((Container) block.getState()));
                    }
                }
            }
        }
    }

    private boolean detectChanges(HotelRoom room, Hold hold) {
        World world = Bukkit.getWorld(room.getWorldName());
        if (world == null) return false;
        for (Map.Entry<String, Material> e : hold.blocks.entrySet()) {
            int[] pos = parseKey(e.getKey());
            if (pos == null) continue;
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            if (block.getType() != e.getValue()) return true;
        }
        for (Map.Entry<String, Integer> e : hold.items.entrySet()) {
            int[] pos = parseKey(e.getKey());
            if (pos == null) continue;
            Block block = world.getBlockAt(pos[0], pos[1], pos[2]);
            if (block.getState() instanceof Container) {
                if (countItems((Container) block.getState()) != e.getValue()) return true;
            }
        }
        return false;
    }

    private int countItems(Container container) {
        int total = 0;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null) total += item.getAmount();
        }
        return total;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static int[] parseKey(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 3) return null;
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void recordTx(Transaction.TxType type, UUID uuid, String name, double amount,
                          HotelRoom room, String remark) {
        try {
            Transaction tx = new Transaction();
            tx.setType(type);
            tx.setPlayerUUID(uuid != null ? uuid.toString() : null);
            tx.setPlayerName(name != null ? name : "未知");
            tx.setAmount(amount);
            tx.setRoomId(room.getId());
            tx.setRoomName(room.getName());
            tx.setRemark(remark);
            plugin.getTransactionStorage().add(tx);
        } catch (Exception e) {
            plugin.getLogger().warning("记录押金流水失败: " + e.getMessage());
        }
    }
}
