package com.modularmc.mmclutils.api.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * 只读已加载完成的区块，绝不触发加载，也不阻塞主线程。功能：
 * <ul>
 * <li>{@link #chunk}：取已加载的区块，没有就返回 null；服务端在主线程上同原版 getChunkNow，在其它线程上读区块表的可见副本，并用最近 4 个区块的缓存加速</li>
 * <li>{@link #isLoaded}：该格所在的区块是否已加载</li>
 * <li>{@link #blockState}：未加载或在高度范围外时返回虚空空气</li>
 * <li>{@link #blockEntity}：未加载、没有或已移除时返回 null，不创建待加载的方块实体</li>
 * <li>任何线程都能调用；在非主线程上读到的是某一时刻的值，可能和主线程的写入交错，需要强一致时应在主线程上取</li>
 * </ul>
 */
public final class LoadedChunks {

    /** 区块未加载或位置超出建造高度时使用的虚空空气状态。 */
    public static final BlockState UNLOADED = Blocks.VOID_AIR.defaultBlockState();

    private LoadedChunks() {}

    /** 按区块坐标查询已经存在的区块，缺失时返回空值。 */
    public static @Nullable LevelChunk chunk(Level level, int chunkX, int chunkZ) {
        ChunkSource source = level.getChunkSource();
        if (source instanceof Source loaded) return loaded.mmclutils$loadedChunk(chunkX, chunkZ);
        return source.getChunk(chunkX, chunkZ, false);
    }

    /** 将方块位置换算成区块坐标，查询已加载区块。缺失时返回空值。 */
    public static @Nullable LevelChunk chunk(Level level, BlockPos pos) {
        return chunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** 检查该位置所属区块是否已加载。此方法只检查区块平面坐标。 */
    public static boolean isLoaded(Level level, BlockPos pos) {
        return chunk(level, pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /** 读取已加载区块中的方块。位置超出建造高度或区块缺失时返回 {@link #UNLOADED}。 */
    public static BlockState blockState(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos.getY())) return UNLOADED;
        LevelChunk chunk = chunk(level, pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? UNLOADED : chunk.getBlockState(pos);
    }

    /** 读取已经创建且仍有效的方块实体。区块、条目缺失或实体已经移除时返回空值。 */
    public static @Nullable BlockEntity blockEntity(Level level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        BlockEntity be = chunk.getBlockEntities().get(pos);
        return be == null || be.isRemoved() ? null : be;
    }

    /** 区块源提供的只读查询。未加载或尚未完成时返回空值。 */
    @ApiStatus.Internal
    public interface Source {

        /** 查询指定区块坐标，调用期间不会加载区块。 */
        @Nullable
        LevelChunk mmclutils$loadedChunk(int chunkX, int chunkZ);
    }

    /**
     * 最近取过的 4 个区块的缓存，供多个线程无锁共用。功能：
     * <ul>
     * <li>{@link #get}：按打包坐标找，命中返回缓存的值，否则返回 null</li>
     * <li>{@link #put}：轮流覆盖 4 个槽位；坐标和值放在同一个不可变对象里，并发读写时只会读到某一次完整写入的坐标和值，不会读到错位的一对</li>
     * <li>{@link #clear}：清空全部槽位</li>
     * </ul>
     */
    @ApiStatus.Internal
    public static final class RecentChunks<C> {

        private record Slot<C>(long key, C value) {}

        @SuppressWarnings("unchecked")
        private final Slot<C>[] slots = new Slot[4];
        private int next;

        /** 按打包的区块坐标查询，缓存未命中时返回空值。 */
        public @Nullable C get(long key) {
            Slot<C>[] s = slots;
            for (int i = 0; i < 4; i++) {
                Slot<C> slot = s[i];
                if (slot != null && slot.key == key) return slot.value;
            }
            return null;
        }

        /** 覆盖轮换位置的键和值。并发读者始终从同一个缓存条目读取键和值。 */
        public void put(long key, C value) {
            int i = next;
            next = (i + 1) & 3;
            slots[i] = new Slot<>(key, value);
        }

        /** 清空所有缓存条目，下一次查询将重新读取区块源。 */
        public void clear() {
            Arrays.fill(slots, null);
        }
    }
}
