package com.modularmc.mmclutils.serialization;

import com.modularmc.mmclutils.io.BinaryFiles;

import net.minecraft.resources.Identifier;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.UUID;

/** 原始二进制字段的读取器和写入器。变长字段由调用方设置长度上限。 */
public final class BinaryCodecs {

    public static final BinaryCodec<String> UTF = new BinaryCodec<>(input -> input.readUTF(), DataOutputStream::writeUTF);
    public static final BinaryCodec<Boolean> BOOLEAN = new BinaryCodec<>(DataInputStream::readBoolean, DataOutputStream::writeBoolean);
    public static final BinaryCodec<UUID> UUID_TEXT = new BinaryCodec<>(input -> UUID.fromString(input.readUTF()),
            (output, value) -> output.writeUTF(value.toString()));
    public static final BinaryCodec<Identifier> IDENTIFIER = new BinaryCodec<>(input -> Identifier.parse(input.readUTF()),
            (output, value) -> output.writeUTF(value.toString()));

    private BinaryCodecs() {}

    public static BinaryCodec<byte[]> bytes(int maximumLength) {
        if (maximumLength < 0) throw new IllegalArgumentException("最大长度应为非负数");
        return new BinaryCodec<>(input -> {
            int length = input.readInt();
            if (length < 0 || length > maximumLength) throw new IOException("无效的二进制字段长度：" + length);
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return bytes;
        }, (output, bytes) -> {
            if (bytes.length > maximumLength) throw new IOException("二进制字段超出长度上限");
            output.writeInt(bytes.length);
            output.write(bytes);
        });
    }

    public static BinaryCodec<BigInteger> bigInteger(int maximumLength) {
        BinaryCodec<byte[]> bytes = bytes(maximumLength);
        return new BinaryCodec<>(input -> {
            byte[] value = bytes.read(input);
            if (value.length == 0) throw new IOException("整数二进制字段为空");
            return new BigInteger(value);
        }, (output, value) -> bytes.write(output, value.toByteArray()));
    }

    /** 同一值的两个编解码方向共享约定的字节布局。 */
    public record BinaryCodec<T>(BinaryFiles.Reader<T> reader, BinaryFiles.Writer<T> writer)
            implements BinaryFiles.Reader<T>, BinaryFiles.Writer<T> {

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
