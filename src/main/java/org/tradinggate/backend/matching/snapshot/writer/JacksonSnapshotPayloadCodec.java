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

/**
 * - PartitionSnapshot을 JSON으로 직렬화하고, 압축/체크섬을 계산해 저장 payload를 만든다.
 *
 * [정책]
 * - checksum은 "압축된 바이트" 기준으로 계산.
 *   (전송/저장 단위가 gzipped payload이므로 파일 무결성 검증이 단순해짐)
 */
public class JacksonSnapshotPayloadCodec implements SnapshotPayloadCodec {
    private final ObjectMapper objectMapper;

    public JacksonSnapshotPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @return gzipped JSON bytes + sha256Hex(압축 바이트 기준)
     */
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
