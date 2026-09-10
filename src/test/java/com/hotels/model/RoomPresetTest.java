package com.hotels.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoomPreset 单元测试 —— 覆盖旋转算法、尺寸匹配、序列化、边界条件。
 */
class RoomPresetTest {

    // ─── 辅助方法 ───────────────────────────────────────────────

    /** 构建一个 5x3x4 的预设（sizeX=5, sizeY=3, sizeZ=4）用于测试 */
    private RoomPreset buildPreset() {
        RoomPreset p = new RoomPreset();
        p.setName("test");
        p.setWorld("world");
        p.setSizeX(5);
        p.setSizeY(3);
        p.setSizeZ(4);
        return p;
    }

    // ─── getRotatedRelCoords 正确性 ────────────────────────────

    @Nested
    @DisplayName("getRotatedRelCoords 旋转坐标变换")
    class RotateCoordsTest {

        @Test
        @DisplayName("0° 旋转应返回原始坐标")
        void rotateZero() {
            RoomPreset p = buildPreset();
            p.setRotation(0);
            assertArrayEquals(new int[]{2, 1, 3}, p.getRotatedRelCoords(2, 1, 3));
        }

        @Test
        @DisplayName("90° 顺时针旋转：(x,z) -> (z, sizeX-1-x)")
        void rotate90() {
            RoomPreset p = buildPreset(); // sizeX=5, sizeZ=4
            p.setRotation(90);

            // 四个角点验证
            // 原点 (0,0,0) -> (0, 1, 4)
            assertArrayEquals(new int[]{0, 1, 4}, p.getRotatedRelCoords(0, 1, 0));
            // (4,1,3) -> (3, 1, 0)
            assertArrayEquals(new int[]{3, 1, 0}, p.getRotatedRelCoords(4, 1, 3));
            // (2,1,2) -> (2, 1, 2)
            assertArrayEquals(new int[]{2, 1, 2}, p.getRotatedRelCoords(2, 1, 2));
            // (0,0,3) -> (3, 0, 4)
            assertArrayEquals(new int[]{3, 0, 4}, p.getRotatedRelCoords(0, 0, 3));
        }

        @Test
        @DisplayName("180° 旋转：(x,z) -> (sizeX-1-x, sizeZ-1-z)")
        void rotate180() {
            RoomPreset p = buildPreset(); // sizeX=5, sizeZ=4
            p.setRotation(180);

            // (0,1,0) -> (4, 1, 3)
            assertArrayEquals(new int[]{4, 1, 3}, p.getRotatedRelCoords(0, 1, 0));
            // (4,1,3) -> (0, 1, 0)
            assertArrayEquals(new int[]{0, 1, 0}, p.getRotatedRelCoords(4, 1, 3));
            // (2,1,2) -> (2, 1, 1)
            assertArrayEquals(new int[]{2, 1, 1}, p.getRotatedRelCoords(2, 1, 2));
        }

        @Test
        @DisplayName("270° 逆时针旋转：(x,z) -> (sizeZ-1-z, x)")
        void rotate270() {
            RoomPreset p = buildPreset(); // sizeX=5, sizeZ=4
            p.setRotation(270);

            // (0,1,0) -> (3, 1, 0)
            assertArrayEquals(new int[]{3, 1, 0}, p.getRotatedRelCoords(0, 1, 0));
            // (4,1,3) -> (0, 1, 4)
            assertArrayEquals(new int[]{0, 1, 4}, p.getRotatedRelCoords(4, 1, 3));
            // (2,1,2) -> (1, 1, 2)
            assertArrayEquals(new int[]{1, 1, 2}, p.getRotatedRelCoords(2, 1, 2));
        }

        @Test
        @DisplayName("Y 轴坐标在旋转中保持不变")
        void rotationPreservesY() {
            RoomPreset p = buildPreset();
            p.setRotation(90);
            assertEquals(5, p.getRotatedRelCoords(1, 5, 2)[1]);
            p.setRotation(270);
            assertEquals(5, p.getRotatedRelCoords(1, 5, 2)[1]);
        }

