package com.modularmc.mmclutils.serialization;

import com.modularmc.mmclutils.io.FileUtils.Reader;
import com.modularmc.mmclutils.io.FileUtils.Writer;

import net.minecraft.resources.Identifier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.NullMarked;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

/** 二进制值与集合的编解码。读取变长字段时按调用方给定的上限检查长度。 */
@NullMarked
public final class BinaryCodecs {

    public static final BinaryCodec<String> UTF = new BinaryCodec<>(DataInput::readUTF, DataOutputStream::writeUTF);
    public static final BinaryCodec<Boolean> BOOLEAN = new BinaryCodec<>(DataInputStream::readBoolean, DataOutputStream::writeBoolean);
    public static final BinaryCodec<UUID> UUID_TEXT = new BinaryCodec<>(input -> UUID.fromString(input.readUTF()),
            (output, value) -> output.writeUTF(value.toString()));
    public static final BinaryCodec<Identifier> IDENTIFIER = new BinaryCodec<>(input -> Identifier.parse(input.readUTF()),
            (output, value) -> output.writeUTF(value.toString()));

    private BinaryCodecs() {}

    /** 以四字节长度开头读写字节数组，限制读写的最大字节数。 */
    public static BinaryCodec<byte[]> bytes(int maximumLength) {
        checkMaximumLength(maximumLength);
        return new BinaryCodec<>(input -> {
            int length = checkLength(input.readInt(), maximumLength);
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return bytes;
        }, (output, bytes) -> {
            output.writeInt(checkLength(bytes.length, maximumLength));
            output.write(bytes);
        });
    }

    /** 按有符号二进制补码读写大整数。上限单位为字节。 */
    public static BinaryCodec<BigInteger> bigInteger(int maximumLength) {
        BinaryCodec<byte[]> bytes = bytes(maximumLength);
        return new BinaryCodec<>(input -> {
            byte[] value = bytes.read(input);
            if (value.length == 0) throw new IOException("整数二进制字段为空");
            return new BigInteger(value);
        }, (output, value) -> bytes.write(output, value.toByteArray()));
    }

    /** 兼容旧文件的四字节长度与逐字节异或布局。字符集须与旧文件写入时一致。 */
    public static BinaryCodec<String> legacyXorString(int maximumLength, Charset charset) {
        BinaryCodec<byte[]> bytes = bytes(maximumLength);
        return new BinaryCodec<>(input -> new String(xor(bytes.read(input)), charset),
                (output, value) -> bytes.write(output, xor(value.getBytes(charset))));
    }

    /** 将已有读取器的结果转换为另一类型。 */
    public static <S, T> Reader<T> convertReader(Reader<S> reader, Function<? super S, ? extends T> convert) {
        return input -> convert.apply(reader.read(input));
    }

    /** 将写入前的值转换为已有写入器接受的类型。 */
    public static <S, T> Writer<T> convertWriter(Writer<? super S> writer, Function<? super T, ? extends S> convert) {
        return (output, value) -> writer.write(output, convert.apply(value));
    }

    /** 读取带条目数的键值对。工厂收到校验后的条目数，重复键由映射决定如何处理。 */
    public static <K, V, M extends Map<K, V>> Reader<M> mapReader(
                                                                  int maximumEntries, Reader<K> keys, Reader<V> values, IntFunction<M> create) {
        checkMaximumLength(maximumEntries);
        return input -> {
            int size = checkLength(input.readInt(), maximumEntries);
            M map = create.apply(size);
            for (int index = 0; index < size; index++) {
                map.put(keys.read(input), values.read(input));
            }
            return map;
        };
    }

    /** 读取二进制映射，键和值均按引用比较。 */
    public static <K, V> Reader<Map<K, V>> mapReader(int maximumEntries, Reader<K> keys, Reader<V> values) {
        return mapReader(maximumEntries, keys, values, Reference2ReferenceOpenHashMap::new);
    }

    /** 读取带元素数的序列。工厂收到校验后的元素数，并创建空集合。 */
    public static <E, C extends Collection<E>> Reader<C> collectionReader(
                                                                          int maximumEntries, Reader<E> elements, IntFunction<C> create) {
        checkMaximumLength(maximumEntries);
        return input -> {
            int size = checkLength(input.readInt(), maximumEntries);
            C result = create.apply(size);
            for (int index = 0; index < size; index++) {
                result.add(elements.read(input));
            }
            return result;
        };
    }

    /** 读取有序元素序列，返回可修改的列表。 */
    public static <E> Reader<List<E>> listReader(int maximumEntries, Reader<E> elements) {
        return collectionReader(maximumEntries, elements, ObjectArrayList::new);
    }

    /** 读取集合，元素按引用比较。 */
    public static <E> Reader<Set<E>> setReader(int maximumEntries, Reader<E> elements) {
        return collectionReader(maximumEntries, elements, ReferenceOpenHashSet::new);
    }

    /** 读取带长度的数组。工厂按入参长度创建元素数组。 */
    public static <E> Reader<E[]> arrayReader(int maximumLength, Reader<E> elements, IntFunction<E[]> create) {
        checkMaximumLength(maximumLength);
        return input -> {
            int length = checkLength(input.readInt(), maximumLength);
            E[] values = create.apply(length);
            for (int index = 0; index < length; index++) {
                values[index] = elements.read(input);
            }
            return values;
        };
    }

    /** 将键和值按映射的迭代顺序写入二进制格式。 */
    public static <K, V> Writer<Map<K, V>> mapWriter(Writer<? super K> keys, Writer<? super V> values) {
        return (output, map) -> {
            output.writeInt(map.size());
            for (Map.Entry<K, V> entry : map.entrySet()) {
                keys.write(output, entry.getKey());
                values.write(output, entry.getValue());
            }
        };
    }

    /** 将元素数量和集合内容写入二进制流。 */
    public static <E> Writer<Collection<E>> collectionWriter(Writer<? super E> elements) {
        return (output, values) -> {
            output.writeInt(values.size());
            for (E value : values) {
                elements.write(output, value);
            }
        };
    }

    /** 将数组长度和数组元素写入二进制流。 */
    public static <E> Writer<E[]> arrayWriter(Writer<? super E> elements) {
        return (output, values) -> {
            output.writeInt(values.length);
            for (E value : values) {
                elements.write(output, value);
            }
        };
    }

    private static void checkMaximumLength(int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("二进制字段上限须为非负数");
    }

    private static int checkLength(int length, int maximum) throws IOException {
        if (length < 0 || length > maximum) {
            throw new IOException("二进制字段长度 " + length + " 超出范围 [0, " + maximum + "]");
        }
        return length;
    }

    private static byte[] xor(byte[] bytes) {
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] ^= 0x43;
        }
        return bytes;
    }

    /** 同一值的两个编解码方向共享约定的字节布局。 */
    public record BinaryCodec<T>(Reader<T> reader, Writer<T> writer) implements Reader<T>, Writer<T> {

        @Override
        public T read(DataInputStream input) throws IOException {
            return reader.read(input);
        }

        @Override
        public void write(DataOutputStream output, T value) throws IOException {
            writer.write(output, value);
        }
    }
}
