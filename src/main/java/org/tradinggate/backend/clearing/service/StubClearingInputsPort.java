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
    public List<AccountSymbol> resolveUniverse(ClearingComputationContext ctx) {
        // 왜: scope 파싱은 ctx 생성 시 1회만 수행하는 게 목표지만, 혹시 ctx.scopeSpec이 null로 들어와도 안전하게 동작시킨다.
        ClearingScopeSpec spec = Objects.requireNonNull(ctx.scopeSpec(), () -> "scopeSpec is null in ctx. batchId=" + ctx.batchId());
        log.debug("[CLEARING] universe resolved by stub. scope={} specType={}", ctx.scopeRaw(), spec.type());

        List<AccountSymbol> base = List.of(
                new AccountSymbol(1001L, 1L),
                new AccountSymbol(1001L, 2L),
                new AccountSymbol(1002L, 1L)
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
            case SYMBOL_SET -> {
                Set<Long> allowed = spec.symbolIds().stream().collect(Collectors.toSet());
                yield base.stream()
                        .filter(x -> allowed.contains(x.symbolId()))
                        .toList();
            }
            case CHUNK -> {
                int index = spec.chunk().index();
                int total = spec.chunk().total();
                yield base.stream()
                        .filter(x -> {
                            // 왜: chunk는 accountId 해시 기반으로 분산하여 대상 유니버스를 샤딩한다(NFR-C-03).
                            int h = Long.hashCode(x.accountId());
                            return Math.floorMod(h, total) == index;
                        })
                        .toList();
            }
        };
    }

    @Override
    public OpeningPosition loadOpening(ClearingComputationContext ctx, Long accountId, Long symbolId) {
        LocalDate businessDate = ctx.businessDate();
        log.debug("[CLEARING] opening resolved by stub. date={} accountId={} symbolId={}", businessDate, accountId, symbolId);
        return new OpeningPosition(BigDecimal.ZERO, null);
    }

    @Override
    public TradeAgg aggregateTrades(ClearingComputationContext ctx, Long accountId, Long symbolId) {
        // cutoffOffsets는 정합성 기준점. stub에서도 null이면 사고이므로 fail-fast 한다.
        if (ctx.cutoffOffsets() == null) {
            throw new IllegalStateException("cutoffOffsets is null in ctx. batchId=" + ctx.batchId());
        }
        log.debug("[CLEARING] tradeAgg resolved by stub. date={} accountId={} symbolId={} cutoffKeys={}",
                ctx.businessDate(), accountId, symbolId, ctx.cutoffOffsets().keySet());
        long salt = ctx.cutoffOffsets().values().stream().mapToLong(Long::longValue).sum();
        BigDecimal netQty = BigDecimal.valueOf(salt % 3); // 0~2 사이
        return new TradeAgg(netQty, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public SymbolInfo loadSymbolInfo(ClearingComputationContext ctx, Long symbolId) {
        log.debug("[CLEARING] symbolInfo resolved by stub. symbolId={}", symbolId);
        return new SymbolInfo(symbolId == 2L ? ProductType.DERIVATIVE : ProductType.SPOT);
    }

    @Override
    public PriceSnapshot loadPriceSnapshot(ClearingComputationContext ctx, Long symbolId) {
        if (ctx.marketSnapshotId() == null) {
            throw new IllegalStateException("marketSnapshotId is null in ctx. batchId=" + ctx.batchId());
        }
        log.debug("[CLEARING] priceSnapshot resolved by stub. snapshotId={} symbolId={}", ctx.marketSnapshotId(), symbolId);
        if (symbolId == 2L) {
            return new PriceSnapshot(
                    BigDecimal.valueOf(101),
                    BigDecimal.valueOf(102),
                    BigDecimal.valueOf(103),
                    BigDecimal.valueOf(104)
            );
        }
        return new PriceSnapshot(
                BigDecimal.valueOf(201),
                BigDecimal.valueOf(202),
                BigDecimal.valueOf(203),
                BigDecimal.valueOf(204)
        );
    }
}