        @Test
        @DisplayName("旋转两次 90° 的结果自洽（均在同一坐标系下）")
        void twoRotate90Equals180() {
            RoomPreset p = buildPreset(); // sizeX=5, sizeZ=4
            // 原始坐标 (2,1,3)
            // 一次90°: (2,1,3) -> (3, 1, sizeX-1-2) = (3, 1, 2)
            p.setRotation(90);
            int[] r1 = p.getRotatedRelCoords(2, 1, 3);
            assertEquals(3, r1[0]);
            assertEquals(1, r1[1]);
            assertEquals(2, r1[2]);

            // 直接180°: (2,1,3) -> (sizeX-1-2, 1, sizeZ-1-3) = (2, 1, 0)
            p.setRotation(180);
            int[] r2 = p.getRotatedRelCoords(2, 1, 3);
            assertEquals(2, r2[0]);
            assertEquals(1, r2[1]);
            assertEquals(0, r2[2]);

            // 验证旋转对合性：两次90°连续作用于结果坐标（仍在原坐标系）
            // 第二次90°作用于 r1=(3,1,2): (3,1,2) -> (2, 1, sizeX-1-3) = (2, 1, 1)
            p.setRotation(90);
            int[] r1again = p.getRotatedRelCoords(r1[0], r1[1], r1[2]);
            assertArrayEquals(new int[]{2, 1, 1}, r1again);
        }

        @Test
        @DisplayName("旋转360° 等于 0°（恒等变换）")
        void rotate360EqualsIdentity() {
            RoomPreset p = buildPreset();
            p.setRotation(360); // setRotation 归一化为 0
            assertArrayEquals(new int[]{2, 1, 3}, p.getRotatedRelCoords(2, 1, 3));
        }

        @Test
        @DisplayName("负角度应被归一化")
        void negativeAngleNormalized() {
            RoomPreset p = buildPreset();
            p.setRotation(-90); // 应为 270
            assertEquals(270, p.getRotation());
            // -90 的结果应与 270 相同
            int[] negResult = p.getRotatedRelCoords(1, 1, 1);
            p.setRotation(270);
            int[] posResult = p.getRotatedRelCoords(1, 1, 1);
            assertArrayEquals(posResult, negResult);
        }

        @Test
        @DisplayName("超大角度应被归一化")
        void largeAngleNormalized() {
            RoomPreset p = buildPreset();
            p.setRotation(720); // 应为 0
            assertEquals(0, p.getRotation());
            p.setRotation(450); // 应为 90
            assertEquals(90, p.getRotation());
        }

