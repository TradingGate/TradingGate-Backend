package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingResultRow;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingCalculator;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingInputsPort;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingInputsPort.AccountAsset;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingInputsPort.BalanceSnapshot;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingInputsPort.LedgerAgg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 고정된 배치 워터마크 기준으로 ledger 기반 B-5 v1 정산 결과 행을 계산한다.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Profile("clearing")
public class DefaultClearingCalculator implements ClearingCalculator {

    private final ClearingInputsPort inputs;

    @Override
    public List<ClearingResultRow> calculate(ClearingComputationContext ctx) {
        requireContext(ctx);

        // 입력 포트가 동일 워터마크 기준으로 scope 반영 유니버스를 구성한다.
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

            BalanceSnapshot closing = inputs.loadClosingBalance(ctx, accountId, asset);
            if (closing == null) {
                throw new IllegalStateException("closingBalance is null. batchId=" + ctx.batchId()
                        + ", accountId=" + accountId + ", asset=" + asset);
            }

            BalanceSnapshot opening = inputs.loadOpeningBalance(ctx, accountId, asset);

            // 집계값은 inputs 구현에서 이미 워터마크 기준으로 필터링되어 있어야 한다.
            LedgerAgg agg = inputs.aggregateLedger(ctx, accountId, asset);
            if (agg == null) {
                throw new IllegalStateException("ledgerAgg is null. batchId=" + ctx.batchId()
                        + ", accountId=" + accountId + ", asset=" + asset);
            }

            BigDecimal closingTotal = safe(closing.totalBalance());
            BigDecimal periodNetChange = safe(agg.periodNetChange());
            BigDecimal openingTotal = (opening == null || opening.totalBalance() == null)
                    ? closingTotal.subtract(periodNetChange)
                    : opening.totalBalance();
            BigDecimal netChange = periodNetChange;

            // 잔고/변화가 모두 0인 row는 결과 노이즈를 줄이기 위해 제외한다.
            if (isZeroNoiseRow(openingTotal, closingTotal, netChange, agg)) {
                continue;
            }

            validateRow(accountId, asset, openingTotal, closingTotal, netChange, agg, ctx);

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

    private boolean isZeroNoiseRow(BigDecimal openingTotal, BigDecimal closingTotal, BigDecimal netChange, LedgerAgg agg) {
        return openingTotal.compareTo(BigDecimal.ZERO) == 0
                && closingTotal.compareTo(BigDecimal.ZERO) == 0
                && netChange.compareTo(BigDecimal.ZERO) == 0
                && safe(agg.feeTotal()).compareTo(BigDecimal.ZERO) == 0
                && agg.tradeCount() == 0
                && safe(agg.tradeValue()).compareTo(BigDecimal.ZERO) == 0;
    }

    private void validateRow(Long accountId, String asset,
                             BigDecimal openingTotal, BigDecimal closingTotal, BigDecimal netChange,
                             LedgerAgg agg, ClearingComputationContext ctx) {
        // 계산 불일치는 조용히 저장하지 않고 즉시 실패시켜 배치를 FAILED로 종료한다.
        BigDecimal expectedClosing = openingTotal.add(netChange);
        if (expectedClosing.compareTo(closingTotal) != 0) {
            throw new IllegalStateException("clearing sanity check failed (opening+net!=closing). batchId=" + ctx.batchId()
                    + ", accountId=" + accountId + ", asset=" + asset
                    + ", opening=" + openingTotal + ", netChange=" + netChange + ", closing=" + closingTotal);
        }
        if (agg.tradeCount() < 0) {
            throw new IllegalStateException("clearing sanity check failed (tradeCount<0). batchId=" + ctx.batchId()
                    + ", accountId=" + accountId + ", asset=" + asset + ", tradeCount=" + agg.tradeCount());
        }
        if (safe(agg.tradeValue()).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("clearing sanity check failed (tradeValue<0). batchId=" + ctx.batchId()
                    + ", accountId=" + accountId + ", asset=" + asset + ", tradeValue=" + agg.tradeValue());
        }
        if (safe(agg.feeTotal()).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("clearing sanity check failed (feeTotal<0). batchId=" + ctx.batchId()
                    + ", accountId=" + accountId + ", asset=" + asset + ", feeTotal=" + agg.feeTotal());
        }
        if (agg.tradeCount() == 0 && safe(agg.tradeValue()).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("clearing sanity check failed (tradeCount==0 but tradeValue!=0). batchId=" + ctx.batchId()
                    + ", accountId=" + accountId + ", asset=" + asset + ", tradeValue=" + agg.tradeValue());
        }
    }
}
