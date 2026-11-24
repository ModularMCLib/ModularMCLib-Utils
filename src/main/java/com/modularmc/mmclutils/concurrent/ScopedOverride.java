package com.modularmc.mmclutils.concurrent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * "recreations" of Java 25 scoped values using {@link AutoCloseable AutoCloseables}.
 */
public abstract class ScopedOverride implements AutoCloseable {

    /**
     * 在作用域内覆盖对象值，退出时恢复为 {@code null}。
     *
     * @param <T> 对象类型
     */
    @RequiredArgsConstructor
    public static final class Object<T> extends ScopedOverride {

        private final @Nullable T initialValue;

        /**
         * 当前线程的作用域值。
         */
        @Getter
        private @Nullable T value;

        public Object() {
            this(null);
        }

        /**
         * 在当前线程进入一层作用域，并设定该层的值。
         *
         * @return 当前作用域对象
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
     * 在作用域内覆盖布尔值，退出外层后使用 {@link #initialValue}。
     */
    @RequiredArgsConstructor
    public static final class Boolean extends ScopedOverride {

        private final boolean initialValue;

        /**
         * 当前线程的作用域值。
         */
        @Getter
        private boolean active;

        /**
         * 在当前线程进入一层作用域，并设定该层的布尔值。
         *
         * @return 当前作用域对象
         */
        public Boolean with(boolean value) {
            this.active = value;
            return this;
        }

        /**
         * 在当前线程进入一层作用域，并使用初始值的反值。
         *
         * @return 当前作用域对象
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