        @Test
        @DisplayName("90° 旋转是双射：无冲突且完整覆盖旋转后网格")
        void rotate90IsBijection() {
            RoomPreset p = buildPreset(); // 5x3x4，旋转后占据 4x3x5
            p.setRotation(90);
            Set<String> targets = new HashSet<>();
            for (int rx = 0; rx < 5; rx++) {
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r = p.getRotatedRelCoords(rx, ry, rz);
                        assertTrue(r[0] >= 0 && r[0] < 4, "rx' 越界: " + r[0]);
                        assertEquals(ry, r[1]);
                        assertTrue(r[2] >= 0 && r[2] < 5, "rz' 越界: " + r[2]);
                        String key = r[0] + "," + r[1] + "," + r[2];
                        assertTrue(targets.add(key), "坐标冲突: " + key);
                    }
                }
            }
            assertEquals(5 * 3 * 4, targets.size(), "双射要求覆盖全部网格单元");
        }

        @Test
        @DisplayName("180° 旋转是双射（尺寸不变）")
        void rotate180IsBijection() {
            RoomPreset p = buildPreset(); // 5x3x4
            p.setRotation(180);
            Set<String> targets = new HashSet<>();
            for (int rx = 0; rx < 5; rx++) {
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r = p.getRotatedRelCoords(rx, ry, rz);
                        assertTrue(r[0] >= 0 && r[0] < 5 && r[2] >= 0 && r[2] < 4);
                        assertTrue(targets.add(r[0] + "," + r[1] + "," + r[2]));
                    }
                }
            }
            assertEquals(5 * 3 * 4, targets.size());
        }

        @Test
        @DisplayName("270° 旋转是双射：无冲突且完整覆盖旋转后网格")
        void rotate270IsBijection() {
            RoomPreset p = buildPreset(); // 5x3x4，旋转后占据 4x3x5
            p.setRotation(270);
            Set<String> targets = new HashSet<>();
            for (int rx = 0; rx < 5; rx++) {
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r = p.getRotatedRelCoords(rx, ry, rz);
                        assertTrue(r[0] >= 0 && r[0] < 4 && r[2] >= 0 && r[2] < 5);
                        assertTrue(targets.add(r[0] + "," + r[1] + "," + r[2]));
                    }
                }
            }
            assertEquals(5 * 3 * 4, targets.size());
        }

        @Test
        @DisplayName("90° 后再逆旋转 270° 等价于恒等变换（所有网格单元）")
        void rotate90Then270IsIdentity() {
            RoomPreset p90 = buildPreset(); // 5x3x4 -> 4x3x5
            p90.setRotation(90);
            RoomPreset p270 = new RoomPreset();
            p270.setSizeX(4);
            p270.setSizeY(3);
            p270.setSizeZ(5);
            p270.setRotation(270);
            for (int rx = 0; rx < 5; rx++) {
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r1 = p90.getRotatedRelCoords(rx, ry, rz);
                        int[] r2 = p270.getRotatedRelCoords(r1[0], r1[1], r1[2]);
                        assertArrayEquals(new int[]{rx, ry, rz}, r2,
                                "坐标 (" + rx + "," + ry + "," + rz + ")");
                    }
                }
            }
        }

        @Test
        @DisplayName("270° 后再旋转 90° 等价于恒等变换（所有网格单元）")
        void rotate270Then90IsIdentity() {
            RoomPreset p270 = buildPreset(); // 5x3x4 -> 4x3x5
            p270.setRotation(270);
            RoomPreset p90 = new RoomPreset();
            p90.setSizeX(4);
            p90.setSizeY(3);
            p90.setSizeZ(5);
            p90.setRotation(90);
            for (int rx = 0; rx < 5; rx++) {
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r1 = p270.getRotatedRelCoords(rx, ry, rz);
                        int[] r2 = p90.getRotatedRelCoords(r1[0], r1[1], r1[2]);
                        assertArrayEquals(new int[]{rx, ry, rz}, r2,
                                "坐标 (" + rx + "," + ry + "," + rz + ")");
                    }
                }
            }
        }

        @Test
        @DisplayName("180° 旋转两次等价于恒等变换（所有网格单元）")
        void rotate180TwiceIsIdentity() {
            RoomPreset p = buildPreset();
            p.setRotation(180);
            for (int rx = 0; rx < 5; rx++) {
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r1 = p.getRotatedRelCoords(rx, ry, rz);
                        int[] r2 = p.getRotatedRelCoords(r1[0], r1[1], r1[2]);
                        assertArrayEquals(new int[]{rx, ry, rz}, r2,
                                "坐标 (" + rx + "," + ry + "," + rz + ")");
                    }
                }
            }
        }

        @Test
        @DisplayName("非正方形长条房间的旋转双射性（1x3x4）")
        void bijectionOnNarrowRoom() {
            RoomPreset p = new RoomPreset();
            p.setSizeX(1);
            p.setSizeY(3);
            p.setSizeZ(4);
            for (int rot : new int[]{90, 180, 270}) {
                p.setRotation(rot);
                Set<String> targets = new HashSet<>();
                for (int ry = 0; ry < 3; ry++) {
                    for (int rz = 0; rz < 4; rz++) {
                        int[] r = p.getRotatedRelCoords(0, ry, rz);
                        String key = r[0] + "," + r[1] + "," + r[2];
                        assertTrue(targets.add(key), "旋转 " + rot + "° 坐标冲突: " + key);
                    }
                }
                assertEquals(3 * 4, targets.size(), "旋转 " + rot + "° 双射要求完整覆盖");
            }
        }
    }

    // ─── setRotation 异常处理 ──────────────────────────────────

    @Nested
    @DisplayName("setRotation 输入验证")
    class RotationValidationTest {

        @Test
        @DisplayName("90的倍数角度应通过")
        void validAngles() {
            RoomPreset p = buildPreset();
            assertDoesNotThrow(() -> p.setRotation(0));
            assertDoesNotThrow(() -> p.setRotation(90));
            assertDoesNotThrow(() -> p.setRotation(180));
            assertDoesNotThrow(() -> p.setRotation(270));
            assertDoesNotThrow(() -> p.setRotation(360));
            assertDoesNotThrow(() -> p.setRotation(-90));
        }

        @Test
        @DisplayName("非90倍数的角度应抛出 IllegalArgumentException")
        void invalidAngles() {
            RoomPreset p = buildPreset();
            assertThrows(IllegalArgumentException.class, () -> p.setRotation(45));
            assertThrows(IllegalArgumentException.class, () -> p.setRotation(100));
            assertThrows(IllegalArgumentException.class, () -> p.setRotation(1));
            assertThrows(IllegalArgumentException.class, () -> p.setRotation(30));
        }

        @Test
        @DisplayName("负角度中的无效值应被拒绝")
        void negativeInvalidAngles() {
            RoomPreset p = buildPreset();
            assertThrows(IllegalArgumentException.class, () -> p.setRotation(-45));
            assertThrows(IllegalArgumentException.class, () -> p.setRotation(-1));
        }
    }

    // ─── matchesSize ───────────────────────────────────────────

    @Nested
    @DisplayName("matchesSize 旋转尺寸匹配")
    class MatchesSizeTest {

        @Test
        @DisplayName("0°旋转：尺寸必须完全一致")
        void zeroRotationExactMatch() {
            RoomPreset p = buildPreset(); // 5x3x4
            p.setRotation(0);
            assertTrue(p.matchesSize(5, 3, 4));
            assertFalse(p.matchesSize(5, 3, 3)); // Z 不匹配
            assertFalse(p.matchesSize(6, 3, 4)); // X 不匹配
            assertFalse(p.matchesSize(5, 4, 4)); // Y 不匹配
        }

        @Test
        @DisplayName("90°旋转：X/Z 互换后匹配")
        void ninetyDegreeRotation() {
            RoomPreset p = buildPreset(); // sizeX=5, sizeY=3, sizeZ=4
            p.setRotation(90);
            // 旋转后显示尺寸为 4x3x5，房间需要 4x3x5
            assertTrue(p.matchesSize(4, 3, 5));
            assertFalse(p.matchesSize(5, 3, 4)); // 未旋转的尺寸不匹配
            assertFalse(p.matchesSize(4, 4, 5)); // Y 不匹配
        }

        @Test
        @DisplayName("180°旋转：同0°尺寸校验")
        void hundredEightyDegreeRotation() {
            RoomPreset p = buildPreset(); // 5x3x4
            p.setRotation(180);
            assertTrue(p.matchesSize(5, 3, 4));
            assertFalse(p.matchesSize(4, 3, 5));
        }

        @Test
        @DisplayName("270°旋转：同90°尺寸校验")
        void twoSeventyDegreeRotation() {
            RoomPreset p = buildPreset(); // sizeX=5, sizeY=3, sizeZ=4
            p.setRotation(270);
            assertTrue(p.matchesSize(4, 3, 5));
            assertFalse(p.matchesSize(5, 3, 4));
        }

        @Test
        @DisplayName("正方形截面房间旋转后仍可应用")
        void squareCrossSectionRotation() {
            // 正方形横截面 (5x5)，旋转不影响尺寸匹配
            RoomPreset p = new RoomPreset();
            p.setSizeX(5);
            p.setSizeY(3);
            p.setSizeZ(5);
            for (int rot : new int[]{0, 90, 180, 270}) {
                p.setRotation(rot);
                assertTrue(p.matchesSize(5, 3, 5),
                        "旋转 " + rot + "° 应与正方形房间匹配");
            }
        }

        @Test
        @DisplayName("1x1x1 立方体旋转后仍匹配")
        void unitCubeRotation() {
            RoomPreset p = new RoomPreset();
            p.setSizeX(1);
            p.setSizeY(1);
            p.setSizeZ(1);
            for (int rot : new int[]{0, 90, 180, 270}) {
                p.setRotation(rot);
                assertTrue(p.matchesSize(1, 1, 1));
            }
        }
    }

    // ─── getSizeDisplay ────────────────────────────────────────

    @Nested
    @DisplayName("getSizeDisplay 尺寸显示")
    class SizeDisplayTest {

        @Test
        @DisplayName("0° 显示原始尺寸")
        void displayZeroRotation() {
            RoomPreset p = buildPreset();
            p.setRotation(0);
            assertEquals("5 x 3 x 4", p.getSizeDisplay());
        }

        @Test
        @DisplayName("90° 显示交换后的尺寸")
        void displayNinetyRotation() {
            RoomPreset p = buildPreset();
            p.setRotation(90);
            assertEquals("4 x 3 x 5", p.getSizeDisplay());
        }

        @Test
        @DisplayName("180° 显示原始尺寸")
        void displayOneEightyRotation() {
            RoomPreset p = buildPreset();
            p.setRotation(180);
            assertEquals("5 x 3 x 4", p.getSizeDisplay());
        }

        @Test
        @DisplayName("270° 显示交换后的尺寸")
        void displayTwoSeventyRotation() {
            RoomPreset p = buildPreset();
            p.setRotation(270);
            assertEquals("4 x 3 x 5", p.getSizeDisplay());
        }
    }

    // ─── parseLine / encode 正确性 ────────────────────────────

    @Nested
    @DisplayName("SnapshotLine 解析与序列化")
    class SnapshotLineTest {

        @Test
        @DisplayName("正常格式行应正确解析")
        void parseValidLine() {
            String line = "2,1,3|minecraft:chest[facing=north]";
            RoomPreset.SnapshotLine sl = RoomPreset.parseLine(line);
            assertNotNull(sl);
            assertEquals(2, sl.x);
            assertEquals(1, sl.y);
            assertEquals(3, sl.z);
            assertEquals("minecraft:chest[facing=north]", sl.data);
        }

        @Test
        @DisplayName("encode 应还原为原始字符串")
        void encodeRoundTrip() {
            RoomPreset.SnapshotLine sl = new RoomPreset.SnapshotLine(0, 5, 2, "stone");
            assertEquals("0,5,2|stone", sl.encode());
        }

        @Test
        @DisplayName("缺少分隔符的行应返回 null")
        void parseMissingDelimiter() {
            assertNull(RoomPreset.parseLine("no-pipe-here"));
        }

        @Test
        @DisplayName("非数字坐标应返回 null")
        void parseNonNumericCoords() {
            assertNull(RoomPreset.parseLine("a,b,c|stone"));
            assertNull(RoomPreset.parseLine("1,2,3x|stone"));
        }

        @Test
        @DisplayName("坐标不足3个应返回 null")
        void parseInsufficientCoords() {
            assertNull(RoomPreset.parseLine("1,2|stone"));
            assertNull(RoomPreset.parseLine("1|stone"));
        }

        @Test
        @DisplayName("空字符串应返回 null")
        void parseEmptyString() {
            assertNull(RoomPreset.parseLine(""));
        }
    }

    // ─── 序列化/反序列化 ──────────────────────────────────────

    @Nested
    @DisplayName("YAML 序列化与反序列化")
    class SerializationTest {

        @Test
        @DisplayName("含旋转的预设序列化后应能完整恢复")
        void serializeDeserializeWithRotation() {
            RoomPreset original = buildPreset();
            original.setRotation(90);
            original.getBlocks().add("0,0,0|minecraft:stone");
            original.getBlocks().add("1,1,2|minecraft:oak_planks");
            original.getExtras().put("0:0:0", Map.of("type", "container", "items", List.of()));

            Map<String, Object> map = original.serialize();
            RoomPreset restored = RoomPreset.deserialize(map);

            assertEquals(original.getName(), restored.getName());
            assertEquals(original.getWorld(), restored.getWorld());
            assertEquals(original.getRotation(), restored.getRotation());
            assertEquals(original.getSizeX(), restored.getSizeX());
            assertEquals(original.getSizeY(), restored.getSizeY());
            assertEquals(original.getSizeZ(), restored.getSizeZ());
            assertEquals(original.getBlocks().size(), restored.getBlocks().size());
            assertEquals(original.getExtras().size(), restored.getExtras().size());
        }

        @Test
        @DisplayName("缺少 rotation 字段的旧预设应默认 rotation=0")
        void deserializeLegacyWithoutRotation() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", "world");
            map.put("sizeX", 5);
            map.put("sizeY", 3);
            map.put("sizeZ", 4);
            map.put("blocks", List.of("0,0,0|stone"));

            RoomPreset restored = RoomPreset.deserialize(map);
            assertEquals(0, restored.getRotation());
            assertEquals(5, restored.getSizeX());
            assertEquals(4, restored.getSizeZ());
        }

        @Test
        @DisplayName("copyWithRotation 不应修改原预设对象")
        void copyWithRotationDoesNotMutateOriginal() {
            RoomPreset original = buildPreset(); // rotation=0
            original.getBlocks().add("0,0,0|minecraft:stone");
            original.getExtras().put("0:0:0", Map.of("type", "sign", "lines", List.of("a")));

            RoomPreset copy = original.copyWithRotation(90);

            assertEquals(90, copy.getRotation());
            assertEquals(0, original.getRotation(), "原预设 rotation 不应被修改");
            assertEquals(original.getBlocks(), copy.getBlocks(), "副本应共享方块快照数据");
            assertEquals(original.getExtras(), copy.getExtras(), "副本应共享附加数据");
            assertEquals(original.getSizeX(), copy.getSizeX());
            assertEquals(original.getSizeZ(), copy.getSizeZ());

            // 副本的尺寸匹配逻辑应基于新旋转角度
            assertTrue(copy.matchesSize(4, 3, 5));
            assertTrue(original.matchesSize(5, 3, 4), "原预设的匹配逻辑不应变化");
        }
    }

    // ─── 性能测试 ─────────────────────────────────────────────

    @Nested
    @DisplayName("性能测试")
    class PerformanceTest {

        @Test
        @DisplayName("批量旋转坐标变换应在 1ms 内完成")
        void rotationPerformance() {
            RoomPreset p = buildPreset();
            p.setRotation(90);

            int iterations = 1_000_000;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                p.getRotatedRelCoords(i % 5, i % 3, i % 4);
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("旋转坐标变换 %d 次耗时: %.2f ms%n", iterations, elapsed / 1_000_000.0);
            assertTrue(elapsed < 1_000_000_000L, "旋转坐标变换超过 1 秒");
        }

        @Test
        @DisplayName("解析 10000 行方块快照应低于 50ms")
        void parseLinesPerformance() {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                lines.add(i + "," + (i % 10) + "," + (i % 8) + "|minecraft:stone");
            }

            long start = System.nanoTime();
            for (String line : lines) {
                RoomPreset.parseLine(line);
            }
            long elapsed = System.nanoTime() - start;
            System.out.printf("解析 10000 行快照耗时: %.2f ms%n", elapsed / 1_000_000.0);
            assertTrue(elapsed < 50_000_000L, "解析超过 50ms");
        }
    }
}
