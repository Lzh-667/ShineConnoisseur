package com.lzh.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Delivery identity is independent of notification business deduplication. */
public final class MessageIdentity {
    private MessageIdentity() {}

    public static String delivery(String messageId, byte[] body) {
        return hash((messageId == null ? "legacy" : "id:" + messageId) + ":" + digest(body));
    }

    public static String hash(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
