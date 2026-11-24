package com.modularmc.mmclutils.utils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** 通过 NeoForge 的流体能力装入或倒空物品容器。 */
public final class FluidContainerUtils {

    private FluidContainerUtils() {}

    /** 从来源装入容器。调用方持有容器能力，并负责应用变化后的物品栈。 */
    public static int fill(IFluidHandlerItem item, IFluidHandler source, int maximum) {
        return transfer(source, item, maximum).getAmount();
    }

    /** 将单个物品容器排入流体处理器。调用方负责应用变化后的物品栈。 */
    public static int drain(IFluidHandlerItem item, IFluidHandler target, int maximum) {
        return transfer(item, target, maximum).getAmount();
    }

    /** 在服务端处理光标上的容器。普通点击处理一个，批量点击至多处理整组。 */
    public static int click(ServerPlayer player, IFluidHandler handler, boolean batch, boolean allowFill, boolean allowDrain) {
        return click(player, player.containerMenu, handler, batch ? Integer.MAX_VALUE : 1, allowFill, allowDrain);
    }

    /**
     * 操作代表指定储罐的流体处理器。装入成功后按同一方向继续，无法装入时可尝试倒空。
     * 光标空出后放回同类结果，其余交给玩家库存。
     */
    public static int click(ServerPlayer player, AbstractContainerMenu menu, IFluidHandler handler,
                            int maximumContainers, boolean allowFill, boolean allowDrain) {
        if (maximumContainers < 0) throw new IllegalArgumentException("容器数量应为非负数");
        ItemStack carried = menu.getCarried();
        int maximum = Math.min(carried.getCount(), maximumContainers);
        if (maximum == 0 || (!allowFill && !allowDrain)) return 0;

        var products = new ObjectArrayList<ItemStack>();
        FluidStack firstFluid = FluidStack.EMPTY;
        int containers = 0;
        boolean fill = allowFill;
        for (; containers < maximum; containers++) {
            var item = FluidUtil.getFluidHandler(carried.copyWithCount(1)).orElse(null);
            if (item == null) break;
            FluidStack moved = fill ? transfer(handler, item, Integer.MAX_VALUE) : FluidStack.EMPTY;
            if (moved.isEmpty() && (allowDrain && containers == 0 || !fill)) {
                moved = transfer(item, handler, Integer.MAX_VALUE);
                fill = false;
            }
            if (moved.isEmpty()) break;

            ItemStack product = item.getContainer();
            if (!product.isEmpty()) products.add(product);
            carried.shrink(1);
            if (firstFluid.isEmpty()) firstFluid = moved;
        }
        if (containers == 0) return 0;

        ItemStack cursor = carried;
        for (ItemStack product : products) {
            if (cursor.isEmpty()) {
                cursor = product;
            } else if (ItemStack.isSameItemSameComponents(cursor, product)) {
                int stackable = Math.min(cursor.getMaxStackSize() - cursor.getCount(), product.getCount());
                if (stackable > 0) {
                    cursor.grow(stackable);
                    product.shrink(stackable);
                }
                if (!product.isEmpty()) player.getInventory().placeItemBackInInventory(product);
            } else {
                player.getInventory().placeItemBackInInventory(product);
            }
        }
        menu.setCarried(cursor);

        var sound = firstFluid.getFluidType().getSound(firstFluid, fill ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
        if (sound == null) sound = fill ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY;
        player.level().playSound(null, player, sound, SoundSource.BLOCKS, 1, 1);
        menu.broadcastChanges();
        return containers;
    }

    private static FluidStack transfer(IFluidHandler source, IFluidHandler target, int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("转移数量应为非负数");
        return maximum == 0 || source == target ? FluidStack.EMPTY : FluidUtil.tryFluidTransfer(target, source, maximum, true);
    }
}
