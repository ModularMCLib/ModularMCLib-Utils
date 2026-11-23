package com.modularmc.mmclutils.concurrent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * "recreations" of Java 25 scoped values using {@link AutoCloseable AutoCloseables}.
 */
public abstract class ScopedOverride implements AutoCloseable {

    /**
     * Scoped object value. Resets to {@code null} when exiting scope.
     *
     * @param <T> The type of the object.
     */
    @RequiredArgsConstructor
    public static final class Object<T> extends ScopedOverride {

        private final @Nullable T initialValue;

        /**
         * Current value in this scope
         */
        @Getter
        private @Nullable T value;

        public Object() {
            this(null);
        }

        /**
         * Set {@code current} to {@code value} within this scope.
         *
         * @return this
         */
        public Object<T> with(T value) {
            this.value = value;
            return this;
        }

        @Override
        public void close() {
            this.value = this.initialValue;
        }
    }

    /**
     * Scoped boolean value. Resets to {@link #initialValue} when exiting scope.
     */
    @RequiredArgsConstructor
    public static final class Boolean extends ScopedOverride {

        private final boolean initialValue;

        /**
         * Current value in this scope
         */
        @Getter
        private boolean active;

        /**
         * Set {@code current} to {@code value} within this scope.
         *
         * @return this
         */
        public Boolean with(boolean value) {
            this.active = value;
            return this;
        }

        /**
         * Shortcut method that sets {@code current} to {@code !initial}
         *
         * @return this
         */
        public Boolean active() {
            return with(!this.initialValue);
        }

        @Override
        public void close() {
            this.active = this.initialValue;
        }
    }
}
