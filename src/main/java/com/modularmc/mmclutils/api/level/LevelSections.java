package com.modularmc.mmclutils.api.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * 把维度的方块状态按区段交给 {@link SectionScan}。功能：
 * <ul>
 * <li>默认只读已加载的区块。未加载、高度范围外与空区块整段是虚空空气，全空气区段整段是空气，
 * 其余区段用调色板判断能否跳过，调试世界退回逐格读取</li>
 * <li>{@link #betweenClosed}/{@link #inRange}/{@link #withinManhattan}：对某维度直接做 {@link SectionScan} 的三种扫描，betweenClosed
 * 的角点顺序随意，
 * 同 {@code BlockPos.betweenClosed(a, b)}</li>
 * <li>通过 {@link Unloaded} 显式选择未加载区块的处理。传入 {@link Unloaded#LOAD} 时允许请求加载区块。</li>
 * </ul>
 * 实例在扫描期间复用当前区块和区段，只在拥有该维度的线程上使用。
 */
public final class LevelSections implements SectionScan.Sections<BlockState> {

    /** 指定区块扫描遇到未加载区块时的处理方式。 */
    public enum Unloaded {
        /** 查询时允许由原版加载所需区块。 */
        LOAD,
        /** 不加载区块，将未加载区域视为虚空空气。 */
        SKIP
    }

    private final Level level;
    private final boolean skipUnloaded;
    private final boolean debug;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int chunkX, chunkZ;
    private boolean chunkCached;
    private @Nullable LevelChunk chunk;
    private @Nullable LevelChunkSection section;
    private int baseX, baseY, baseZ;

    /** 创建只读取已加载区块的区段访问器。 */
    public LevelSections(Level level) {
        this(level, Unloaded.SKIP);
    }

    /**
     * 创建一次扫描使用的区段访问器。
     *
     * @param level    要读取的维度
     * @param unloaded 遇到未加载区块的处理方式
     */
    public LevelSections(Level level, Unloaded unloaded) {
        this.level = level;
        this.skipUnloaded = unloaded == Unloaded.SKIP;
        this.debug = level.isDebug();
    }

    /** 按原版坐标顺序扫描两角之间的闭区间，未加载区块视为虚空空气。 */
    public static int betweenClosed(Level level, BlockPos a, BlockPos b, Predicate<? super BlockState> match, int limit, Collection<? super BlockPos> out) {
        return betweenClosed(level, Unloaded.SKIP, a, b, match, (state, pos) -> true, limit, out);
    }

    /** 扫描闭区间并允许匹配条件读取坐标，未加载区块视为虚空空气。 */
    public static int betweenClosed(Level level, BlockPos a, BlockPos b, Predicate<? super BlockState> palette,
                                    SectionScan.CellTest<? super BlockState> test, int limit, Collection<? super BlockPos> out) {
        return betweenClosed(level, Unloaded.SKIP, a, b, palette, test, limit, out);
    }

    /** 按指定的区块加载策略扫描闭区间，匹配条件仅依赖方块状态。 */
    public static int betweenClosed(Level level, Unloaded unloaded, BlockPos a, BlockPos b, Predicate<? super BlockState> match, int limit,
                                    Collection<? super BlockPos> out) {
        return betweenClosed(level, unloaded, a, b, match, (state, pos) -> true, limit, out);
    }

    /**
     * 按原版遍历顺序扫描闭区间并追加匹配坐标。
     *
     * @param level    要扫描的维度
     * @param unloaded 遇到未加载区块的处理方式
     * @param a        闭区间的一个角点
     * @param b        另一角点，各轴顺序任意
     * @param palette  状态筛选条件，用于跳过无候选状态的区段
     * @param test     对候选方块执行的检查，回调内的坐标在下一次检查时可能被复用
     * @param limit    最多接受的匹配数，非正数时返回零
     * @param out      用于接收匹配坐标的可修改集合
     * @return 本次扫描接受的匹配数
     */
    public static int betweenClosed(Level level, Unloaded unloaded, BlockPos a, BlockPos b, Predicate<? super BlockState> palette,
                                    SectionScan.CellTest<? super BlockState> test, int limit, Collection<? super BlockPos> out) {
        return SectionScan.betweenClosed(new LevelSections(level, unloaded), Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()), palette, test, limit, out);
    }

    /** 在球形或盒形范围内匹配方块状态，未加载区块视为虚空空气。 */
    public static int inRange(Level level, BlockPos center, BlockRange range, Predicate<? super BlockState> match, int limit, Collection<? super BlockPos> out) {
        return inRange(level, Unloaded.SKIP, center, range, match, (state, pos) -> true, limit, out);
    }

    /** 在指定范围内按状态和坐标筛选，未加载区块视为虚空空气。 */
    public static int inRange(Level level, BlockPos center, BlockRange range, Predicate<? super BlockState> palette,
                              SectionScan.CellTest<? super BlockState> test, int limit, Collection<? super BlockPos> out) {
        return inRange(level, Unloaded.SKIP, center, range, palette, test, limit, out);
    }

    /** 在指定范围内按状态筛选，可显式选择区块加载策略。 */
    public static int inRange(Level level, Unloaded unloaded, BlockPos center, BlockRange range, Predicate<? super BlockState> match, int limit,
                              Collection<? super BlockPos> out) {
        return inRange(level, unloaded, center, range, match, (state, pos) -> true, limit, out);
    }

    /**
     * 在球形或盒形范围内追加匹配坐标。整段不与范围相交时跳过该区段。
     *
     * @param level    要扫描的维度
     * @param unloaded 遇到未加载区块的处理方式
     * @param center   范围中心
     * @param range    以中心为原点的扫描范围，空范围返回零
     * @param palette  状态筛选条件
     * @param test     对候选方块状态与坐标执行的检查
     * @param limit    最多接受的匹配数，非正数时返回零
     * @param out      用于接收匹配坐标的可修改集合
     * @return 本次扫描接受的匹配数
     */
    public static int inRange(Level level, Unloaded unloaded, BlockPos center, BlockRange range, Predicate<? super BlockState> palette,
                              SectionScan.CellTest<? super BlockState> test, int limit, Collection<? super BlockPos> out) {
        return SectionScan.inRange(new LevelSections(level, unloaded), center.getX(), center.getY(), center.getZ(), range, palette, test, limit, out);
    }

    /** 按原版曼哈顿距离顺序匹配状态，未加载区块视为虚空空气。 */
    public static int withinManhattan(Level level, BlockPos center, int rx, int ry, int rz, Predicate<? super BlockState> match, int limit,
                                      Collection<? super BlockPos> out) {
        return withinManhattan(level, Unloaded.SKIP, center, rx, ry, rz, match, (state, pos) -> true, limit, out);
    }

    /** 按曼哈顿距离顺序筛选状态和坐标，未加载区块视为虚空空气。 */
    public static int withinManhattan(Level level, BlockPos center, int rx, int ry, int rz, Predicate<? super BlockState> palette,
                                      SectionScan.CellTest<? super BlockState> test, int limit, Collection<? super BlockPos> out) {
        return withinManhattan(level, Unloaded.SKIP, center, rx, ry, rz, palette, test, limit, out);
    }

    /** 按曼哈顿距离顺序匹配状态，可显式选择区块加载策略。 */
    public static int withinManhattan(Level level, Unloaded unloaded, BlockPos center, int rx, int ry, int rz, Predicate<? super BlockState> match,
                                      int limit, Collection<? super BlockPos> out) {
        return withinManhattan(level, unloaded, center, rx, ry, rz, match, (state, pos) -> true, limit, out);
    }

    /**
     * 在三轴半径限定的盒形范围内，沿原版的曼哈顿距离顺序收集方块。
     *
     * @param level    要扫描的维度
     * @param unloaded 遇到未加载区块的处理方式
     * @param center   范围中心
     * @param rx       X 轴半径
     * @param ry       Y 轴半径
     * @param rz       Z 轴半径
     * @param palette  状态筛选条件
     * @param test     对候选方块状态与坐标执行的检查
     * @param limit    最多接受的匹配数，非正数时返回零
     * @param out      用于接收匹配坐标的可修改集合
     * @return 本次扫描接受的匹配数
     */
    public static int withinManhattan(Level level, Unloaded unloaded, BlockPos center, int rx, int ry, int rz, Predicate<? super BlockState> palette,
                                      SectionScan.CellTest<? super BlockState> test, int limit, Collection<? super BlockPos> out) {
        return SectionScan.withinManhattan(new LevelSections(level, unloaded), center.getX(), center.getY(), center.getZ(), rx, ry, rz, palette, test, limit, out);
    }

    @Override
    public @Nullable BlockState seek(int sectionX, int sectionY, int sectionZ) {
        if (level.isOutsideBuildHeight(sectionY << 4)) return Blocks.VOID_AIR.defaultBlockState();
        if (!chunkCached || chunkX != sectionX || chunkZ != sectionZ) {
            chunk = skipUnloaded ? LoadedChunks.chunk(level, sectionX, sectionZ) : level.getChunk(sectionX, sectionZ);
            chunkX = sectionX;
            chunkZ = sectionZ;
            chunkCached = true;
        }
        if (chunk == null || chunk instanceof EmptyLevelChunk) return Blocks.VOID_AIR.defaultBlockState();
        baseX = sectionX << 4;
        baseY = sectionY << 4;
        baseZ = sectionZ << 4;
        if (debug) {
            section = null;
            return null;
        }
        LevelChunkSection s = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
        if (s.hasOnlyAir()) return Blocks.AIR.defaultBlockState();
        section = s;
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean mayContain(Predicate<? super BlockState> palette) {
        return section == null || section.maybeHas((Predicate<BlockState>) palette);
    }

    @Override
    public BlockState get(int localX, int localY, int localZ) {
        if (section == null) return level.getBlockState(cursor.set(baseX + localX, baseY + localY, baseZ + localZ));
        return section.getBlockState(localX, localY, localZ);
    }
}
