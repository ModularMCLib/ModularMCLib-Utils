package com.modularmc.mmclutils.utils;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/** 文件与流读写、目录操作、路径互斥及内容摘要。 */
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

    /** 读取一个完整的值。调用期间输入流有效，格式错误通过 IOException 报告。 */
    @FunctionalInterface
    public interface Reader<T> {

        /** 从当前流位置解码一个值，遇到截断或格式错误时抛出 IOException。 */
        T read(DataInputStream input) throws IOException;
    }

    /** 按与读取器相同的布局编码一个值。在调用线程完成写入，由外层管理流的生命周期。 */
    @FunctionalInterface
    public interface Writer<T> {

        /** 从当前流位置写入值，输出失败时抛出 IOException。 */
        void write(DataOutputStream output, T value) throws IOException;
    }

    /** 在调用线程处理由外层打开的流，读写失败时由调用方接收 IOException。 */
    @FunctionalInterface
    public interface StreamAction<S> {

        /** 在流开放期间处理数据，流的关闭由调用本回调的方法负责。 */
        void accept(S stream) throws IOException;
    }
}
