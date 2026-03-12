package org.tradinggate.backend.matching.snapshot.util;

import org.apache.kafka.common.utils.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SnapshotCryptoUtils {

    public static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    public static byte[] gunzip(byte[] gz) throws IOException {
        try (GZIPInputStream gis =
                     new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return gis.readAllBytes();
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static int resolve(String key, int partitionCount) {
        if (partitionCount <= 0) throw new IllegalArgumentException("partitionCount must be > 0");
        if (key == null) return 0;
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return Utils.toPositive(Utils.murmur2(bytes)) % partitionCount;
    }
}
