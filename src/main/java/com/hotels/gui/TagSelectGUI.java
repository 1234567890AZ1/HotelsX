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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TagSelectGUI {

    public static final String GUI_NAME = "tag_select";

    public static void open(Player player, HotelRoom room, HotelsPlugin plugin) {
        List<String> presetTags = plugin.getConfig().getStringList("room-tags");
        List<String> currentTags = room.getTags();

        int size = Math.min(54, Math.max(9, ((presetTags.size() / 9) + 2) * 9));
        Inventory inv = Bukkit.createInventory(new GUIHolder(GUI_NAME, room), size, plugin.getLang().get("gui.tag_select.title"));

        ItemStack infoItem = new ItemStack(Material.NAME_TAG);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§6" + room.getName());
            infoMeta.setLore(Arrays.asList(
                    plugin.getLang().get("gui.tag_select.info_current", "tags", room.getTagsDisplay()),
                    plugin.getLang().get("gui.tag_select.info_can_add", "n", 3 - currentTags.size()),
                    "",
                    plugin.getLang().get("gui.tag_select.click_toggle")
            ));
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(4, infoItem);

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        inv.setItem(4, infoItem);

        int slot = 9;
        for (String tag : presetTags) {
            boolean hasTag = currentTags.contains(tag);
            boolean canAdd = currentTags.size() < 3;

            Material mat = hasTag ? Material.LIME_DYE : (canAdd ? Material.GRAY_DYE : Material.BARRIER);
            String status = plugin.getLang().get(hasTag ? "gui.tag_select.selected" : (canAdd ? "gui.tag_select.click_add" : "gui.tag_select.limit_reached"));

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((hasTag ? "§a" : "§7") + "§l" + tag);
                meta.setLore(Arrays.asList(
                        plugin.getLang().get("gui.tag_select.status_line", "status", status),
                        "",
                        plugin.getLang().get(hasTag ? "gui.tag_select.click_remove" : (canAdd ? "gui.tag_select.click_add" : "gui.tag_select.limit_reached"))
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        inv.setItem(size - 1, createItem(Material.ARROW,
                plugin.getLang().get("gui.common.back_gold"),
                plugin.getLang().get("gui.tag_select.back_lore")));

        player.openInventory(inv);
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
