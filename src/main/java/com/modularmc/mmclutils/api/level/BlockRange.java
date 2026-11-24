package com.modularmc.mmclutils.api.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.AABB;

/**
 * 以某个方块为中心的查询范围，供 {@link BlockOctree} 和 {@link BlockIndex} 使用。功能：
 * <ul>
 * <li>{@link #sphere(int)}：球形，方块坐标差的平方和 ≤ r²；{@link #sphereSq(long)} 直接给平方半径，
 * 例如原版 {@code closerThan} 的"严格小于 r"写成 {@code sphereSq(r * r - 1)}</li>
 * <li>{@link #box(int)} / {@link #box(int, int, int)}：立方体或长方体，各轴坐标差的绝对值 ≤ 对应半径</li>
 * <li>{@link #contains(int, int, int)}：判断坐标差是否落在范围内</li>
 * <li>{@link #reaches(long, long, long)}：给定三轴的最小距离，判断一个区域是否可能与范围相交，也用于判断区域是否整体落在范围内</li>
 * <li>半径为负的范围是空范围，什么都不包含</li>
 * <li>0~64 的球形与立方体半径返回预建的共享实例，不分配内存；实例不可变，可放心复用</li>
 * </ul>
 */
public final class BlockRange {

    /** 范围使用的几何形状。 */
    public enum Shape {
        /** 三轴平方距离之和不超过半径平方。 */
        SPHERE,
        /** 三轴距离分别不超过各自半径。 */
        BOX
    }

    private static final int CACHED = 64;
    private static final BlockRange EMPTY_SPHERE = new BlockRange(Shape.SPHERE, -1, -1, -1, -1);
    private static final BlockRange EMPTY_BOX = new BlockRange(Shape.BOX, -1, -1, -1, -1);
    private static final BlockRange[] SPHERES = new BlockRange[CACHED + 1];
    private static final BlockRange[] BOXES = new BlockRange[CACHED + 1];

    static {
        for (int r = 0; r <= CACHED; r++) {
            SPHERES[r] = new BlockRange(Shape.SPHERE, r, r, r, (long) r * r);
            BOXES[r] = new BlockRange(Shape.BOX, r, r, r, -1);
        }
    }

    private final Shape shape;
    private final int rx, ry, rz;
    private final long radiusSq;

    private BlockRange(Shape shape, int rx, int ry, int rz, long radiusSq) {
        this.shape = shape;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.radiusSq = radiusSq;
    }

    /** 以方块为单位创建闭球范围。负半径表示空范围。 */
    public static BlockRange sphere(int radius) {
        if (radius < 0) return EMPTY_SPHERE;
        if (radius <= CACHED) return SPHERES[radius];
        return sphereSq((long) radius * radius);
    }

    /** 直接使用平方半径，便于表示严格距离条件。负值表示空范围，各轴外包半径最多为 {@link Integer#MAX_VALUE}。 */
    public static BlockRange sphereSq(long radiusSq) {
        if (radiusSq < 0) return EMPTY_SPHERE;
        int r = (int) Math.sqrt((double) radiusSq);
        while ((long) r * r > radiusSq) r--;
        while (r < Integer.MAX_VALUE && (r + 1L) * (r + 1L) <= radiusSq) r++;
        return new BlockRange(Shape.SPHERE, r, r, r, radiusSq);
    }

    /** 创建三轴半径相同的闭立方体。负半径表示空范围。 */
    public static BlockRange box(int radius) {
        return box(radius, radius, radius);
    }

    /** 分别指定三轴半径，任一轴为负时返回空范围。 */
    public static BlockRange box(int rx, int ry, int rz) {
        if (rx < 0 || ry < 0 || rz < 0) return EMPTY_BOX;
        if (rx == ry && ry == rz && rx <= CACHED) return BOXES[rx];
        return new BlockRange(Shape.BOX, rx, ry, rz, -1);
    }

    /**
     * 以方块中心为基准创建区块覆盖范围，水平方向每增加一圈就在两侧各扩展一个区块宽度。
     *
     * @param center      原版压缩坐标可表示的中心方块
     * @param chunkRadius 水平方向的区块圈数，零圈覆盖一个区块宽度
     * @param below       向下覆盖的方块数
     * @param above       向上覆盖的方块数
     * @return 可用于位置判断、区块索引和渲染的范围
     * @throws IllegalArgumentException 圈数或任一高度为负
     */
    public static ChunkCoverage chunkCoverage(BlockPos center, int chunkRadius, int below, int above) {
        return new ChunkCoverage(center.asLong(), chunkRadius, below, above);
    }

    /** 返回相交测试所使用的形状。 */
    public Shape shape() {
        return shape;
    }

    /** 返回外包范围在 X 轴的半径，空范围时返回负值。 */
    public int radiusX() {
        return rx;
    }

    /** 返回外包范围在 Y 轴的半径，空范围时返回负值。 */
    public int radiusY() {
        return ry;
    }

    /** 返回外包范围在 Z 轴的半径，空范围时返回负值。 */
    public int radiusZ() {
        return rz;
    }

    /** 返回球形范围的平方半径。盒形或空范围返回 {@code -1}。 */
    public long radiusSq() {
        return radiusSq;
    }

