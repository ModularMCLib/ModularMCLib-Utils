package com.modularmc.mmclutils.api.blockentity;

import com.modularmc.mmclutils.api.level.BlockRange;
import com.modularmc.mmclutils.api.level.LevelStore.SlotHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 按能力自动维护的方块实体索引：分类器按类把方块实体分为不收录、候选（确定提供）、待探测（可能提供）三种，
 * 待探测的不另外存放，查询第一次覆盖到所在区段时从 {@link BlockEntityIndex#all()} 取出来用探测条件判一次，结果按实例记在方块实体上，
 * 之后只走候选。适合能力挂在任意方块实体上、逐个判断很贵的查找；能力会随时变化的，查询结果使用前仍要再取一次能力。
 * 创建时机要求同 {@link BlockEntityIndex}，最多 32 个。功能：
 * <ul>
 * <li>{@link #create(Function, Predicate)}：分类器每个类只调用一次，返回 null
 * 视为不收录；{@link #create(Function, Predicate, Predicate)}：另在登记时判一次条件</li>
 * <li>{@link #query}/{@link #forEach}/{@link #findAny}：先探测范围内还没探测过的方块实体，再按 {@link BlockRange} 查候选</li>
 * <li>{@link #candidateCount}/{@link #sweptSectionCount}：某维度候选数与已扫过的区段数</li>
 * </ul>
 */
public final class ProbedBlockEntityIndex {

    /** 按方块实体类型为自动索引选择的处理方式。 */
    public enum Kind {
        /** 不把此类型登记到索引。 */
        NONE,
        /** 登记为可直接参与空间查询的候选。 */
        CANDIDATE,
        /** 仅在查询覆盖所在区段时按需执行探测条件。 */
        PROBE
    }

    private final ProbedSink<BlockEntity> sink;

    private ProbedBlockEntityIndex(ProbedSink<BlockEntity> sink) {
        this.sink = sink;
    }

    /**
     * 创建按类型分类并按需探测的方块实体索引。
     *
     * @param classifier 将方块实体类型分类为不收录、候选或按需探测
     * @param probe      匹配查询触达的待探测实体时执行的能力检查
     * @return 新的索引，登记在创建后出现的索引条目上
     */
    public static ProbedBlockEntityIndex create(Function<Class<?>, Kind> classifier, Predicate<? super BlockEntity> probe) {
        return create(classifier, probe, null);
    }

    /**
     * 创建具有额外登记过滤条件的按需探测索引。
     *
     * @param classifier 将方块实体类型分类为不收录、候选或按需探测
     * @param probe      查询触达待探测实体时的能力检查
     * @param admit      登记或实体状态变化时的候选过滤条件，可以为空
     * @return 新的按需探测索引
     */
    public static ProbedBlockEntityIndex create(Function<Class<?>, Kind> classifier, Predicate<? super BlockEntity> probe,
                                                @Nullable Predicate<? super BlockEntity> admit) {
        Tracker<BlockEntity> tracker = BlockEntityTracking.TRACKER;
        TypedSink<BlockEntity, BlockEntity> all = BlockEntityIndex.all().sink();
        return new ProbedBlockEntityIndex(tracker.register(new ProbedSink<>(tracker, all, classifier, probe, admit)));
    }

    /**
     * 在指定范围查找候选，并把匹配项追加至输出集合。
     *
     * @param level  要查询的维度
     * @param center 范围中心
     * @param range  球形或长方体范围
     * @param filter 可选的最终结果过滤条件
     * @param out    接收匹配方块实体的集合
     * @return 追加到集合中的条目数
     */
    public int query(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super BlockEntity> filter, Collection<? super BlockEntity> out) {
        return sink.query((SlotHolder) level, center.getX(), center.getY(), center.getZ(), range, filter, out);
    }

    /** 对范围内匹配的方块实体依次执行操作。 */
    public void forEach(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super BlockEntity> filter, Consumer<? super BlockEntity> action) {
        sink.forEach((SlotHolder) level, center.getX(), center.getY(), center.getZ(), range, filter, action);
    }

    /** 返回范围内第一个满足过滤条件的方块实体，没有匹配项时返回空值。 */
    public @Nullable BlockEntity findAny(Level level, BlockPos center, BlockRange range, @Nullable Predicate<? super BlockEntity> filter) {
        return sink.findAny((SlotHolder) level, center.getX(), center.getY(), center.getZ(), range, filter);
    }

    /** 返回此维度当前已登记的候选实体数量。 */
    public int candidateCount(Level level) {
        return sink.candidateCount((SlotHolder) level);
    }

    /** 返回此维度已执行过按需探测的区段数量。 */
    public int sweptSectionCount(Level level) {
        return sink.sweptSectionCount((SlotHolder) level);
    }
}
