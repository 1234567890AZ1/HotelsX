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
        HotelsPlugin plugin = HotelsPlugin.getInstance();
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":manage"), 27, plugin.getLang().get("gui.collection.manage_title"));

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        inv.setItem(11, createItem(Material.ENDER_CHEST, plugin.getLang().get("gui.collection.browse_all_name"),
                plugin.getLang().get("gui.collection.browse_all_lore1"),
                "",
                plugin.getLang().get("gui.collection.click_browse")));

        inv.setItem(13, createItem(Material.CHEST, plugin.getLang().get("gui.collection.create_name"),
                plugin.getLang().get("gui.collection.create_lore1"),
                plugin.getLang().get("gui.collection.create_lore2"),
                "",
                plugin.getLang().get("gui.collection.click_create")));

        inv.setItem(15, createItem(Material.BOOKSHELF, plugin.getLang().get("gui.collection.my_hotels_name"),
                plugin.getLang().get("gui.collection.my_hotels_lore1"),
                "",
                plugin.getLang().get("gui.collection.click_view")));

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
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

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":browse_all:" + page), 54, plugin.getLang().get("gui.collection.browse_title", "page", page + 1, "total", Math.max(1, totalPages)));

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName(plugin.getLang().get("gui.common.search_hotel"));
            searchMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.common.click_search")));
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
                lore.add(plugin.getLang().get("gui.common.owner", "owner", col.getOwnerName()));
                lore.add(plugin.getLang().get("gui.collection.rooms_available", "count", col.getRoomCount(), "available", available));
                lore.add(plugin.getLang().get("gui.collection.duration", "duration", col.getDurationDisplay()));
                lore.add("");
                lore.add(plugin.getLang().get("gui.collection.view_rooms_hint"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack prev = new ItemStack(page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(plugin.getLang().get(page > 0 ? "gui.common.prev_page" : "gui.common.prev_page_off"));
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(45, prev);

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        ItemStack next = new ItemStack(page < totalPages - 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(plugin.getLang().get(page < totalPages - 1 ? "gui.common.next_page" : "gui.common.next_page_off"));
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
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":my"), 54, plugin.getLang().get("gui.collection.my_title"));

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName(plugin.getLang().get("gui.common.search_hotel"));
            searchMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.common.click_search")));
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
                lore.add(plugin.getLang().get("gui.collection.rooms_count", "count", col.getRoomCount()));
                lore.add("");
                lore.add(plugin.getLang().get("gui.collection.manage_hint"));
                lore.add(plugin.getLang().get("gui.collection.delete_hint"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
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

        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":manage_collection:" + page, col), 54, plugin.getLang().get("gui.collection.manage_collection_title", "name", col.getName(), "page", page + 1, "total", Math.max(1, totalPages)));

        ItemStack infoItem = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6" + col.getName());
            infoMeta.setLore(Arrays.asList(
                    plugin.getLang().get("gui.collection.rooms_progress", "in", inCol.size(), "total", rooms.size()),
                    plugin.getLang().get("gui.collection.admins_count", "count", col.getAdminCount()),
                    plugin.getLang().get("gui.collection.click_toggle_room")
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
            searchMeta.setDisplayName(plugin.getLang().get("gui.common.search_room"));
            searchMeta.setLore(java.util.Arrays.asList(plugin.getLang().get("gui.common.click_search")));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(3, searchItem);

        inv.setItem(46, createItem(Material.PLAYER_HEAD, "§d§l管理员管理",
                plugin.getLang().get("gui.collection.admin_manage_lore1"),
                plugin.getLang().get("gui.collection.admin_manage_lore2", "count", col.getAdminCount()),
                "",
                plugin.getLang().get("gui.collection.click_manage")));

        inv.setItem(52, createItem(Material.GOLD_INGOT, "§6§l一键定价",
                plugin.getLang().get("gui.collection.set_price_lore1"),
                plugin.getLang().get("gui.collection.set_price_lore2", "count", inCol.size()),
                "",
                plugin.getLang().get("gui.collection.click_set")));

        int slot = 9;
        for (HotelRoom room : pageRooms) {
            if (slot >= 45) break;
            boolean isInCol = col.getRoomIds().contains(room.getId());

            Material mat = isInCol ? Material.GREEN_WOOL : Material.RED_WOOL;
            String status = plugin.getLang().get(isInCol ? "gui.collection.joined" : "gui.collection.not_joined");

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + room.getName());
                meta.setLore(Arrays.asList(
                        plugin.getLang().get("gui.common.id", "id", room.getId()),
                        plugin.getLang().get("gui.common.status", "status", status),
                        "",
                        plugin.getLang().get(isInCol ? "gui.collection.click_remove_collection" : "gui.collection.click_add_collection")
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack prev = new ItemStack(page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(plugin.getLang().get(page > 0 ? "gui.common.prev_page" : "gui.common.prev_page_off"));
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(45, prev);

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        ItemStack next = new ItemStack(page < totalPages - 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(plugin.getLang().get(page < totalPages - 1 ? "gui.common.next_page" : "gui.common.next_page_off"));
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
                    plugin.getLang().get("gui.common.owner", "owner", col.getOwnerName()),
                    plugin.getLang().get("gui.collection.rooms_count", "count", rooms.size())
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
                        plugin.getLang().get("gui.common.owner", "owner", room.getOwnerName()),
                        plugin.getLang().get("gui.common.price", "price", room.getPrice()),
                        plugin.getLang().get("gui.common.status", "status", getStatusDisplay(room.getStatus())),
                        "",
                        plugin.getLang().get("gui.collection.left_checkin")
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    public static void openAdminManage(Player player, RoomCollection col, HotelsPlugin plugin) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME + ":admin_manage", col), 54, plugin.getLang().get("gui.collection.admin_title", "name", col.getName()));

        ItemStack infoItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(plugin.getLang().get("gui.collection.admin_info_name"));
            infoMeta.setLore(Arrays.asList(
                    plugin.getLang().get("gui.collection.admin_info_lore1", "owner", col.getOwnerName()),
                    plugin.getLang().get("gui.collection.admin_info_lore2", "count", col.getAdminCount()),
                    plugin.getLang().get("gui.collection.admin_click_hint")
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
                status = plugin.getLang().get("gui.collection.role_owner");
            } else if (isAdmin) {
                mat = Material.EMERALD_BLOCK;
                status = plugin.getLang().get("gui.collection.role_admin");
            } else {
                mat = Material.STONE;
                status = plugin.getLang().get("gui.collection.role_normal");
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + online.getName());
                meta.setLore(Arrays.asList(
                        plugin.getLang().get("gui.common.status", "status", status),
                        "",
                        isOwner ? plugin.getLang().get("gui.collection.owner_cannot_operate") :
                        (isAdmin ? plugin.getLang().get("gui.collection.click_remove_admin") : plugin.getLang().get("gui.collection.click_add_admin"))
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(plugin.getLang().get("gui.common.back_red"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    private static String getStatusDisplay(HotelRoom.RoomStatus status) {
        switch (status) {
            case AVAILABLE: return HotelsPlugin.getInstance().getLang().get("command.status.available");
            case OCCUPIED: return HotelsPlugin.getInstance().getLang().get("command.status.occupied");
            case MAINTENANCE: return HotelsPlugin.getInstance().getLang().get("command.status.maintenance");
            default: return HotelsPlugin.getInstance().getLang().get("command.status.unknown");
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