    /** 判断范围是否排除所有位置，零半径仍包含中心。 */
    public boolean isEmpty() {
        return rx < 0;
    }

    /** 判断带符号的三轴坐标差是否落在范围内，边界计入。 */
    public boolean contains(int dx, int dy, int dz) {
        return reaches(Math.abs((long) dx), Math.abs((long) dy), Math.abs((long) dz));
    }

    /** 用三个非负的轴向最小距离判断范围是否与区域相交。 */
    public boolean reaches(long ax, long ay, long az) {
        if (rx < 0) return false;
        if (shape == Shape.BOX) return ax <= rx && ay <= ry && az <= rz;
        if (ax > rx || ay > ry || az > rz) return false;
        return az * az <= radiusSq - ax * ax - ay * ay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockRange other)) return false;
        return shape == other.shape && rx == other.rx && ry == other.ry && rz == other.rz && radiusSq == other.radiusSq;
    }

    @Override
    public int hashCode() {
        int h = shape.hashCode();
        h = 31 * h + rx;
        h = 31 * h + ry;
        h = 31 * h + rz;
        return 31 * h + Long.hashCode(radiusSq);
    }

    @Override
    public String toString() {
        return shape == Shape.SPHERE ? "BlockRange.sphereSq(" + radiusSq + ")" : "BlockRange.box(" + rx + ", " + ry + ", " + rz + ")";
    }

    /**
     * 以方块中心对齐的水平正方形和不对称的竖直范围。可在线程之间共享。
     *
     * @param center      用 {@link BlockPos#asLong()} 编码的中心方块
     * @param chunkRadius 从中心向外扩展的区块圈数，须非负
     * @param below       从中心方块向下覆盖的方块数，须非负
     * @param above       从中心方块向上覆盖的方块数，须非负
     */
    public record ChunkCoverage(long center, int chunkRadius, int below, int above) {

        /** @throws IllegalArgumentException 圈数或任一高度为负 */
        public ChunkCoverage {
            if (chunkRadius < 0 || below < 0 || above < 0) {
                throw new IllegalArgumentException("区块圈数和覆盖高度须非负");
            }
        }

        /** 判断方块中心是否在范围内，水平最小边界计入，最大边界排除。 */
        public boolean contains(BlockPos pos) {
            return contains(pos.getX(), pos.getY(), pos.getZ());
        }

        /** 判断原版压缩坐标对应的方块中心是否在范围内。 */
        public boolean contains(long pos) {
            return contains(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
        }

        private boolean contains(int x, int y, int z) {
            long halfWidth = halfWidth();
            long dx = (long) x - BlockPos.getX(center);
            long dy = (long) y - BlockPos.getY(center);
            long dz = (long) z - BlockPos.getZ(center);
            return dx >= -halfWidth && dx < halfWidth && dz >= -halfWidth && dz < halfWidth && dy >= -below && dy <= above;
        }

        /** 返回未裁切的世界坐标包围盒。最大高度位于最高一层方块的顶面。 */
        public AABB bounds() {
            int y = BlockPos.getY(center);
            return bounds((double) y - below, (double) y + above + 1);
        }

        /** 将包围盒裁切到世界的建造高度。完全落在世界外时，返回贴在相邻边界的零高包围盒。 */
        public AABB bounds(LevelHeightAccessor height) {
            int y = BlockPos.getY(center);
            int minY = height.getMinY();
            double endY = (double) height.getMaxY() + 1;
            return bounds(Math.clamp((double) y - below, minY, endY), Math.clamp((double) y + above + 1, minY, endY));
        }

        /** 返回包围盒相交的最小区块 X 坐标。 */
        public int minChunkX() {
            return chunkAt(BlockPos.getX(center) - halfWidth());
        }

        /** 返回包围盒相交的最大区块 X 坐标。 */
        public int maxChunkX() {
            return chunkAt(BlockPos.getX(center) + halfWidth());
        }

        /** 返回包围盒相交的最小区块 Z 坐标。 */
        public int minChunkZ() {
            return chunkAt(BlockPos.getZ(center) - halfWidth());
        }

        /** 返回包围盒相交的最大区块 Z 坐标。 */
        public int maxChunkZ() {
            return chunkAt(BlockPos.getZ(center) + halfWidth());
        }

        /** 返回按覆盖宽度折算的区块数，未计入边缘部分相交的区块。超出 {@code long} 时抛出算术异常。 */
        public long nominalChunkCount() {
            long diameter = 2L * chunkRadius + 1;
            return Math.multiplyExact(diameter, diameter);
        }

        private long halfWidth() {
            return ((long) chunkRadius << SectionPos.SECTION_BITS) + SectionPos.SECTION_HALF_SIZE;
        }

        private static int chunkAt(long block) {
            return Math.toIntExact(block >> SectionPos.SECTION_BITS);
        }

        private AABB bounds(double minY, double maxY) {
            double x = BlockPos.getX(center) + 0.5;
            double z = BlockPos.getZ(center) + 0.5;
            long halfWidth = halfWidth();
            return new AABB(x - halfWidth, minY, z - halfWidth, x + halfWidth, maxY, z + halfWidth);
        }
    }
}
