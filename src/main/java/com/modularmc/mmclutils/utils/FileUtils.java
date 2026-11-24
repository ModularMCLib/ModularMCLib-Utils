package com.modularmc.mmclutils.utils;

import net.minecraft.resources.Identifier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/** 文件与流读写、二进制格式、目录操作、路径互斥及内容摘要。 */
@NullMarked
public final class FileUtils {

    private static final ConcurrentMap<Path, ReentrantLock> FILE_LOCKS = new ConcurrentHashMap<>();

    private FileUtils() {}

    /** 按根路径所属的文件系统组合路径片段。 */
    public static Path resolve(Path root, String... parts) {
        return resolve(root, 0, parts);
    }

    /** 从指定片段开始组合路径，允许从末尾开始以直接返回根路径。 */
    public static Path resolve(Path root, int firstPart, String... parts) {
        Objects.checkFromToIndex(firstPart, parts.length, parts.length);
        return switch (parts.length - firstPart) {
            case 0 -> root;
            case 1 -> root.resolve(parts[firstPart]);
            default -> root.resolve(root.getFileSystem().getPath(parts[firstPart], Arrays.copyOfRange(parts, firstPart + 1, parts.length)));
        };
    }

    /** 读取存在的文件。回调返回后流由本方法关闭。 */
    public static <T> T read(Path path, Reader<T> reader) throws IOException {
        return read(Files.newInputStream(path), reader);
    }

