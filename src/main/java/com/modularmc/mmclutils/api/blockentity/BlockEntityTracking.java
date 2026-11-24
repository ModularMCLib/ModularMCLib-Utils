package com.modularmc.mmclutils.api.blockentity;

import com.modularmc.mmclutils.api.level.LevelStore.SlotHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

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

    public static void added(BlockEntity be) {
        TRACKER.added(be);
    }

    public static void removed(BlockEntity be) {
        TRACKER.removed(be);
    }

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

        /** 返回当前索引登记的位。 */
        int mmclutils$probedMask();

        /** 更新该方块实体的索引登记。 */
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
