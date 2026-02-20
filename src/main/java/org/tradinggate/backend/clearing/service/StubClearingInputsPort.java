package org.tradinggate.backend.clearing.service;

import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.dto.ClearingComputationContext;
import org.tradinggate.backend.clearing.dto.ClearingScopeSpec;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;
import org.tradinggate.backend.clearing.service.support.ClearingScopeSpecParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 왜: 실데이터(ledger/포지션/심볼/시세) 연동 전에도 Clearing 파이프라인을 end-to-end로 검증하기 위한 스텁 입력 포트 구현체.
 * 규칙: scope v1을 파싱해 유니버스(AccountSymbol)를 필터링한다(ALL/account range/symbol set/chunk).
 */
@Log4j2
@Component
@RequiredArgsConstructor
@Profile("clearing")
public class StubClearingInputsPort implements ClearingInputsPort {

    @Override
    public List<AccountAsset> resolveUniverse(ClearingComputationContext ctx) {
        ClearingScopeSpec spec = Objects.requireNonNull(
                ctx.scopeSpec(),
                () -> "scopeSpec is null in ctx. batchId=" + ctx.batchId()
        );

        log.debug("[CLEARING] universe resolved by stub. scope batchId={} specType={}",
                ctx.batchId(), spec.type());

        // v2는 (account, asset)
        List<AccountAsset> base = List.of(
                new AccountAsset(1001L, "USDT"),
                new AccountAsset(1001L, "BTC"),
                new AccountAsset(1002L, "USDT")
        );

        return switch (spec.type()) {
            case ALL -> base;

            case ACCOUNT_RANGE -> {
                long from = spec.accountRange().fromInclusive();
                long to = spec.accountRange().toInclusive();
                yield base.stream()
                        .filter(x -> x.accountId() >= from && x.accountId() <= to)
                        .toList();
            }

            case CHUNK -> {
                int index = spec.chunk().index();
                int total = spec.chunk().total();
                yield base.stream()
                        .filter(x -> {
                            int h = Long.hashCode(x.accountId());
                            return Math.floorMod(h, total) == index;
                        })
                        .toList();
            }

            // v2에서는 symbol 기반 스코프가 의미가 없어서 사고 방지용 fail-fast 추천
            case SYMBOL_SET -> throw new IllegalArgumentException(
                    "SYMBOL_SET scope is not supported in v2 clearing. scope batchId=" + ctx.batchId()
            );
        };
    }

    @Override
    public BalanceSnapshot loadOpeningBalance(ClearingComputationContext ctx, Long accountId, String asset) {
        log.debug("[CLEARING] openingBalance resolved by stub. date={} accountId={} asset={}",
                ctx.businessDate(), accountId, asset);

        // MVP: opening이 없으면 null 허용이므로 그냥 null로 둬도 OK.
        // 데모에서 netChange를 보고 싶으면 0을 넣어도 됨.
        return null;
    }

    @Override
    public BalanceSnapshot loadClosingBalance(ClearingComputationContext ctx, Long accountId, String asset) {
        log.debug("[CLEARING] closingBalance resolved by stub. date={} accountId={} asset={}", ctx.businessDate(), accountId, asset);

        // 데모용: accountId/asset에 따라 deterministic balance 생성
        BigDecimal base = BigDecimal.valueOf(accountId % 1000); // 1~999
        BigDecimal total = "USDT".equals(asset) ? base.multiply(BigDecimal.valueOf(100)) : base;
        BigDecimal available = total.multiply(BigDecimal.valueOf(0.9));
        BigDecimal locked = total.subtract(available);

        return new BalanceSnapshot(total, available, locked);
    }

    @Override
    public LedgerAgg aggregateLedger(ClearingComputationContext ctx, Long accountId, String asset) {
        if (ctx.cutoffOffsets() == null) {
            throw new IllegalStateException("cutoffOffsets is null in ctx. batchId=" + ctx.batchId());
        }

        long salt = ctx.cutoffOffsets().values().stream().mapToLong(Long::longValue).sum();
        log.debug("[CLEARING] ledgerAgg resolved by stub. date={} accountId={} asset={} cutoffKeys={}",
                ctx.businessDate(), accountId, asset, ctx.cutoffOffsets().keySet());

        // 데모용: watermark에 따라 변하는 값
        BigDecimal feeTotal = BigDecimal.valueOf(salt % 5);          // 0~4
        long tradeCount = salt % 20;                                 // 0~19
        BigDecimal tradeValue = BigDecimal.valueOf((salt % 100) * 10);// 0~990

        return new LedgerAgg(feeTotal, tradeCount, tradeValue);
    }
}
