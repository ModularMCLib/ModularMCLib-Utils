package com.modularmc.mmclutils.item;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.Hash;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A configurable generator of hashing strategies, allowing for consideration of select properties of ItemStacks when
 * considering equality.
 */
public interface ItemStackStrategies extends Hash.Strategy<ItemStack> {

    /**
     * @return a builder object for producing a custom ItemStackStrategies.
     */
    static ItemStackHashStrategyBuilder builder() {
        return new ItemStackHashStrategyBuilder();
    }

    /**
     * Generates an ItemStackHash configured to compare every aspect of ItemStacks.
     *
     * @return the ItemStackStrategies as described above.
     */
    static ItemStackStrategies comparingAll() {
        return ItemStackHashStrategyBuilder.ALL;
    }

    /**
     * Generates an ItemStackHash configured to compare every aspect of ItemStacks except the number
     * of items in the stack.
     *
     * @return the ItemStackStrategies as described above.
     */
    static ItemStackStrategies comparingAllButCount() {
        return ItemStackHashStrategyBuilder.ITEM_AND_TAG;
    }

    static ItemStackStrategies comparingItem() {
        return ItemStackHashStrategyBuilder.ITEM;
    }

    /**
     * Builder pattern class for generating customized ItemStackStrategies
     */
    class ItemStackHashStrategyBuilder {

        private static final ItemStackStrategies ALL = builder().compareItem(true)
                .compareCount(true)
                .compareComponents(true)
                .build();
        private static final ItemStackStrategies ITEM_AND_TAG = builder().compareItem(true)
                .compareComponents(true)
                .build();
        private static final ItemStackStrategies ITEM = builder().compareItem(true).build();

        private boolean item, count, components;

        /**
         * Defines whether the Item type should be considered for equality.
         *
         * @param choice {@code true} to consider this property, {@code false} to ignore it.
         * @return {@code this}
         */
        public ItemStackHashStrategyBuilder compareItem(boolean choice) {
            item = choice;
            return this;
        }

        /**
         * Defines whether stack size should be considered for equality.
         *
         * @param choice {@code true} to consider this property, {@code false} to ignore it.
         * @return {@code this}
         */
        public ItemStackHashStrategyBuilder compareCount(boolean choice) {
            count = choice;
            return this;
        }

        /**
         * Defines whether Data Components should be considered for equality.
         *
         * @param choice {@code true} to consider this property, {@code false} to ignore it.
         * @return {@code this}
         */
        public ItemStackHashStrategyBuilder compareComponents(boolean choice) {
            components = choice;
            return this;
        }

        /**
         * @return the ItemStackStrategies as configured by "compare" methods.
         */
        public ItemStackStrategies build() {
            return new ItemStackStrategies() {

                @Override
                public int hashCode(@Nullable ItemStack o) {
                    return o == null || o.isEmpty() ? 0 : Objects.hash(
                            item ? o.getItem() : null,
                            count ? o.getCount() : null,
                            components ? o.getComponents() : null);
                }

                @Override
                public boolean equals(@Nullable ItemStack a, @Nullable ItemStack b) {
                    if (a == null || a.isEmpty()) return b == null || b.isEmpty();
                    if (b == null || b.isEmpty()) return false;

                    return (!item || a.getItem() == b.getItem()) &&
                            (!count || a.getCount() == b.getCount()) &&
                            (!components || Objects.equals(a.getComponents(), b.getComponents()));
                }
            };
        }
    }
}
