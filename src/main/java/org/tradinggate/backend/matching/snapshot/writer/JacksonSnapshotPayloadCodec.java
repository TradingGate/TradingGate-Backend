package org.tradinggate.backend.matching.snapshot.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tradinggate.backend.matching.snapshot.model.OrderBookSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotPayload;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

public class JacksonSnapshotPayloadCodec implements SnapshotPayloadCodec {
    private final ObjectMapper objectMapper;

    public JacksonSnapshotPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SnapshotPayload encode(
            PartitionSnapshot snapshot,
            CompressionType compressionType,
            ChecksumAlgorithm checksumAlgorithm
    ) throws Exception {

        byte[] jsonBytes = objectMapper.writeValueAsBytes(snapshot);

        byte[] compressed;
        if (compressionType == CompressionType.GZIP) {
            compressed = SnapshotCryptoUtils.gzip(jsonBytes);
        } else {
            throw new UnsupportedOperationException("Unsupported compression: " + compressionType);
        }

        String checksum;
        if (checksumAlgorithm == ChecksumAlgorithm.SHA_256) {
            checksum = SnapshotCryptoUtils.sha256Hex(compressed);
        } else {
            throw new UnsupportedOperationException("Unsupported checksum: " + checksumAlgorithm);
        }

        return new SnapshotPayload(compressed, checksum);
    }
}
