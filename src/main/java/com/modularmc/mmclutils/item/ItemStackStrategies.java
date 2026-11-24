package com.modularmc.mmclutils.item;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.Hash;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 按物品、数量和数据组件配置物品栈的哈希与相等策略。
 */
public interface ItemStackStrategies extends Hash.Strategy<ItemStack> {

    /**
     * @return 可组合比较字段的构建器
     */
    static ItemStackHashStrategyBuilder builder() {
        return new ItemStackHashStrategyBuilder();
    }

    /**
     * 对物品、数量和组件都进行比较。
     *
     * @return 全字段比较策略
     */
    static ItemStackStrategies comparingAll() {
        return ItemStackHashStrategyBuilder.ALL;
    }

    /**
     * 仅比较物品和数据组件。
     *
     * @return 忽略数量的比较策略
     */
    static ItemStackStrategies comparingAllButCount() {
        return ItemStackHashStrategyBuilder.ITEM_AND_TAG;
    }

    static ItemStackStrategies comparingItem() {
        return ItemStackHashStrategyBuilder.ITEM;
    }

    /**
     * 配置物品栈策略的比较字段。
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
         * 选择是否比较物品类型。
         *
         * @param choice 是否参与比较
         * @return 当前构建器
         */
        public ItemStackHashStrategyBuilder compareItem(boolean choice) {
            item = choice;
            return this;
        }

        /**
         * 选择是否比较堆叠数量。
         *
         * @param choice 是否参与比较
         * @return 当前构建器
         */
        public ItemStackHashStrategyBuilder compareCount(boolean choice) {
            count = choice;
            return this;
        }

        /**
         * 选择是否比较数据组件。
         *
         * @param choice 是否参与比较
         * @return 当前构建器
         */
        public ItemStackHashStrategyBuilder compareComponents(boolean choice) {
            components = choice;
            return this;
        }

        /**
         * @return 按当前比较字段生成的物品栈策略
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
