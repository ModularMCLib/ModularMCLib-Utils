package com.modularmc.mmclutils.io;

import org.jspecify.annotations.NullMarked;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 以缓冲流读写二进制文件。写入在同一目录内完成原子替换。 */
@NullMarked
public final class BinaryFiles {

    private BinaryFiles() {}

    /** 读取存在的文件。回调返回后流由本方法关闭。 */
    public static <T> T read(Path path, Reader<T> reader) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            return reader.read(input);
        }
    }

    /** 仅在输出流关闭成功后替换目标。读取方始终看到一份完整的文件。 */
    public static <T> void writeAtomically(Path path, T value, Writer<? super T> writer) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Path directory = target.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".mmclutils-", ".tmp");
        try {
            try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                writer.write(output, value);
            }
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

    /** 读取一个完整的值。调用期间输入流有效，格式错误通过 IOException 报告。 */
    @FunctionalInterface
    public interface Reader<T> {

        T read(DataInputStream input) throws IOException;
    }

    /** 按与读取器相同的布局写入值。输出流由调用方管理。 */
    @FunctionalInterface
    public interface Writer<T> {

        void write(DataOutputStream output, T value) throws IOException;
    }
}
