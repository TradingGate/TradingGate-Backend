package org.tradinggate.backend.recon.domain.e;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReconFailureCode {
    BATCH_NOT_ACQUIRED("R-SKIP-001", false),
    BATCH_ALREADY_RUNNING("R-SKIP-002", false),
    BATCH_ALREADY_SUCCEEDED("R-SKIP-003", false),

    INPUT_LOAD_FAILED("R-FAIL-001", true),
    DIFF_WRITE_FAILED("R-FAIL-002", true),
    UNEXPECTED_ERROR("R-FAIL-999", true);

    private final String code;
    private final boolean retryable;
}