    /** 从输入流读取一个值并关闭输入流。 */
    public static <T> T read(InputStream stream, Reader<T> reader) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(stream))) {
            return reader.read(input);
        }
    }

    /** 文件存在时读取。仅在文件成功读取时返回 true。 */
    public static boolean loadFile(Path path, StreamAction<DataInputStream> action) throws IOException {
        return readIfPresent(path, input -> {
            action.accept(input);
            return Boolean.TRUE;
        }) != null;
    }

    /** 直接创建或覆盖文件，并创建尚不存在的父目录。适用于可重建的数据。 */
    public static <T> void write(Path path, T value, Writer<? super T> writer) throws IOException {
        write(Files.newOutputStream(prepareWriteTarget(path)), value, writer);
    }

    /** 在写入回调中直接填充文件。回调结束后关闭输出流。 */
    public static void saveFile(Path path, StreamAction<DataOutputStream> action) throws IOException {
        write(path, action, (output, write) -> write.accept(output));
    }

    /** 将一个值写入输出流并关闭输出流。 */
    public static <T> void write(OutputStream stream, T value, Writer<? super T> writer) throws IOException {
        try (var output = new DataOutputStream(new BufferedOutputStream(stream))) {
            writer.write(output, value);
        }
    }

    /** 文件存在时读取。打开文件时若路径缺失，返回空值。读取失败会保留原文件并抛出异常。 */
    public static <T> @Nullable T readIfPresent(Path path, Reader<T> reader) throws IOException {
        InputStream stream;
        try {
            stream = Files.newInputStream(path);
        } catch (NoSuchFileException missing) {
            return null;
        }
        return read(stream, reader);
    }

    /** 仅在输出流关闭成功后替换目标。读取方始终看到一份完整的文件。 */
    public static <T> void writeAtomically(Path path, T value, Writer<? super T> writer) throws IOException {
        Path target = prepareWriteTarget(path);
        Path temporary = Files.createTempFile(target.getParent(), ".mmclutils-", ".tmp");
        try {
            write(Files.newOutputStream(temporary), value, writer);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** 通过回调填充临时文件，写入成功后原子替换目标。 */
    public static void writeAtomically(Path path, StreamAction<DataOutputStream> action) throws IOException {
        writeAtomically(path, action, (output, write) -> write.accept(output));
    }

    private static Path prepareWriteTarget(Path path) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path directory = target.getParent();
        if (directory == null) throw new IOException("目标路径没有文件名：" + path);
        Files.createDirectories(directory);
        return target;
    }

    /** 列出目录中名称以 {@code .ser} 结尾的普通文件。目录缺失时返回空流，调用方负责关闭返回的流。 */
    public static Stream<Path> cacheFiles(Path directory) throws IOException {
        try {
            return Files.list(directory)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ser"))
                    .filter(Files::isRegularFile);
        } catch (NoSuchFileException missing) {
            return Stream.empty();
        }
    }

    /** 递归删除文件或目录。路径缺失时直接返回，遇到符号链接时删除链接本身。 */
    public static void deleteTree(Path path) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 取得按规范化绝对路径共享的进程内锁。调用方负责加锁与解锁。 */
    public static ReentrantLock fileLock(Path path) {
        return FILE_LOCKS.computeIfAbsent(path.toAbsolutePath().normalize(), ignored -> new ReentrantLock());
    }

    /** 以调用方指定的 JDK 算法计算文件摘要，返回小写十六进制。 */
    public static String digest(Path path, String algorithm) throws IOException, NoSuchAlgorithmException {
        try (InputStream input = Files.newInputStream(path)) {
            return digest(input, algorithm);
        }
    }

    /** 从流的当前位置分块计算摘要。调用方负责关闭输入流。 */
    public static String digest(InputStream input, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 计算字节数组的摘要，输出小写十六进制。 */
    public static String digest(byte[] bytes, String algorithm) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    /** 将 UTF-8 文本转换为稳定的 SHA-256 标识，保留算法前缀。 */
    public static String sha256(String value) throws NoSuchAlgorithmException {
        return "sha256:" + digest(value.getBytes(StandardCharsets.UTF_8), "SHA-256");
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

    /** 在调用线程从数据流读取完整的非空值。外层负责维护流的生命周期，格式错误通过 IOException 报告。 */
    @FunctionalInterface
    public interface Reader<T> {

        /** 从当前流位置解码一个值，遇到截断或格式错误时抛出 IOException。 */
        T read(DataInputStream input) throws IOException;

        /** 将已有读取器的结果转换为另一类型。 */
        static <S, T> Reader<T> convert(Reader<S> reader, Function<? super S, ? extends T> convert) {
            return input -> convert.apply(reader.read(input));
        }

        /** 读取带条目数的键值对。工厂收到校验后的条目数，重复键由映射决定如何处理。 */
        static <K, V, M extends Map<K, V>> Reader<M> map(
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
        static <K, V> Reader<Map<K, V>> map(int maximumEntries, Reader<K> keys, Reader<V> values) {
            return map(maximumEntries, keys, values, Reference2ReferenceOpenHashMap::new);
        }

        /** 读取带元素数的序列。工厂收到校验后的元素数，并创建空集合。 */
        static <E, C extends Collection<E>> Reader<C> collection(
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
        static <E> Reader<List<E>> list(int maximumEntries, Reader<E> elements) {
            return list(maximumEntries, elements, ObjectArrayList::new);
        }

        /** 由调用方选择列表类型。工厂接收长度校验后的元素数。 */
        static <E, L extends List<E>> Reader<L> list(int maximumEntries, Reader<E> elements, IntFunction<L> create) {
            return collection(maximumEntries, elements, create);
        }

        /** 读取集合，元素按引用比较。 */
        static <E> Reader<Set<E>> set(int maximumEntries, Reader<E> elements) {
            return set(maximumEntries, elements, ReferenceOpenHashSet::new);
        }

        /** 由调用方选择集合类型及其元素比较策略。 */
        static <E, S extends Set<E>> Reader<S> set(int maximumEntries, Reader<E> elements, IntFunction<S> create) {
            return collection(maximumEntries, elements, create);
        }

        /** 读取带长度的数组。工厂按入参长度创建元素数组。 */
        static <E> Reader<E[]> array(int maximumLength, Reader<E> elements, IntFunction<E[]> create) {
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
    }

    /** 在调用线程按读取器约定的布局写入非空值。输出失败时抛出 IOException，外层负责关闭流。 */
    @FunctionalInterface
    public interface Writer<T> {

        /** 从当前流位置写入值，输出失败时抛出 IOException。 */
        void write(DataOutputStream output, T value) throws IOException;

        /** 将写入前的值转换为已有写入器接受的类型。 */
        static <S, T> Writer<T> convert(Writer<? super S> writer, Function<? super T, ? extends S> convert) {
            return (output, value) -> writer.write(output, convert.apply(value));
        }

        /** 将键和值按映射的迭代顺序写入二进制格式。 */
        static <K, V> Writer<Map<K, V>> map(Writer<? super K> keys, Writer<? super V> values) {
            return (output, map) -> {
                output.writeInt(map.size());
                for (Map.Entry<K, V> entry : map.entrySet()) {
                    keys.write(output, entry.getKey());
                    values.write(output, entry.getValue());
                }
            };
        }

        /** 将元素数量和集合内容写入二进制流。 */
        static <E> Writer<Collection<E>> collection(Writer<? super E> elements) {
            return (output, values) -> {
                output.writeInt(values.size());
                for (E value : values) {
                    elements.write(output, value);
                }
            };
        }

        /** 以列表顺序逐项编码。可作为内层的列表写入器使用。 */
        static <E> Writer<List<E>> list(Writer<? super E> elements) {
            return Writer.<E>collection(elements)::write;
        }

        /** 以集合的迭代顺序逐项编码。 */
        static <E> Writer<Set<E>> set(Writer<? super E> elements) {
            return Writer.<E>collection(elements)::write;
        }

        /** 将数组长度和数组元素写入二进制流。 */
        static <E> Writer<E[]> array(Writer<? super E> elements) {
            return (output, values) -> {
                output.writeInt(values.length);
                for (E value : values) {
                    elements.write(output, value);
                }
            };
        }
    }

    /** 同一值的两个编解码方向共享约定的字节布局。 */
    public record Serializer<T>(Reader<T> reader, Writer<T> writer) implements Reader<T>, Writer<T> {

        /** 使用数据流的修订版 UTF-8 格式。 */
        public static final Serializer<String> UTF = new Serializer<>(DataInput::readUTF, DataOutputStream::writeUTF);

        /** 使用数据流的一字节布尔格式。 */
        public static final Serializer<Boolean> BOOLEAN = new Serializer<>(DataInputStream::readBoolean, DataOutputStream::writeBoolean);

        /** 以单个 UTF 字段保存 UUID，与旧存档的文本布局一致。 */
        public static final Serializer<UUID> UUID_TEXT = new Serializer<>(input -> {
            String value = input.readUTF();
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException failure) {
                throw new IOException("无效的 UUID：" + value, failure);
            }
        }, (output, value) -> output.writeUTF(value.toString()));

        /** 将完整标识作为一个 UTF 字段写入。 */
        public static final Serializer<Identifier> IDENTIFIER = new Serializer<>(input -> {
            String text = input.readUTF();
            Identifier id = Identifier.tryParse(text);
            if (id == null) throw new IOException("无效的资源标识：" + text);
            return id;
        }, (output, value) -> output.writeUTF(value.toString()));

        /** 兼容先写命名空间、再写路径的双 UTF 布局。 */
        public static final Serializer<Identifier> IDENTIFIER_PARTS = new Serializer<>(input -> {
            String namespace = input.readUTF();
            String path = input.readUTF();
            Identifier id = Identifier.tryBuild(namespace, path);
            if (id == null) throw new IOException("无效的资源标识：" + namespace + ":" + path);
            return id;
        }, (output, id) -> {
            output.writeUTF(id.getNamespace());
            output.writeUTF(id.getPath());
        });

        /** 以四字节长度开头读写字节数组，限制读写的最大字节数。 */
        public static Serializer<byte[]> bytes(int maximumLength) {
            checkMaximumLength(maximumLength);
            return new Serializer<>(input -> {
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
        public static Serializer<BigInteger> bigInteger(int maximumLength) {
            Serializer<byte[]> bytes = bytes(maximumLength);
            return new Serializer<>(input -> {
                byte[] value = bytes.read(input);
                if (value.length == 0) throw new IOException("整数二进制字段为空");
                return new BigInteger(value);
            }, (output, value) -> bytes.write(output, value.toByteArray()));
        }

        /** 兼容旧文件的四字节长度与逐字节异或布局。字符集须与旧文件写入时一致。 */
        public static Serializer<String> legacyXorString(int maximumLength, Charset charset) {
            Serializer<byte[]> bytes = bytes(maximumLength);
            return new Serializer<>(input -> new String(xor(bytes.read(input)), charset),
                    (output, value) -> bytes.write(output, xor(value.getBytes(charset))));
        }

        private static byte[] xor(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] ^= 0x43;
            }
            return bytes;
        }

        @Override
        public T read(DataInputStream input) throws IOException {
            return reader.read(input);
        }

        @Override
        public void write(DataOutputStream output, T value) throws IOException {
            writer.write(output, value);
        }
    }

    /** 在调用线程处理由外层打开的流，读写失败时由调用方接收 IOException。 */
    @FunctionalInterface
    public interface StreamAction<S> {

        /** 在流开放期间处理数据，流的关闭由调用本回调的方法负责。 */
        void accept(S stream) throws IOException;
    }
}
