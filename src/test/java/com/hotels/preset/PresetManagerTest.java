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
package com.hotels.preset;

import com.hotels.model.HotelRoom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PresetManager 纯逻辑测试：房间边界与尺寸计算（不依赖 Bukkit 运行时）。
 * 这些方法是冲突检测（countNonAir 的区域范围）与尺寸匹配的基础。
 */
class PresetManagerTest {

    /** 构造一个坐标已归一化（x1<x2 等）的房间 */
    private static HotelRoom normalizedRoom(double x1, double y1, double z1,
                                            double x2, double y2, double z2) {
        HotelRoom room = new HotelRoom();
        room.setX1(Math.min(x1, x2));
        room.setY1(Math.min(y1, y2));
        room.setZ1(Math.min(z1, z2));
        room.setX2(Math.max(x1, x2));
        room.setY2(Math.max(y1, y2));
        room.setZ2(Math.max(z1, z2));
        room.setWorldName("world");
        return room;
    }

    @Nested
    @DisplayName("房间边界 roomBounds")
    class RoomBoundsTest {

        @Test
        @DisplayName("正方向坐标：min 为角点，max 为对角点")
        void forwardBounds() {
            HotelRoom room = normalizedRoom(10, 60, -20, 14, 62, -16);
            int[] b = PresetManager.roomBounds(room);
            assertArrayEquals(new int[]{10, 60, -20, 14, 62, -16}, b);
        }

        @Test
        @DisplayName("反向输入坐标（角点顺序颠倒）也能归一化")
        void reversedBounds() {
            HotelRoom room = normalizedRoom(14, 62, -16, 10, 60, -20);
            int[] b = PresetManager.roomBounds(room);
            assertArrayEquals(new int[]{10, 60, -20, 14, 62, -16}, b);
        }

        @Test
        @DisplayName("带小数的坐标向下取整")
        void fractionalBoundsFloored() {
            HotelRoom room = normalizedRoom(10.9, 60.1, -20.7, 13.2, 61.9, -17.3);
            int[] b = PresetManager.roomBounds(room);
            // min 向下取整，max 也向下取整（保证区域完整包含房间）
            assertEquals(10, b[0]);
            assertEquals(60, b[1]);
            assertEquals(-21, b[2]); // Math.floor(-20.7) = -21
            assertEquals(13, b[3]);
            assertEquals(61, b[4]);
            assertEquals(-18, b[5]); // Math.floor(-17.3) = -18
        }

        @Test
        @DisplayName("单方块房间：所有坐标相等")
        void singleBlockRoom() {
            HotelRoom room = normalizedRoom(5, 70, 5, 5, 70, 5);
            int[] b = PresetManager.roomBounds(room);
            assertArrayEquals(new int[]{5, 70, 5, 5, 70, 5}, b);
        }
    }

    @Nested
    @DisplayName("房间尺寸 roomDimensions")
    class RoomDimensionsTest {

        @Test
        @DisplayName("尺寸 = max - min + 1")
        void dimensionComputed() {
            HotelRoom room = normalizedRoom(10, 60, -20, 14, 62, -16);
            int[] d = PresetManager.roomDimensions(room);
            assertArrayEquals(new int[]{5, 3, 5}, d); // X: 10..14, Y: 60..62, Z: -20..-16
        }

        @Test
        @DisplayName("单方块房间尺寸为 1x1x1")
        void singleBlockDimension() {
            HotelRoom room = normalizedRoom(5, 70, 5, 5, 70, 5);
            int[] d = PresetManager.roomDimensions(room);
            assertArrayEquals(new int[]{1, 1, 1}, d);
        }

        @Test
        @DisplayName("不规则长宽（深度 > 宽度）也能正确计算")
        void deepRoomDimension() {
            HotelRoom room = normalizedRoom(0, 0, 0, 1, 3, 10);
            int[] d = PresetManager.roomDimensions(room);
            assertArrayEquals(new int[]{2, 4, 11}, d);
        }
    }
}
