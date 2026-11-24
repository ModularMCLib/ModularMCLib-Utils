package com.modularmc.mmclutils.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.function.BooleanSupplier;

/**
 * 为固定原点周围的六个方块保留 NeoForge 能力缓存。
 *
 * @param <T> 能力类型
 */
public final class AdjacentBlockCapabilityCache<T> {

    private final BlockCapability<T, Direction> capability;
    private final ServerLevel level;
    private final BlockPos origin;
    private final BooleanSupplier isValid;
    private final EnumMap<Direction, BlockCapabilityCache<T, Direction>> caches = new EnumMap<>(Direction.class);

    /**
     * 按需建立各方向缓存。失效监听器随所属对象的有效期释放。
     *
     * @param capability 查询的方块能力
     * @param level      目标方块所在的服务端维度
     * @param origin     查询原点
     * @param isValid    所属对象的存活判定
     */
    public AdjacentBlockCapabilityCache(
                                        BlockCapability<T, Direction> capability,
                                        ServerLevel level,
                                        BlockPos origin,
                                        BooleanSupplier isValid) {
        this.capability = capability;
        this.level = level;
        this.origin = origin.immutable();
        this.isValid = isValid;
    }

    /**
     * 查询邻接方块朝向原点的面所提供的能力。
     *
     * @param side 从原点指向目标的方向
     * @return 当前能力，目标未暴露该能力时返回 {@code null}
     */
    @Nullable
    public T get(Direction side) {
        return this.caches.computeIfAbsent(side, this::createCache).getCapability();
    }

    /**
     * 按传入的方向顺序查询能力。
     *
     * @param sides 参与查询的方向
     * @return 当前已暴露能力的不可变列表
     */
    public ObjectList<T> getAll(Iterable<Direction> sides) {
        ObjectList<T> resolved = new ObjectArrayList<>();
        for (Direction side : sides) {
            T value = get(side);
            if (value != null) {
                resolved.add(value);
            }
        }
        return resolved.isEmpty() ? ObjectList.of() : ObjectLists.unmodifiable(resolved);
    }

    /**
     * 查询能力并保留每项对应的方向。
     *
     * @param sides 参与查询的方向
     * @return 按枚举顺序返回方向与能力的独立映射，修改结果不会影响缓存
     */
    public EnumMap<Direction, T> getAllBySide(Iterable<Direction> sides) {
        EnumMap<Direction, T> resolved = new EnumMap<>(Direction.class);
        for (Direction side : sides) {
            T value = get(side);
            if (value != null) {
                resolved.put(side, value);
            }
        }
        return resolved;
    }

    private BlockCapabilityCache<T, Direction> createCache(Direction side) {
        return BlockCapabilityCache.create(
                this.capability,
                this.level,
                this.origin.relative(side),
                side.getOpposite(),
                this.isValid,
                () -> {});
    }
}
