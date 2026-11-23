package com.modularmc.mmclutils.io;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Creates stable SHA-256 identifiers without exposing server addresses or filesystem paths in client config. */
public final class Digests {

    private Digests() {}

    /** Hashes the exact UTF-8 value and prefixes the lower-case digest with its algorithm. */
    public static String sha256(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
