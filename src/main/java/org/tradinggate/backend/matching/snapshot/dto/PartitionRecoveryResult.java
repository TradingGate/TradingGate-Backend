package org.tradinggate.backend.matching.snapshot.dto;

import java.util.List;

public record PartitionRecoveryResult(
        boolean recovered,
        int recoveredSymbols,
        long seekStartOffset,
        List<RecoveredOne> details
) {

    public static PartitionRecoveryResult none() {
        return new PartitionRecoveryResult(
                false,
                0,
                -1L,
                List.of()
        );
    }

    public static PartitionRecoveryResult recovered(
            int recoveredSymbols,
            long seekStartOffset,
            List<RecoveredOne> details
    ) {
        return new PartitionRecoveryResult(
                true,
                recoveredSymbols,
                seekStartOffset,
                details
        );
    }
}
