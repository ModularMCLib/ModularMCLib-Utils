package com.modularmc.mmclutils.api.data;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function13;
import com.mojang.datafixers.util.Function8;
import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * 创建按键选择值编码器及按固定顺序组合多个字段的流编解码。
 * 组合编码器先依次写字段，解码时按相同顺序读取并传入构造函数。
 */
public class StreamCodecs {

    /**
     * 创建带条目数前缀的映射编解码器，每个值的编码器由其键选择。
     * 解码时将容量限制为不超过 65536 后交给映射工厂，重复键如何处理由映射实现决定。
     *
     * @param <B>                 字节缓冲区类型
     * @param <K>                 映射键类型
     * @param <V>                 映射值类型
     * @param <M>                 目标映射类型
     * @param mapCreationFunction 根据初始容量创建空映射
     * @param keyCodec            键的编解码器
     * @param valueFunction       根据键选择对应值编解码器
     * @return 映射的流编解码器
     */
    public static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> dispatchMap(
                                                                                               final IntFunction<? extends M> mapCreationFunction,
                                                                                               final StreamCodec<? super B, K> keyCodec,
                                                                                               final Function<K, StreamCodec<? super B, V>> valueFunction) {
        return new StreamCodec<>() {

            public void encode(B buf, M val) {
                ByteBufCodecs.writeCount(buf, val.size(), Integer.MAX_VALUE);
                val.forEach((key, value) -> {
                    keyCodec.encode(buf, key);
                    valueFunction.apply(key).encode(buf, value);
                });
            }

            public M decode(B buf) {
                int i = ByteBufCodecs.readCount(buf, Integer.MAX_VALUE);
                M m = mapCreationFunction.apply(Math.min(i, 65536));

                for (int j = 0; j < i; j++) {
                    K k = keyCodec.decode(buf);
                    V v = valueFunction.apply(k).decode(buf);
                    m.put(k, v);
                }

                return m;
            }
        };
    }

