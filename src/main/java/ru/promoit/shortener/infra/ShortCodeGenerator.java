package ru.promoit.shortener.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ShortCodeGenerator {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private final AtomicLong counter = new AtomicLong(1);

    public String generate(String originalUrl, UUID userUuid, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = userUuid + ":" + originalUrl + ":" + counter.getAndIncrement() + ":" + System.nanoTime();
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                int index = Byte.toUnsignedInt(hash[i % hash.length]) % BASE62.length();
                sb.append(BASE62.charAt(index));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм SHA-256 недоступен", e);
        }
    }
}
