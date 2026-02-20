package org.tradinggate.backend.clearing.domain.e;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClearingFailureCode {

    // === 실행 제어/정상 스킵(실패 아님) ===
    BATCH_NOT_ACQUIRED("C-SKIP-001", false),
    BATCH_ALREADY_RUNNING("C-SKIP-002", false),
    BATCH_ALREADY_SUCCEEDED("C-SKIP-003", false),
    FAILED_BATCH_REQUIRES_RESET("C-SKIP-004", false),

    WATERMARK_NOT_AVAILABLE("C-SKIP-005", false),

    // === 실행 실패(재시도 가치 여부 구분) ===
    WATERMARK_RESOLVE_FAILED("C-FAIL-001", true),
    INPUT_QUERY_FAILED("C-FAIL-002", true),      // account_balance/ledger 집계 조회 실패
    RESULT_WRITE_FAILED("C-FAIL-003", true),
    OUTBOX_APPEND_FAILED("C-FAIL-004", true),

    // === 예상 못한 실패 ===
    UNEXPECTED_ERROR("C-FAIL-999", true);

    private final String code;
    private final boolean retryable;
}
