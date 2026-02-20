package org.tradinggate.backend.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.clearing.dto.*;
import org.tradinggate.backend.clearing.service.port.ClearingCalculator;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.AccountAsset;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.BalanceSnapshot;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort.LedgerAgg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Clearing 배치 1건에 대해 (accountId, symbolId) 단위 정산 결과를 계산한다.
 * 입력(포지션/체결집계/가격)은 포트로 추상화하여, 이후 실데이터 소스(DB ledger 등)로 교체해도 계산기의 흐름이 흔들리지 않게 한다.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class DefaultClearingCalculator implements ClearingCalculator {

    private final ClearingInputsPort inputs;

    /**
     * v2 정산(스냅샷)의 정의:
     * - RUNNING 선점 시점에 고정된 watermarkOffsets + snapshotKey 기준으로
     * - (accountId, asset) 단위로 "잔고 스냅샷 + 거래 요약"을 생성한다.
     */
    @Override
    public List<ClearingResultRow> calculate(ClearingComputationContext ctx) {
        requireContext(ctx);

        // scope 반영된 (accountId, asset) 유니버스
        List<AccountAsset> universe = inputs.resolveUniverse(ctx);
        if (universe == null) {
            throw new IllegalStateException("universe is null. batchId=" + ctx.batchId());
        }
        if (universe.isEmpty()) {
            log.info("[CLEARING] universe empty. batchId={} snapshotKey={}", ctx.batchId(), ctx.snapshotKey());
            return List.of();
        }

        List<ClearingResultRow> out = new ArrayList<>(universe.size());

        for (AccountAsset u : universe) {
            Long accountId = u.accountId();
            String asset = u.asset();

            // closingBalance는 "지금 DB 상태"를 찍는다.
            // 재현성은 '워터마크가 고정된 상태에서 찍었다'는 점에서 확보된다.
            BalanceSnapshot closing = inputs.loadClosingBalance(ctx, accountId, asset);
            if (closing == null) {
                throw new IllegalStateException("closingBalance is null. batchId=" + ctx.batchId()
                        + ", accountId=" + accountId + ", asset=" + asset);
            }

            // openingBalance는 MVP에선 null이어도 OK (전일 EOD FINAL 없으면 null)
            BalanceSnapshot opening = inputs.loadOpeningBalance(ctx, accountId, asset);

            // 거래 요약(수수료/거래대금/건수 등)은 inputs가 책임진다.
            // ※ '워터마크 기준' 범위 필터링은 inputs 구현이 수행.
            LedgerAgg agg = inputs.aggregateLedger(ctx, accountId, asset);
            if (agg == null) {
                throw new IllegalStateException("ledgerAgg is null. batchId=" + ctx.batchId()
                        + ", accountId=" + accountId + ", asset=" + asset);
            }

            BigDecimal openingTotal = (opening == null || opening.totalBalance() == null) ? BigDecimal.ZERO : opening.totalBalance();
            BigDecimal closingTotal = safe(closing.totalBalance());
            BigDecimal netChange = closingTotal.subtract(openingTotal);

            out.add(new ClearingResultRow(
                    accountId,
                    asset,
                    openingTotal,
                    closingTotal,
                    netChange,
                    safe(agg.feeTotal()),
                    agg.tradeCount(),
                    safe(agg.tradeValue())
            ));
        }

        return out;
    }

    private void requireContext(ClearingComputationContext ctx) {
        if (ctx == null) throw new IllegalStateException("ctx is null");
        if (ctx.batchId() == null) throw new IllegalStateException("batchId is null");
        if (ctx.snapshotKey() == null || ctx.snapshotKey().isBlank()) {
            throw new IllegalStateException("snapshotKey is blank. batchId=" + ctx.batchId());
        }
        if (ctx.cutoffOffsets() == null || ctx.cutoffOffsets().isEmpty()) {
            throw new IllegalStateException("watermarkOffsets is empty. batchId=" + ctx.batchId());
        }
        if (ctx.businessDate() == null) {
            throw new IllegalStateException("businessDate is null. batchId=" + ctx.batchId());
        }
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}