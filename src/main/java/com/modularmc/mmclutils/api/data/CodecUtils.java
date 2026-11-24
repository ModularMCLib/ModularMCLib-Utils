package com.modularmc.mmclutils.api.data;

import com.modularmc.mmclutils.api.function.Memoization;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** 为原版 Codec 提供数值范围、集合容器和映射编解码。 */
public final class CodecUtils {

    /** 使用当前数据格式写入长整数。读取时拒绝小数与超出有符号 64 位范围的数值。 */
    public static final Codec<Long> EXACT_LONG = new PrimitiveCodec<>() {

        @Override
        public <T> DataResult<Long> read(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                try {
                    return DataResult.success(Long.parseLong(number.toString()));
                } catch (NumberFormatException invalid) {
                    return DataResult.error(() -> "数值必须是可精确读取的长整数 " + number);
                }
            });
        }

        @Override
        public <T> T write(DynamicOps<T> ops, Long value) {
            return ops.createLong(value);
        }

        @Override
        public String toString() {
            return "ExactLong";
        }
    };

    /** 精确读取含零的非负长整数，拒绝超范围或非整数字面量。 */
    public static final Codec<Long> NON_NEGATIVE_LONG = longRangeWithMessage(0, Long.MAX_VALUE,
            value -> "Value must be non-negative: " + value);
    /** 精确读取严格大于零的长整数。 */
    public static final Codec<Long> POSITIVE_LONG = longRangeWithMessage(1, Long.MAX_VALUE,
            value -> "Value must be positive: " + value);

    private CodecUtils() {}

    /** 以列表格式编解码元素，并在读取后分配可修改的列表。写入保留原迭代顺序。 */
    public static <E> Codec<List<E>> mutableList(Codec<E> elements) {
        return elements.listOf().xmap(ObjectArrayList::new, List::copyOf);
    }

    /** 读取后按元素相等性去重，保留首次出现的顺序。写入时按集合迭代顺序编码为列表。 */
    public static <E> Codec<Set<E>> set(Codec<E> elements) {
        return elements.listOf().xmap(ObjectLinkedOpenHashSet::new, List::copyOf);
    }

    /** 以列表格式保存枚举集合。读取后去重并按枚举声明顺序迭代。 */
    public static <E extends Enum<E>> Codec<Set<E>> enumSet(Class<E> type, Codec<E> elements) {
        return elements.listOf().xmap(values -> {
            Set<E> result = EnumSet.noneOf(type);
            result.addAll(values);
            return result;
        }, List::copyOf);
    }

    /** 解码后生成可修改的映射，保留底层数据格式提供的条目顺序。 */
    public static <K, V> Codec<Map<K, V>> orderedMap(Codec<K> keys, Codec<V> values) {
        return Codec.unboundedMap(keys, values).xmap(Object2ObjectLinkedOpenHashMap::new, map -> map);
    }

    /** 解码后用枚举序号存储键，迭代顺序遵循枚举声明。 */
    public static <K extends Enum<K>, V> Codec<Map<K, V>> enumMap(Class<K> type, Codec<K> keys, Codec<V> values) {
        return Codec.unboundedMap(keys, values).xmap(encoded -> {
            Map<K, V> result = new EnumMap<>(type);
            result.putAll(encoded);
            return result;
        }, map -> map);
    }

    /** 调用方提供新容器，可选择原始类型集合、比较器、引用比较策略或插入顺序。 */
    public static <E, C extends Collection<E>> Codec<C> collection(Codec<E> elements, Supplier<C> create) {
        return elements.listOf().xmap(values -> {
            C collection = create.get();
            collection.addAll(values);
            return collection;
        }, List::copyOf);
    }

    /** 以条目序列编码 Map，可使用无法表示为 JSON 属性名的键。重复键由容器采用最后一个值。 */
    public static <K, V, M extends Map<K, V>> Codec<M> entries(Codec<K> keys, Codec<V> values, Supplier<M> create) {
        return Codec.pair(keys.fieldOf("key").codec(), values.fieldOf("value").codec()).listOf().xmap(entries -> {
            M map = create.get();
            for (Pair<K, V> entry : entries) map.put(entry.getFirst(), entry.getSecond());
            return map;
        }, map -> {
            var entries = new ObjectArrayList<Pair<K, V>>(map.size());
            map.forEach((key, value) -> entries.add(Pair.of(key, value)));
            return entries;
        });
    }

    /** 用相同结构表示普通值与空值，可在 NBT 中保持集合元素类型统一。 */
    public static <E> Codec<Optional<E>> optionalElement(Codec<E> codec) {
        return codec.optionalFieldOf("value").codec();
    }

    /** 以普通列表编码，读取为 fastutil 的可修改对象列表。 */
    public static <E> Codec<ObjectList<E>> objectListCodec(Codec<E> elementCodec) {
        return elementCodec.listOf().xmap(
                values -> new ObjectArrayList<>(values),
                values -> values);
    }

    /**
     * 精确读取长整数，并在读写两端校验闭区间。
     *
     * @param min          最小值
     * @param max          最大值
     * @param errorMessage 根据超范围整数生成错误说明
     * @return 带范围约束的长整数 Codec
     */
    public static Codec<Long> longRangeWithMessage(long min, long max, Function<Long, String> errorMessage) {
        return EXACT_LONG
                .validate(
                        value -> value.compareTo(min) >= 0 && value.compareTo(max) <= 0 ? DataResult.success(value) :
                                DataResult.error(() -> errorMessage.apply(value)));
    }

    /** 校验闭区间内的整数。 */
    public static Codec<Long> longRange(long min, long max) {
        return longRangeWithMessage(min, max, value -> "数值 " + value + " 超出区间 [" + min + ", " + max + "]");
    }

    /** 读取两侧类型相同的任一分支。 */
    public static <T> T unboxEither(Either<T, T> either) {
        return either.map(Function.identity(), Function.identity());
    }

    /** 在首次读取时解码并缓存结果，解码失败由该次读取抛出。供应器由调用线程持有。 */
    public static <T> Codec<Supplier<T>> lazyParsingCodec(Codec<T> delegate) {
        return new LazyParsingCodec<>(delegate);
    }

    /** 用给定 {@link DynamicOps} 和 MapCodec 构建一个独立映射值。合并映射的失败保留在结果中。 */
    public static <T, A> DataResult<A> encodeMap(T value, MapCodec<T> codec, DynamicOps<A> ops) {
        return codec.encode(value, ops, ops.mapBuilder()).build(ops.empty());
    }

    private record LazyParsingCodec<A>(Codec<A> delegate) implements Codec<Supplier<A>> {

        @Override
        public <T> DataResult<Pair<Supplier<A>, T>> decode(DynamicOps<T> ops, T input) {
            Supplier<A> lazyValue = Memoization.memoize(() -> delegate.decode(ops, input).map(Pair::getFirst).getOrThrow());
            return DataResult.success(Pair.of(lazyValue, input));
        }

        @Override
        public <T> DataResult<T> encode(Supplier<A> input, DynamicOps<T> ops, T prefix) {
            A value = input.get();
            return value == null ? DataResult.success(prefix) : delegate.encode(value, ops, prefix);
        }
    }
}
