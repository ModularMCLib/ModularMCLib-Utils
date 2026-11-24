package com.modularmc.mmclutils.api.blockentity;

import com.modularmc.mmclutils.api.level.BlockIndex;
import com.modularmc.mmclutils.api.level.BlockRange;
import com.modularmc.mmclutils.api.level.LevelStore.SlotHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 按类型维护方块实体索引。区块放入、替换、移除方块实体以及区块卸载时会自动更新。
 * 必须在模组加载阶段创建（声明成静态常量，并在模组构造时触发所在类初始化），维度开始加载方块实体后再创建会抛异常。功能：
 * <ul>
 * <li>{@link #create(Class)}：收录运行时类是该类型子类型的方块实体；{@link #create(Class, Predicate)}：另在登记时判一次条件，只收录通过的</li>
 * <li>{@link #all()}：全部方块实体共用的一份索引，供只能逐个判断能力的查找和 {@link ProbedBlockEntityIndex} 的待探测部分使用；第一次调用时创建，同样必须在加载阶段调用</li>
 * <li>{@link #query}/{@link #forEach}/{@link #findAny}/{@link #findNearest}：按 {@link BlockRange} 查某维度范围内的方块实体，语义同
 * {@link BlockIndex}</li>
 * <li>{@link #findAny(Level, Predicate)}/{@link #forEach(Level, Consumer)}：遍历某维度全部收录的方块实体</li>
 * <li>{@link #get}/{@link #size}/{@link #version}：按坐标取、数量、内容变化计数</li>
 * <li>索引存放在维度实例上，随维度回收；只在拥有该维度的线程上查询</li>
 * </ul>
 *
 * @param <T> 收录的方块实体类型或由方块实体实现的接口
 */
public final class BlockEntityIndex<T> {

    private final TypedSink<BlockEntity, T> sink;

    private BlockEntityIndex(TypedSink<BlockEntity, T> sink) {
        this.sink = sink;
    }

    /** 为指定类或接口建立索引，兼容实现该类型的方块实体。 */
    public static <T> BlockEntityIndex<T> create(Class<T> type) {
        return create(type, null);
    }

    /**
     * 在模组加载阶段创建一个按类型自动登记的索引。
     *
     * @param <T>   收录的方块实体类型或接口
     * @param type  用于筛选方块实体的运行时类型
     * @param admit 只在方块实体登记时执行的附加条件，为空时接纳全部对应类型
     * @return 由加载区块维护的索引
     * @throws IllegalStateException 已开始跟踪方块实体
     */
    public static <T> BlockEntityIndex<T> create(Class<T> type, @Nullable Predicate<? super T> admit) {
        return new BlockEntityIndex<>(BlockEntityTracking.TRACKER.register(new TypedSink<>(type, admit)));
    }

    /** 返回包含所有已登记原版方块实体的共享索引。 */
    public static BlockEntityIndex<BlockEntity> all() {
        return All.INDEX;
    }

    private static final class All {

        static final BlockEntityIndex<BlockEntity> INDEX = create(BlockEntity.class);
    }

    TypedSink<BlockEntity, T> sink() {
        return sink;
    }

    private @Nullable BlockIndex<T> index(Level level) {
        return sink.index((SlotHolder) level);
    }

    /** 向集合追加范围内通过筛选的方块实体。筛选可为空，返回集合实际增加的数量。 */
    public int query(Level level, int x, int y, int z, BlockRange range, @Nullable Predicate<? super T> filter, Collection<? super T> out) {
        BlockIndex<T> index = index(level);
        return index == null ? 0 : index.query(x, y, z, range, filter, out);
    }

    /** 使用方块位置作为查询中心，并将结果加入调用方提供的集合。 */
    public int query(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super T> filter, Collection<? super T> out) {
        return query(level, center.getX(), center.getY(), center.getZ(), range, filter, out);
    }

    /** 在世界线程访问范围内的匹配实体。回调不应修改正在遍历的索引。 */
    public void forEach(Level level, int x, int y, int z, BlockRange range, @Nullable Predicate<? super T> filter, Consumer<? super T> action) {
        BlockIndex<T> index = index(level);
        if (index != null) index.query(x, y, z, range, filter, action);
    }

    /** 以方块位置作为中心连续处理匹配值。 */
    public void forEach(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super T> filter, Consumer<? super T> action) {
        forEach(level, center.getX(), center.getY(), center.getZ(), range, filter, action);
    }

    /** 查找范围内任一符合条件的实体，未命中时返回空值。 */
    public @Nullable T findAny(Level level, int x, int y, int z, BlockRange range, @Nullable Predicate<? super T> filter) {
        BlockIndex<T> index = index(level);
        return index == null ? null : index.findAny(x, y, z, range, filter);
    }

    /** 以方块位置为中心查找任一匹配实体。 */
    public @Nullable T findAny(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super T> filter) {
        return findAny(level, center.getX(), center.getY(), center.getZ(), range, filter);
    }

    /** 按平方欧氏距离查找最近实体，未命中时返回空值。 */
    public @Nullable T findNearest(Level level, int x, int y, int z, BlockRange range, @Nullable Predicate<? super T> filter) {
        BlockIndex<T> index = index(level);
        return index == null ? null : index.findNearest(x, y, z, range, filter);
    }

    /** 以方块位置为中心查找最近的匹配实体。 */
    public @Nullable T findNearest(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super T> filter) {
        return findNearest(level, center.getX(), center.getY(), center.getZ(), range, filter);
    }

    /** 在当前维度的全部已索引实体中查找任意匹配项。 */
    public @Nullable T findAny(Level level, Predicate<? super T> filter) {
        BlockIndex<T> index = index(level);
        return index == null ? null : index.findAny(filter);
    }

    /** 遍历该维度已索引的实体，回调中不要修改索引。 */
    public void forEach(Level level, Consumer<? super T> action) {
        BlockIndex<T> index = index(level);
        if (index != null) index.forEach(action);
    }

    /** 按位置查询当前索引中的实体。条目缺失时返回空值。 */
    public @Nullable T get(Level level, BlockPos pos) {
        BlockIndex<T> index = index(level);
        return index == null ? null : index.get(pos);
    }

    /** 读取此维度内保存的条目总数。 */
    public int size(Level level) {
        BlockIndex<T> index = index(level);
        return index == null ? 0 : index.size();
    }

    /** 读取本维度索引的修改版本，索引尚未创建时返回零。 */
    public int version(Level level) {
        BlockIndex<T> index = index(level);
        return index == null ? 0 : index.version();
    }
}
