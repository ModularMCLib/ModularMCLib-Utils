package com.modularmc.mmclutils.api.inventory;

import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.Hash;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 按流体、数量和数据组件配置流体栈的哈希与相等策略。
 */
public interface FluidStackStrategy extends Hash.Strategy<FluidStack> {

    /**
     * @return 可组合比较字段的构建器
     */
    static FluidStackStrategy.FluidStackHashStrategyBuilder builder() {
        return new FluidStackStrategy.FluidStackHashStrategyBuilder();
    }

    /**
     * 对流体、数量和组件都进行比较。
     *
     * @return 全字段比较策略
     */
    static FluidStackStrategy comparingAll() {
        return builder().compareFluid(true)
                .compareCount(true)
                .compareComponents(true)
                .build();
    }

    /**
     * 仅比较流体和数据组件。
     *
     * @return 忽略数量的比较策略
     */
    static FluidStackStrategy comparingAllButAmount() {
        return builder().compareFluid(true)
                .compareComponents(true)
                .build();
    }

    /**
     * 配置流体栈策略的比较字段。
     */
    class FluidStackHashStrategyBuilder {

        private boolean fluid, amount, components;

        /**
         * 选择是否比较流体类型。
         *
         * @param choice 是否参与比较
         * @return 当前构建器
         */
        public FluidStackStrategy.FluidStackHashStrategyBuilder compareFluid(boolean choice) {
            fluid = choice;
            return this;
        }

        /**
         * 选择是否比较流体量。
         *
         * @param choice 是否参与比较
         * @return 当前构建器
         */
        public FluidStackStrategy.FluidStackHashStrategyBuilder compareCount(boolean choice) {
            amount = choice;
            return this;
        }

        /**
         * 选择是否比较数据组件。
         *
         * @param choice 是否参与比较
         * @return 当前构建器
         */
        public FluidStackStrategy.FluidStackHashStrategyBuilder compareComponents(boolean choice) {
            components = choice;
            return this;
        }

        /**
         * @return 按当前比较字段生成的流体栈策略
         */
        public FluidStackStrategy build() {
            return new FluidStackStrategy() {

                @Override
                public int hashCode(@Nullable FluidStack o) {
                    return o == null || o.isEmpty() ? 0 : Objects.hash(
                            fluid ? o.getFluid() : null,
                            amount ? o.getAmount() : null,
                            components ? o.getComponents() : null);
                }

                @Override
                public boolean equals(@Nullable FluidStack a, @Nullable FluidStack b) {
                    if (a == null || a.isEmpty()) return b == null || b.isEmpty();
                    if (b == null || b.isEmpty()) return false;

                    return (!fluid || a.getFluid() == b.getFluid()) &&
                            (!amount || a.getAmount() == b.getAmount()) &&
                            (!components || Objects.equals(a.getComponents(), b.getComponents()));
                }
            };
        }
    }
}
