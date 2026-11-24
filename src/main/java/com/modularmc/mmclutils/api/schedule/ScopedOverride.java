package com.modularmc.mmclutils.api.schedule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 通过 {@code try} 资源语句临时覆盖状态。每个实例用于单线程上的一次覆盖，关闭时恢复初始值。
 */
public abstract class ScopedOverride implements AutoCloseable {

    /**
     * 临时覆盖对象值，关闭时恢复构造时指定的值，默认恢复为 {@code null}。
     *
     * @param <T> 对象类型
     */
    @RequiredArgsConstructor
    public static final class Object<T> extends ScopedOverride {

        private final @Nullable T initialValue;

        /**
         * 当前覆盖值，关闭后为配置的恢复值。
         */
        @Getter
        private @Nullable T value;

        /** 创建以空值作为恢复值的对象覆盖。 */
        public Object() {
            this(null);
        }

        /**
         * 设置当前覆盖值，返回可在资源语句中关闭的对象。
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
     * 临时覆盖布尔值，关闭时恢复构造时指定的值。
     */
    @RequiredArgsConstructor
    public static final class Boolean extends ScopedOverride {

        private final boolean initialValue;

        /**
         * 当前布尔状态，关闭后为配置的恢复值。
         */
        @Getter
        private boolean active;

        /**
         * 设置当前覆盖值，返回可在资源语句中关闭的对象。
         *
         * @return 当前作用域对象
         */
        public Boolean with(boolean value) {
            this.active = value;
            return this;
        }

        /**
         * 将当前状态设为初始值的反值，返回可关闭的对象。
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
