package com.modularmc.mmclutils.api.data;

import net.minecraft.util.StringRepresentable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * 包含真、假和使用默认值的三态布尔值。
 */
public enum TriState implements StringRepresentable {

    /**
     * 真。
     */
    TRUE,
    /**
     * 使用调用方指定的默认值。
     */
    DEFAULT,
    /**
     * 假。
     */
    FALSE;

    /** 接受布尔值和枚举名。显式真、假写作布尔值，默认状态写作枚举名。 */
    public static final Codec<TriState> CODEC = Codec.either(Codec.BOOL, StringRepresentable.fromEnum(TriState::values))
            .xmap(either -> either.map(TriState::of, Function.identity()),
                    triState -> triState != DEFAULT ? Either.left(triState.isTrue()) : Either.right(DEFAULT));

    /** 判断是否显式启用。 */
    public boolean isTrue() {
        return this == TRUE;
    }

    /** 判断是否应交由调用方决定具体布尔值。 */
    public boolean isDefault() {
        return this == DEFAULT;
    }

    /** 判断是否显式禁用。 */
    public boolean isFalse() {
        return this == FALSE;
    }

    /** 包装已确定的布尔值。 */
    public static TriState of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /** 包装可空布尔值，缺失时使用 {@link #DEFAULT}。 */
    public static TriState of(@Nullable Boolean value) {
        return value == null ? DEFAULT : of((boolean) value);
    }

    /** 读取成功结果中的布尔值。解析错误或缺少值时使用 {@link #DEFAULT}。 */
    public static TriState of(DataResult<Boolean> value) {
        if (value.result().isPresent()) return of(value.result().get());
        // 解析失败时采用默认值。
        else return DEFAULT;
    }

    /** 将真和假转为装箱布尔值，默认状态返回空值。 */
    public @Nullable Boolean toBoolean() {
        return switch (this) {
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            default -> null;
        };
    }

    /** 将真和假转为成功结果，默认状态转为失败结果，供需要确定值的 Codec 使用。 */
    public DataResult<Boolean> toBooleanOrError() {
        return switch (this) {
            case TRUE -> DataResult.success(Boolean.TRUE);
            case FALSE -> DataResult.success(Boolean.FALSE);
            default -> DataResult.error(() -> "Default does not have a value", Lifecycle.stable());
        };
    }

    /** 取得显式布尔值，默认状态时使用调用方传入的值。 */
    public boolean toBooleanOrElse(final boolean other) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            default -> other;
        };
    }

    /** 取得显式布尔值，只有默认状态才调用供应器。 */
    public boolean toBooleanOrElseGet(final @NonNull BooleanSupplier supplier) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            default -> supplier.getAsBoolean();
        };
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
