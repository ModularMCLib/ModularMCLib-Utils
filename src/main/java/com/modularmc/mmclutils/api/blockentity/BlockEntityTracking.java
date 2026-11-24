package com.modularmc.mmclutils.api.blockentity;

import com.modularmc.mmclutils.api.level.LevelStore.SlotHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/** 维护方块实体与区块可见性变化对应的空间索引状态。 */
@ApiStatus.Internal
public final class BlockEntityTracking {

    static final Tracker<BlockEntity> TRACKER = new Tracker<>(new Tracker.Access<>() {

        @Override
        public @Nullable SlotHolder holder(BlockEntity be) {
            return be.getLevel() instanceof SlotHolder holder ? holder : null;
        }

        @Override
        public BlockPos pos(BlockEntity be) {
            return be.getBlockPos();
        }

        @Override
        public boolean live(BlockEntity be) {
            return !be.isRemoved();
        }

        @Override
        public int probedMask(BlockEntity be) {
            return ((TrackedBlockEntity) be).mmclutils$probedMask();
        }

        @Override
        public void setProbedMask(BlockEntity be, int mask) {
            ((TrackedBlockEntity) be).mmclutils$setProbedMask(mask);
        }
    });

    private BlockEntityTracking() {}

    /** 在实体加入其所在区块后登记可探测索引。 */
    public static void added(BlockEntity be) {
        TRACKER.added(be);
    }

    /** 在实体移除或替换前撤销其索引登记。 */
    public static void removed(BlockEntity be) {
        TRACKER.removed(be);
    }

    /**
     * 切换整个区块在空间索引中的可见性，并同步处理当前保存的方块实体。
     *
     * @param chunk   要更新的区块
     * @param visible 区块应参与索引查询时为 {@code true}
     */
    public static void setVisible(LevelChunk chunk, boolean visible) {
        TrackedChunk tracked = (TrackedChunk) chunk;
        if (tracked.mmclutils$hidden() != visible) return;
        tracked.mmclutils$setHidden(!visible);
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (visible) TRACKER.added(be);
            else TRACKER.removed(be);
        }
    }

    /** 方块实体在探测索引中使用的候选位标记。 */
    @ApiStatus.Internal
    public interface TrackedBlockEntity {

        /** 返回已通过探测并登记的索引位掩码。 */
        int mmclutils$probedMask();

        /** 将索引登记位掩码替换为指定值。 */
        void mmclutils$setProbedMask(int mask);
    }

    /** 区块从可见列表移除时用于暂停其中的方块实体登记。 */
    @ApiStatus.Internal
    public interface TrackedChunk {

        /** 查询区块当前是否已从索引隐藏。 */
        boolean mmclutils$hidden();

        /** 记录显隐切换，由索引同步方块实体。 */
        void mmclutils$setHidden(boolean hidden);
    }
}
