package org.tradinggate.backend.matching.snapshot.writer;

import org.tradinggate.backend.matching.snapshot.model.OrderBookSnapshot;
import org.tradinggate.backend.matching.snapshot.model.PartitionSnapshot;
import org.tradinggate.backend.matching.snapshot.model.e.ChecksumAlgorithm;
import org.tradinggate.backend.matching.snapshot.model.e.CompressionType;
import org.tradinggate.backend.matching.snapshot.dto.SnapshotPayload;

public interface SnapshotPayloadCodec {

    SnapshotPayload encode(PartitionSnapshot snapshot, CompressionType compressionType, ChecksumAlgorithm checksumAlgorithm) throws Exception;
}
