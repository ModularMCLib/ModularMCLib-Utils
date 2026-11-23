package com.modularmc.mmclutils.fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.Hash;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A configurable generator of hashing strategies, allowing for consideration of select properties of FluidStacks when
 * considering equality.
 */
public interface FluidStackStrategies extends Hash.Strategy<FluidStack> {

    /**
     * @return a builder object for producing a custom FluidStackStrategies.
     */
    static FluidStackStrategies.FluidStackHashStrategyBuilder builder() {
        return new FluidStackStrategies.FluidStackHashStrategyBuilder();
    }

    /**
     * Generates an FluidStackHash configured to compare every aspect of FluidStacks.
     *
     * @return the FluidStackStrategies as described above.
     */
    static FluidStackStrategies comparingAll() {
        return builder().compareFluid(true)
                .compareCount(true)
                .compareComponents(true)
                .build();
    }

    /**
     * Generates an FluidStackHash configured to compare every aspect of FluidStacks except the amount
     * of fluid in the stack.
     *
     * @return the FluidStackStrategies as described above.
     */
    static FluidStackStrategies comparingAllButAmount() {
        return builder().compareFluid(true)
                .compareComponents(true)
                .build();
    }

    /**
     * Builder pattern class for generating customized FluidStackStrategies
     */
    class FluidStackHashStrategyBuilder {

        private boolean fluid, amount, components;

        /**
         * Defines whether the Fluid type should be considered for equality.
         *
         * @param choice {@code true} to consider this property, {@code false} to ignore it.
         * @return {@code this}
         */
        public FluidStackStrategies.FluidStackHashStrategyBuilder compareFluid(boolean choice) {
            fluid = choice;
            return this;
        }

        /**
         * Defines whether stack amount should be considered for equality.
         *
         * @param choice {@code true} to consider this property, {@code false} to ignore it.
         * @return {@code this}
         */
        public FluidStackStrategies.FluidStackHashStrategyBuilder compareCount(boolean choice) {
            amount = choice;
            return this;
        }

        /**
         * Defines whether Data Components should be considered for equality.
         *
         * @param choice {@code true} to consider this property, {@code false} to ignore it.
         * @return {@code this}
         */
        public FluidStackStrategies.FluidStackHashStrategyBuilder compareComponents(boolean choice) {
            components = choice;
            return this;
        }

        /**
         * @return the FluidStackStrategies as configured by "compare" methods.
         */
        public FluidStackStrategies build() {
            return new FluidStackStrategies() {

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
