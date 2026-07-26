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
package com.hotels.gui;

import com.hotels.HotelsPlugin;
import com.hotels.model.HotelRoom;
import com.hotels.model.RoomCollection;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CollectionGUI {

    public static final String GUI_NAME = "collection";

    public static void openManage(Player player) {
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":manage"), 27, "§8§l酒店合集");

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        inv.setItem(11, createItem(Material.ENDER_CHEST, "§d§l浏览所有酒店",
                "§7查看所有玩家创建的酒店合集",
                "",
                "§8点击浏览"));

        inv.setItem(13, createItem(Material.CHEST, "§a§l创建新酒店",
                "§7创建一个新的房间合集",
                "§7创建后可以将自己的房间加入",
                "",
                "§8点击创建"));

        inv.setItem(15, createItem(Material.BOOKSHELF, "§e§l我的酒店",
                "§7查看和管理你创建的酒店合集",
                "",
                "§8点击查看"));

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            back.setItemMeta(backMeta);
        }
        inv.setItem(22, back);

        player.openInventory(inv);
    }

    public static void openBrowseAll(Player player, HotelsPlugin plugin) {
        openBrowseAll(player, plugin, 0);
    }

    public static void openBrowseAll(Player player, HotelsPlugin plugin, int page) {
        List<RoomCollection> allCols = new ArrayList<>(plugin.getRoomStorage().getAllCollections());
        openBrowseAll(player, plugin, allCols, page);
    }

    public static void openBrowseAll(Player player, HotelsPlugin plugin, List<RoomCollection> collections, int page) {
        
        int totalPages = (int) Math.ceil((double) collections.size() / 36);
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);

        int start = page * 36;
        int end = Math.min(start + 36, collections.size());
        List<RoomCollection> pageCols = collections.subList(start, end);

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":browse_all:" + page), 54, "§8§l浏览酒店 §7(" + (page + 1) + "/" + Math.max(1, totalPages) + ")");

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName("§d§l搜索酒店");
            searchMeta.setLore(java.util.Arrays.asList("§7点击搜索"));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(4, searchItem);

        int slot = 9;
        for (RoomCollection col : pageCols) {
            if (slot >= 45) break;
            List<HotelRoom> rooms = plugin.getRoomStorage().getCollectionRooms(col.getId());
            long available = rooms.stream().filter(r -> r.isAvailable() && !r.isLocked()).count();

            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + col.getName());
                List<String> lore = new ArrayList<>();
                if (col.getDescription() != null) {
                    lore.add("§7" + col.getDescription());
                }
                lore.add("§7房主: §f" + col.getOwnerName());
                lore.add("§7房间: §f" + col.getRoomCount() + " §7间 (空闲 §a" + available + "§7)");
                lore.add("§7时长: " + col.getDurationDisplay());
                lore.add("");
                lore.add("§e左键 §7查看房间列表");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack prev = new ItemStack(page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(page > 0 ? "§7上一页" : "§8上一页");
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(45, prev);

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        ItemStack next = new ItemStack(page < totalPages - 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(page < totalPages - 1 ? "§7下一页" : "§8下一页");
            next.setItemMeta(nextMeta);
        }
        inv.setItem(53, next);

        player.openInventory(inv);
    }

    public static void openMyCollections(Player player, HotelsPlugin plugin) {
        List<RoomCollection> myCols = plugin.getRoomStorage().getCollectionsByOwner(player.getUniqueId());
        openMyCollections(player, plugin, myCols);
    }

    public static void openMyCollections(Player player, HotelsPlugin plugin, List<RoomCollection> collections) {
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":my"), 54, "§8§l我的酒店");

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName("§d§l搜索酒店");
            searchMeta.setLore(java.util.Arrays.asList("§7点击搜索"));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(4, searchItem);

        int slot = 9;
        for (RoomCollection col : collections) {
            if (slot >= 45) break;
            List<HotelRoom> rooms = plugin.getRoomStorage().getCollectionRooms(col.getId());

            ItemStack item = new ItemStack(Material.BOOKSHELF);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + col.getName());
                List<String> lore = new ArrayList<>();
                if (col.getDescription() != null) {
                    lore.add("§7" + col.getDescription());
                }
                lore.add("§7房间: §f" + col.getRoomCount() + " §7间");
                lore.add("");
                lore.add("§e左键 §7管理合集");
                lore.add("§c右键 §7删除合集");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    public static void openManageCollection(Player player, RoomCollection col, HotelsPlugin plugin) {
        openManageCollection(player, col, plugin, 0);
    }

    public static void openManageCollection(Player player, RoomCollection col, HotelsPlugin plugin, int page) {
        List<HotelRoom> myRooms = plugin.getRoomStorage().getRoomsByOwner(player.getUniqueId());
        openManageCollection(player, col, plugin, myRooms, page);
    }

    public static void openManageCollection(Player player, RoomCollection col, HotelsPlugin plugin, List<HotelRoom> rooms, int page) {
        List<HotelRoom> inCol = plugin.getRoomStorage().getCollectionRooms(col.getId());

        int totalPages = (int) Math.ceil((double) rooms.size() / 36);
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);

        int start = page * 36;
        int end = Math.min(start + 36, rooms.size());
        List<HotelRoom> pageRooms = rooms.subList(start, end);

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":manage_collection:" + page, col), 54, "§8§l" + col.getName() + " §7(" + (page + 1) + "/" + Math.max(1, totalPages) + ")");

        ItemStack infoItem = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6" + col.getName());
            infoMeta.setLore(Arrays.asList(
                    "§7房间: §f" + inCol.size() + " / " + rooms.size(),
                    "§7管理员: §f" + col.getAdminCount() + " 人",
                    "§e点击房间添加/移除"
            ));
            infoItem.setItemMeta(infoMeta);
        }

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(4, infoItem);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName("§d§l搜索房间");
            searchMeta.setLore(java.util.Arrays.asList("§7点击搜索"));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(3, searchItem);

        inv.setItem(46, createItem(Material.PLAYER_HEAD, "§d§l管理员管理",
                "§7添加或移除合集管理员",
                "§7当前 " + col.getAdminCount() + " 位管理员",
                "",
                "§8点击管理"));

        inv.setItem(52, createItem(Material.GOLD_INGOT, "§6§l一键定价",
                "§7统一设置合集内所有房间的价格",
                "§7当前 " + inCol.size() + " 个房间",
                "",
                "§8点击设置"));

        int slot = 9;
        for (HotelRoom room : pageRooms) {
            if (slot >= 45) break;
            boolean isInCol = col.getRoomIds().contains(room.getId());

            Material mat = isInCol ? Material.GREEN_WOOL : Material.RED_WOOL;
            String status = isInCol ? "§a已加入" : "§c未加入";

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + room.getName());
                meta.setLore(Arrays.asList(
                        "§7ID: §f" + room.getId(),
                        "§7状态: " + status,
                        "",
                        "§e点击" + (isInCol ? "移出" : "加入") + "合集"
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack prev = new ItemStack(page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(page > 0 ? "§7上一页" : "§8上一页");
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(45, prev);

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        ItemStack next = new ItemStack(page < totalPages - 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(page < totalPages - 1 ? "§7下一页" : "§8下一页");
            next.setItemMeta(nextMeta);
        }
        inv.setItem(53, next);

        player.openInventory(inv);
    }

    public static void openCollectionRooms(Player player, RoomCollection col, HotelsPlugin plugin) {
        List<HotelRoom> rooms = plugin.getRoomStorage().getCollectionRooms(col.getId());
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":collection_rooms", col), 54, "§8§l" + col.getName());

        ItemStack infoItem = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6" + col.getName());
            infoMeta.setLore(Arrays.asList(
                    "§7房主: §f" + col.getOwnerName(),
                    "§7房间: §f" + rooms.size() + " §7间"
            ));
            infoItem.setItemMeta(infoMeta);
        }

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(4, infoItem);

        int slot = 9;
        for (HotelRoom room : rooms) {
            if (slot >= 45) break;
            Material mat;
            switch (room.getStatus()) {
                case AVAILABLE: mat = Material.GREEN_WOOL; break;
                case OCCUPIED: mat = Material.RED_WOOL; break;
                default: mat = Material.GRAY_WOOL;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + room.getName());
                meta.setLore(Arrays.asList(
                        "§7房主: §f" + room.getOwnerName(),
                        "§7价格: §f" + room.getPrice(),
                        "§7状态: " + getStatusDisplay(room.getStatus()),
                        "",
                        "§e左键 §7入住"
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    public static void openAdminManage(Player player, RoomCollection col, HotelsPlugin plugin) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":admin_manage", col), 54, "§8§l管理员: " + col.getName());

        ItemStack infoItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6管理员管理");
            infoMeta.setLore(Arrays.asList(
                    "§7房主: §f" + col.getOwnerName(),
                    "§7管理员: §f" + col.getAdminCount() + " 人",
                    "§e点击在线玩家添加/移除管理员"
            ));
            infoItem.setItemMeta(infoMeta);
        }

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(4, infoItem);

        int slot = 9;
        for (Player online : onlinePlayers) {
            if (slot >= 45) break;
            boolean isOwner = col.getOwner().equals(online.getUniqueId());
            boolean isAdmin = col.getAdmins().contains(online.getUniqueId().toString());

            Material mat;
            String status;
            if (isOwner) {
                mat = Material.GOLD_BLOCK;
                status = "§6房主";
            } else if (isAdmin) {
                mat = Material.EMERALD_BLOCK;
                status = "§a管理员";
            } else {
                mat = Material.STONE;
                status = "§7普通";
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + online.getName());
                meta.setLore(Arrays.asList(
                        "§7状态: " + status,
                        "",
                        isOwner ? "§7房主不可操作" :
                        (isAdmin ? "§c点击移除管理员" : "§a点击添加为管理员")
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l返回");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    private static String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return "§a空闲";
            case OCCUPIED: return "§c已入住";
            case MAINTENANCE: return "§7维护中";
            default: return "§7未知";
        }
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}