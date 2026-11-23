package com.modularmc.mmclutils.fluid;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

/** 通过 NeoForge 的物品访问修改容器，流体与原物品的替换在同一个事务中完成。 */
public final class FluidContainers {

    private FluidContainers() {}

    public static int fill(ItemAccess item, ResourceHandler<FluidResource> source, int tank, int maximum,
                           @Nullable TransactionContext parent) {
        var access = item.oneByOne();
        var target = access.getCapability(Capabilities.Fluid.ITEM);
        return target == null ? 0 : FluidTransfers.move(source, tank, target, maximum, parent);
    }

    public static int drain(ItemAccess item, int tank, ResourceHandler<FluidResource> target, int maximum,
                            @Nullable TransactionContext parent) {
        var access = item.oneByOne();
        var source = access.getCapability(Capabilities.Fluid.ITEM);
        return source == null ? 0 : FluidTransfers.move(source, tank, target, maximum, parent);
    }
}
