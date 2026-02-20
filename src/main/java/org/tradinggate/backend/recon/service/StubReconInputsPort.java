package org.tradinggate.backend.recon.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.recon.domain.ReconBatch;
import org.tradinggate.backend.recon.dto.ReconRow;
import org.tradinggate.backend.recon.service.port.ReconInputsPort;

import java.math.BigDecimal;
import java.util.List;

@Log4j2
@Component
@Profile("recon")
public class StubReconInputsPort implements ReconInputsPort {

    @Override
    public List<ReconRow> loadSnapshot(ReconBatch reconBatch) {
        // TODO: 실제 구현에서는 clearing_result(batchId 기준) 읽기
        return List.of(
                new ReconRow(1001L, "USDT", null, bd("1000"), null, bd("3"), 10L, bd("5000")),
                new ReconRow(1002L, "USDT", null, bd("2000"), null, bd("2"), 5L, bd("1000"))
        );
    }

    @Override
    public List<ReconRow> loadTruth(ReconBatch reconBatch) {
        // TODO: 실제 구현에서는 B-1 truth 테이블(예: account_balance_truth, ledger 집계 등) 읽기
        return List.of(
                new ReconRow(1001L, "USDT", null, bd("1000"), null, bd("3"), 10L, bd("5000")),
                new ReconRow(1002L, "USDT", null, bd("1999"), null, bd("2"), 5L, bd("1000")) // mismatch 예시
        );
    }

    private BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
