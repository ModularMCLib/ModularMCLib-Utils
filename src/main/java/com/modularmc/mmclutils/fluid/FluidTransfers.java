package com.modularmc.mmclutils.fluid;

import com.modularmc.mmclutils.inventory.InventoryTransfers;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

/** 以相同事务转移流体及其组件。 */
public final class FluidTransfers {

    private FluidTransfers() {}

    public static int move(ResourceHandler<FluidResource> source, int tank, ResourceHandler<FluidResource> target,
                           int maximum, @Nullable TransactionContext parent) {
        return InventoryTransfers.move(source, tank, target, maximum, parent);
    }
}
