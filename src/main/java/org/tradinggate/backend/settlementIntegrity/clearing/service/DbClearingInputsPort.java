package org.tradinggate.backend.settlementIntegrity.clearing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.settlementIntegrity.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingBatchType;
import org.tradinggate.backend.settlementIntegrity.clearing.domain.e.ClearingResultStatus;
import org.tradinggate.backend.settlementIntegrity.clearing.repository.ClearingResultRepository;
import org.tradinggate.backend.settlementIntegrity.clearing.service.port.ClearingInputsPort;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Primary
@Component
@RequiredArgsConstructor
@Profile("clearing")
public class DbClearingInputsPort implements ClearingInputsPort {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final ClearingResultRepository clearingResultRepository;

    private final Map<Long, DailyLedgerCache> dailyLedgerCacheByBatchId = new ConcurrentHashMap<>();

    private record LedgerKey(Long accountId, String asset) {}

    private record DailyLedgerCache(
            List<AccountAsset> activeUniverse,
            Map<LedgerKey, LedgerAgg> aggByKey
    ) {}

    @Override
    public List<AccountAsset> resolveUniverse(ClearingComputationContext ctx) {
        Long maxLedgerId = ctx.cutoffOffsets().getOrDefault("max_ledger_id", Long.MAX_VALUE);
        DailyLedgerCache daily = loadDailyLedgerCache(ctx);

        Set<AccountAsset> universe = daily.activeUniverse().stream()
                .collect(Collectors.toSet());

        // carry-over 계정도 같은 워터마크 스냅샷 기준으로 포함해 live projection(account_balance) 혼입을 막는다.
        ledgerEntryRepository.sumAllHoldingsUpTo(maxLedgerId).forEach(row ->
                universe.add(new AccountAsset((Long) row[0], normalizeAsset((String) row[1]))));

        return new ArrayList<>(universe);
    }

    @Override
    public BalanceSnapshot loadOpeningBalance(ClearingComputationContext ctx, Long accountId, String asset) {
        // opening은 가장 최근의 이전 EOD FINAL snapshot closing에서 이어받는다.
        List<BigDecimal> previous = clearingResultRepository.findPreviousClosingBalances(
                ctx.businessDate(),
                accountId,
                asset,
                ClearingResultStatus.FINAL,
                ClearingBatchType.EOD
        );

        if (previous.isEmpty()) {
            return null;
        }

        BigDecimal total = previous.get(0);
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO : total;
        return new BalanceSnapshot(safeTotal, safeTotal, BigDecimal.ZERO);
    }

    @Override
    public BalanceSnapshot loadClosingBalance(ClearingComputationContext ctx, Long accountId, String asset) {
        // closing snapshot은 live account_balance가 아니라 배치 워터마크 시점의 ledger 누적합으로 계산한다.
        Long maxLedgerId = ctx.cutoffOffsets().getOrDefault("max_ledger_id", Long.MAX_VALUE);

        BigDecimal closingBalanceAmount = ledgerEntryRepository.sumByAccountIdAndAssetUpToId(accountId, asset,
                maxLedgerId);

        // v1은 total 기준만 다루므로 available/locked 분리는 의도적으로 생략한다.
        return new BalanceSnapshot(closingBalanceAmount, closingBalanceAmount, BigDecimal.ZERO);
    }

    @Override
    public LedgerAgg aggregateLedger(ClearingComputationContext ctx, Long accountId, String asset) {
        DailyLedgerCache daily = loadDailyLedgerCache(ctx);
        return daily.aggByKey().getOrDefault(
                new LedgerKey(accountId, normalizeAsset(asset)),
                new LedgerAgg(BigDecimal.ZERO, BigDecimal.ZERO, 0L, BigDecimal.ZERO)
        );
    }

    private DailyLedgerCache loadDailyLedgerCache(ClearingComputationContext ctx) {
        return dailyLedgerCacheByBatchId.computeIfAbsent(ctx.batchId(), ignored -> {
            LocalDate date = ctx.businessDate();
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
            Long maxLedgerId = ctx.cutoffOffsets().getOrDefault("max_ledger_id", Long.MAX_VALUE);

            List<Object[]> aggregated = ledgerEntryRepository.aggregateDailyLedger(startOfDay, endOfDay, maxLedgerId);

            List<AccountAsset> activeUniverse = new ArrayList<>(aggregated.size());
            Map<LedgerKey, LedgerAgg> aggByKey = new HashMap<>(Math.max(16, aggregated.size() * 2));

            for (Object[] row : aggregated) {
                Long rowAccountId = (Long) row[0];
                String rowAsset = normalizeAsset((String) row[1]);
                BigDecimal periodNetChange = safe((BigDecimal) row[2]);
                BigDecimal feeTotal = safe((BigDecimal) row[3]);
                BigDecimal tradeValue = safe((BigDecimal) row[4]);
                long tradeCount = row[5] == null ? 0L : ((Number) row[5]).longValue();

                activeUniverse.add(new AccountAsset(rowAccountId, rowAsset));
                aggByKey.put(
                        new LedgerKey(rowAccountId, rowAsset),
                        new LedgerAgg(periodNetChange, feeTotal, tradeCount, tradeValue)
                );
            }

            return new DailyLedgerCache(activeUniverse, aggByKey);
        });
    }

    private String normalizeAsset(String asset) {
        return asset == null ? null : asset.toUpperCase();
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
