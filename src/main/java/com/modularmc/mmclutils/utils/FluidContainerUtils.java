package com.modularmc.mmclutils.utils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.CarriedSlotWrapper;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

/** 通过 NeoForge 的物品访问修改容器，流体与原物品的替换在同一个事务中完成。 */
public final class FluidContainerUtils {

    private FluidContainerUtils() {}

    /**
     * 将指定储罐中的流体装入单个物品容器。
     *
     * @param item 持有待装填容器的物品访问器
     * @param source 流体来源
     * @param tank 来源中的储罐索引
     * @param maximum 最多转移的毫桶数
     * @param parent 可选的外层事务
     * @return 实际装入容器的毫桶数
     */
    public static int fill(ItemAccess item, ResourceHandler<FluidResource> source, int tank, int maximum,
                           @Nullable TransactionContext parent) {
        var access = item.oneByOne();
        var target = access.getCapability(Capabilities.Fluid.ITEM);
        return target == null ? 0 : InventoryUtils.move(source, tank, target, maximum, parent);
    }

    /**
     * 将单个物品容器中的流体排入指定储罐。
     *
     * @param item 持有待排空容器的物品访问器
     * @param tank 目标中的储罐索引
     * @param target 流体目标
     * @param maximum 最多转移的毫桶数
     * @param parent 可选的外层事务
     * @return 实际排入储罐的毫桶数
     */
    public static int drain(ItemAccess item, int tank, ResourceHandler<FluidResource> target, int maximum,
                            @Nullable TransactionContext parent) {
        var access = item.oneByOne();
        var source = access.getCapability(Capabilities.Fluid.ITEM);
        return source == null ? 0 : InventoryUtils.move(source, tank, target, maximum, parent);
    }

    /**
     * 在服务端处理菜单光标上的容器。普通点击处理一个，批量点击最多处理整组。
     *
     * @param player 发起操作的玩家
     * @param handler 目标流体处理器
     * @param tank 要操作的储罐索引
     * @param batch 是否处理光标堆叠中的全部容器
     * @param allowFill 是否允许将储罐流体装入容器
     * @param allowDrain 是否允许将容器流体排入储罐
     * @return 成功操作的容器数量
     */
    public static int click(ServerPlayer player, ResourceHandler<FluidResource> handler, int tank,
                            boolean batch, boolean allowFill, boolean allowDrain) {
        return click(player, player.containerMenu, handler, tank, batch ? Integer.MAX_VALUE : 1, allowFill, allowDrain);
    }

    /**
     * 只操作指定储罐。装入容器成功后按同一方向继续，无法装入时可尝试倒空。
     * 光标空出后放回一组结果，其余交给玩家库存。声音和菜单同步发生在提交后。
     *
     * @param player 发起操作的玩家
     * @param menu 持有光标物品的菜单
     * @param handler 目标流体处理器
     * @param tank 要操作的储罐索引
     * @param maximumContainers 最多处理的容器数量
     * @param allowFill 是否允许将储罐流体装入容器
     * @param allowDrain 是否允许将容器流体排入储罐
     * @return 成功装入或倒空的容器数量
     * @throws IllegalArgumentException 容器数量上限为负数
     */
    public static int click(ServerPlayer player, AbstractContainerMenu menu, ResourceHandler<FluidResource> handler,
                            int tank, int maximumContainers, boolean allowFill, boolean allowDrain) {
        if (maximumContainers < 0) throw new IllegalArgumentException("容器数量应为非负数");
        var cursor = CarriedSlotWrapper.of(menu);
        int count = Math.min(maximumContainers, cursor.getAmountAsInt(0));
        if (count == 0 || (!allowFill && !allowDrain)) return 0;

        var tankAccess = RangedResourceHandler.ofSingleIndex(handler, tank);
        BatchResult result;
        boolean fill = allowFill;
        try (var transaction = Transaction.openRoot()) {
            result = fill ? transferContainers(cursor, tankAccess, count, true, player, transaction) : null;
            if (result == null && allowDrain) {
                fill = false;
                result = transferContainers(cursor, tankAccess, count, false, player, transaction);
            }
            if (result == null) return 0;
            transaction.commit();
        }

        var fluid = result.fluid();
        var stack = fluid.toStack(FluidType.BUCKET_VOLUME);
        var sound = fluid.getFluidType().getSound(stack, fill ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
        if (sound == null) sound = fill ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY;
        player.level().playSound(null, player, sound, SoundSource.BLOCKS, 1, 1);
        menu.broadcastChanges();
        return result.containers();
    }

    private static @Nullable BatchResult transferContainers(ResourceHandler<ItemResource> cursor, ResourceHandler<FluidResource> tank,
                                                            int count, boolean fill, ServerPlayer player, TransactionContext parent) {
        var original = cursor.getResource(0);
        var single = new ItemStacksResourceHandler(1);
        var access = ItemAccess.forHandlerIndexStrict(single, 0);
        var results = new Object2IntLinkedOpenHashMap<ItemResource>();
        FluidResource firstFluid = FluidResource.EMPTY;
        int containers = 0;
        for (; containers < count; containers++) {
            // 每次仅从光标取原始容器，产物在本批结束后回填。
            try (var attempt = Transaction.open(parent)) {
                if (cursor.extract(0, original, 1, attempt) != 1) break;
                if (single.insert(0, original, 1, attempt) != 1) break;
                var container = access.getCapability(Capabilities.Fluid.ITEM);
                if (container == null) break;
                var moved = ResourceHandlerUtil.moveFirst(fill ? tank : container, fill ? container : tank,
                        resource -> true, Integer.MAX_VALUE, attempt);
                if (moved == null) break;

                var product = single.getResource(0);
                if (!product.isEmpty()) {
                    int amount = single.extract(0, product, single.getAmountAsInt(0), attempt);
                    results.addTo(product, amount);
                }
                attempt.commit();
                if (firstFluid.isEmpty()) firstFluid = moved.resource();
            }
        }
        if (containers == 0) return null;

        if (!results.isEmpty() && cursor.getResource(0).isEmpty()) {
            ItemResource last = results.lastKey();
            int inserted = cursor.insert(0, last, results.getInt(last), parent);
            results.addTo(last, -inserted);
        }
        var inventory = PlayerInventoryWrapper.of(player);
        for (var entry : results.object2IntEntrySet()) {
            int amount = entry.getIntValue();
            if (amount > 0) inventory.placeItemBackInInventory(entry.getKey(), amount, parent);
        }
        return new BatchResult(containers, firstFluid);
    }

    private record BatchResult(int containers, FluidResource fluid) {}
}