    /**
     * 创建包含八个字段的组合编解码器。
     * 字段按照参数顺序写入和读取，解码后的八个值按相同顺序传给构造函数。
     *
     * @param <B>         字节缓冲区类型
     * @param <C>         编解码对象类型
     * @param <T1>        第一个字段类型
     * @param <T2>        第二个字段类型
     * @param <T3>        第三个字段类型
     * @param <T4>        第四个字段类型
     * @param <T5>        第五个字段类型
     * @param <T6>        第六个字段类型
     * @param <T7>        第七个字段类型
     * @param <T8>        第八个字段类型
     * @param codec1      第一个字段的编解码器
     * @param getter1     从对象取得第一个字段
     * @param codec2      第二个字段的编解码器
     * @param getter2     从对象取得第二个字段
     * @param codec3      第三个字段的编解码器
     * @param getter3     从对象取得第三个字段
     * @param codec4      第四个字段的编解码器
     * @param getter4     从对象取得第四个字段
     * @param codec5      第五个字段的编解码器
     * @param getter5     从对象取得第五个字段
     * @param codec6      第六个字段的编解码器
     * @param getter6     从对象取得第六个字段
     * @param codec7      第七个字段的编解码器
     * @param getter7     从对象取得第七个字段
     * @param codec8      第八个字段的编解码器
     * @param getter8     从对象取得第八个字段
     * @param constructor 根据解码出的字段构造对象
     * @return 八字段组合编解码器
     */
    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
                                                                                     final StreamCodec<? super B, T1> codec1,
                                                                                     final Function<C, T1> getter1,
                                                                                     final StreamCodec<? super B, T2> codec2,
                                                                                     final Function<C, T2> getter2,
                                                                                     final StreamCodec<? super B, T3> codec3,
                                                                                     final Function<C, T3> getter3,
                                                                                     final StreamCodec<? super B, T4> codec4,
                                                                                     final Function<C, T4> getter4,
                                                                                     final StreamCodec<? super B, T5> codec5,
                                                                                     final Function<C, T5> getter5,
                                                                                     final StreamCodec<? super B, T6> codec6,
                                                                                     final Function<C, T6> getter6,
                                                                                     final StreamCodec<? super B, T7> codec7,
                                                                                     final Function<C, T7> getter7,
                                                                                     final StreamCodec<? super B, T8> codec8,
                                                                                     final Function<C, T8> getter8,
                                                                                     final Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> constructor) {
        return new StreamCodec<>() {

            @Override
            public C decode(B buffer) {
                T1 t1 = codec1.decode(buffer);
                T2 t2 = codec2.decode(buffer);
                T3 t3 = codec3.decode(buffer);
                T4 t4 = codec4.decode(buffer);
                T5 t5 = codec5.decode(buffer);
                T6 t6 = codec6.decode(buffer);
                T7 t7 = codec7.decode(buffer);
                T8 t8 = codec8.decode(buffer);
                return constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8);
            }

            @Override
            public void encode(B buffer, C object) {
                codec1.encode(buffer, getter1.apply(object));
                codec2.encode(buffer, getter2.apply(object));
                codec3.encode(buffer, getter3.apply(object));
                codec4.encode(buffer, getter4.apply(object));
                codec5.encode(buffer, getter5.apply(object));
                codec6.encode(buffer, getter6.apply(object));
                codec7.encode(buffer, getter7.apply(object));
                codec8.encode(buffer, getter8.apply(object));
            }
        };
    }

    /**
     * 创建包含十二个字段的组合编解码器。
     * 字段按照参数顺序写入和读取，解码后的十二个值按相同顺序传给构造函数。
     *
     * @param <B>         字节缓冲区类型
     * @param <C>         编解码对象类型
     * @param <T1>        第一个字段类型
     * @param <T2>        第二个字段类型
     * @param <T3>        第三个字段类型
     * @param <T4>        第四个字段类型
     * @param <T5>        第五个字段类型
     * @param <T6>        第六个字段类型
     * @param <T7>        第七个字段类型
     * @param <T8>        第八个字段类型
     * @param <T9>        第九个字段类型
     * @param <T10>       第十个字段类型
     * @param <T11>       第十一个字段类型
     * @param <T12>       第十二个字段类型
     * @param codec1      第一个字段的编解码器
     * @param getter1     从对象取得第一个字段
     * @param codec2      第二个字段的编解码器
     * @param getter2     从对象取得第二个字段
     * @param codec3      第三个字段的编解码器
     * @param getter3     从对象取得第三个字段
     * @param codec4      第四个字段的编解码器
     * @param getter4     从对象取得第四个字段
     * @param codec5      第五个字段的编解码器
     * @param getter5     从对象取得第五个字段
     * @param codec6      第六个字段的编解码器
     * @param getter6     从对象取得第六个字段
     * @param codec7      第七个字段的编解码器
     * @param getter7     从对象取得第七个字段
     * @param codec8      第八个字段的编解码器
     * @param getter8     从对象取得第八个字段
     * @param codec9      第九个字段的编解码器
     * @param getter9     从对象取得第九个字段
     * @param codec10     第十个字段的编解码器
     * @param getter10    从对象取得第十个字段
     * @param codec11     第十一个字段的编解码器
     * @param getter11    从对象取得第十一个字段
     * @param codec12     第十二个字段的编解码器
     * @param getter12    从对象取得第十二个字段
     * @param constructor 根据解码出的字段构造对象
     * @return 十二字段组合编解码器
     */
    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> StreamCodec<B, C> composite(
                                                                                                        final StreamCodec<? super B, T1> codec1,
                                                                                                        final Function<C, T1> getter1,
                                                                                                        final StreamCodec<? super B, T2> codec2,
                                                                                                        final Function<C, T2> getter2,
                                                                                                        final StreamCodec<? super B, T3> codec3,
                                                                                                        final Function<C, T3> getter3,
                                                                                                        final StreamCodec<? super B, T4> codec4,
                                                                                                        final Function<C, T4> getter4,
                                                                                                        final StreamCodec<? super B, T5> codec5,
                                                                                                        final Function<C, T5> getter5,
                                                                                                        final StreamCodec<? super B, T6> codec6,
                                                                                                        final Function<C, T6> getter6,
                                                                                                        final StreamCodec<? super B, T7> codec7,
                                                                                                        final Function<C, T7> getter7,
                                                                                                        final StreamCodec<? super B, T8> codec8,
                                                                                                        final Function<C, T8> getter8,
                                                                                                        final StreamCodec<? super B, T9> codec9,
                                                                                                        final Function<C, T9> getter9,
                                                                                                        final StreamCodec<? super B, T10> codec10,
                                                                                                        final Function<C, T10> getter10,
                                                                                                        final StreamCodec<? super B, T11> codec11,
                                                                                                        final Function<C, T11> getter11,
                                                                                                        final StreamCodec<? super B, T12> codec12,
                                                                                                        final Function<C, T12> getter12,
                                                                                                        final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, C> constructor) {
        return new StreamCodec<>() {

            @Override
            public C decode(B buffer) {
                T1 t1 = codec1.decode(buffer);
                T2 t2 = codec2.decode(buffer);
                T3 t3 = codec3.decode(buffer);
                T4 t4 = codec4.decode(buffer);
                T5 t5 = codec5.decode(buffer);
                T6 t6 = codec6.decode(buffer);
                T7 t7 = codec7.decode(buffer);
                T8 t8 = codec8.decode(buffer);
                T9 t9 = codec9.decode(buffer);
                T10 t10 = codec10.decode(buffer);
                T11 t11 = codec11.decode(buffer);
                T12 t12 = codec12.decode(buffer);
                return constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
            }

            @Override
            public void encode(B buffer, C object) {
                codec1.encode(buffer, getter1.apply(object));
                codec2.encode(buffer, getter2.apply(object));
                codec3.encode(buffer, getter3.apply(object));
                codec4.encode(buffer, getter4.apply(object));
                codec5.encode(buffer, getter5.apply(object));
                codec6.encode(buffer, getter6.apply(object));
                codec7.encode(buffer, getter7.apply(object));
                codec8.encode(buffer, getter8.apply(object));
                codec9.encode(buffer, getter9.apply(object));
                codec10.encode(buffer, getter10.apply(object));
                codec11.encode(buffer, getter11.apply(object));
                codec12.encode(buffer, getter12.apply(object));
            }
        };
    }

    /**
     * 创建包含十三个字段的组合编解码器。
     * 字段按照参数顺序写入和读取，解码后的十三个值按相同顺序传给构造函数。
     *
     * @param <B>         字节缓冲区类型
     * @param <C>         编解码对象类型
     * @param <T1>        第一个字段类型
     * @param <T2>        第二个字段类型
     * @param <T3>        第三个字段类型
     * @param <T4>        第四个字段类型
     * @param <T5>        第五个字段类型
     * @param <T6>        第六个字段类型
     * @param <T7>        第七个字段类型
     * @param <T8>        第八个字段类型
     * @param <T9>        第九个字段类型
     * @param <T10>       第十个字段类型
     * @param <T11>       第十一个字段类型
     * @param <T12>       第十二个字段类型
     * @param <T13>       第十三个字段类型
     * @param codec1      第一个字段的编解码器
     * @param getter1     从对象取得第一个字段
     * @param codec2      第二个字段的编解码器
     * @param getter2     从对象取得第二个字段
     * @param codec3      第三个字段的编解码器
     * @param getter3     从对象取得第三个字段
     * @param codec4      第四个字段的编解码器
     * @param getter4     从对象取得第四个字段
     * @param codec5      第五个字段的编解码器
     * @param getter5     从对象取得第五个字段
     * @param codec6      第六个字段的编解码器
     * @param getter6     从对象取得第六个字段
     * @param codec7      第七个字段的编解码器
     * @param getter7     从对象取得第七个字段
     * @param codec8      第八个字段的编解码器
     * @param getter8     从对象取得第八个字段
     * @param codec9      第九个字段的编解码器
     * @param getter9     从对象取得第九个字段
     * @param codec10     第十个字段的编解码器
     * @param getter10    从对象取得第十个字段
     * @param codec11     第十一个字段的编解码器
     * @param getter11    从对象取得第十一个字段
     * @param codec12     第十二个字段的编解码器
     * @param getter12    从对象取得第十二个字段
     * @param codec13     第十三个字段的编解码器
     * @param getter13    从对象取得第十三个字段
     * @param constructor 根据解码出的字段构造对象
     * @return 十三字段组合编解码器
     */
    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> StreamCodec<B, C> composite(
                                                                                                             final StreamCodec<? super B, T1> codec1,
                                                                                                             final Function<C, T1> getter1,
                                                                                                             final StreamCodec<? super B, T2> codec2,
                                                                                                             final Function<C, T2> getter2,
                                                                                                             final StreamCodec<? super B, T3> codec3,
                                                                                                             final Function<C, T3> getter3,
                                                                                                             final StreamCodec<? super B, T4> codec4,
                                                                                                             final Function<C, T4> getter4,
                                                                                                             final StreamCodec<? super B, T5> codec5,
                                                                                                             final Function<C, T5> getter5,
                                                                                                             final StreamCodec<? super B, T6> codec6,
                                                                                                             final Function<C, T6> getter6,
                                                                                                             final StreamCodec<? super B, T7> codec7,
                                                                                                             final Function<C, T7> getter7,
                                                                                                             final StreamCodec<? super B, T8> codec8,
                                                                                                             final Function<C, T8> getter8,
                                                                                                             final StreamCodec<? super B, T9> codec9,
                                                                                                             final Function<C, T9> getter9,
                                                                                                             final StreamCodec<? super B, T10> codec10,
                                                                                                             final Function<C, T10> getter10,
                                                                                                             final StreamCodec<? super B, T11> codec11,
                                                                                                             final Function<C, T11> getter11,
                                                                                                             final StreamCodec<? super B, T12> codec12,
                                                                                                             final Function<C, T12> getter12,
                                                                                                             final StreamCodec<? super B, T13> codec13,
                                                                                                             final Function<C, T13> getter13,
                                                                                                             final Function13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, C> constructor) {
        return new StreamCodec<>() {

            @Override
            public C decode(B buffer) {
                T1 t1 = codec1.decode(buffer);
                T2 t2 = codec2.decode(buffer);
                T3 t3 = codec3.decode(buffer);
                T4 t4 = codec4.decode(buffer);
                T5 t5 = codec5.decode(buffer);
                T6 t6 = codec6.decode(buffer);
                T7 t7 = codec7.decode(buffer);
                T8 t8 = codec8.decode(buffer);
                T9 t9 = codec9.decode(buffer);
                T10 t10 = codec10.decode(buffer);
                T11 t11 = codec11.decode(buffer);
                T12 t12 = codec12.decode(buffer);
                T13 t13 = codec13.decode(buffer);
                return constructor.apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13);
            }

            @Override
            public void encode(B buffer, C object) {
                codec1.encode(buffer, getter1.apply(object));
                codec2.encode(buffer, getter2.apply(object));
                codec3.encode(buffer, getter3.apply(object));
                codec4.encode(buffer, getter4.apply(object));
                codec5.encode(buffer, getter5.apply(object));
                codec6.encode(buffer, getter6.apply(object));
                codec7.encode(buffer, getter7.apply(object));
                codec8.encode(buffer, getter8.apply(object));
                codec9.encode(buffer, getter9.apply(object));
                codec10.encode(buffer, getter10.apply(object));
                codec11.encode(buffer, getter11.apply(object));
                codec12.encode(buffer, getter12.apply(object));
                codec13.encode(buffer, getter13.apply(object));
            }
        };
    }
}
