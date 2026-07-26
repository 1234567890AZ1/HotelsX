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
package com.hotels.selection;

import com.hotels.model.PlayerSelection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 选区管理器 - 管理玩家的选点数据
 */
public class SelectionManager {

    private final Map<UUID, PlayerSelection> selections;

    public SelectionManager() {
        this.selections = new ConcurrentHashMap<>();
    }

    /**
     * 获取玩家的选区数据
     */
    public PlayerSelection getSelection(Player player) {
        return selections.computeIfAbsent(
                player.getUniqueId(),
                k -> new PlayerSelection(player.getUniqueId())
        );
    }

    /**
     * 设置第一个点
     */
    public void setPos1(Player player, Location loc) {
        PlayerSelection sel = getSelection(player);
        sel.setPos1(loc);
    }

    /**
     * 设置第二个点
     */
    public void setPos2(Player player, Location loc) {
        PlayerSelection sel = getSelection(player);
        sel.setPos2(loc);
    }

    /**
     * 设置传送点
     */
    public void setSpawnPoint(Player player, Location loc) {
        PlayerSelection sel = getSelection(player);
        sel.setSpawnPoint(loc);
    }

    /**
     * 清除玩家的选区
     */
    public void clearSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    /**
     * 玩家是否有完整的选区（两个点 + 传送点）
     */
    public boolean hasCompleteSelection(Player player) {
        PlayerSelection sel = selections.get(player.getUniqueId());
        return sel != null && sel.hasBothPositions() && sel.hasSpawnPoint();
    }
}
