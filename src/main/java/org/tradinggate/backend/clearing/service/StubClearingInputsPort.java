package org.tradinggate.backend.clearing.service;

import lombok.extern.log4j.Log4j2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.clearing.dto.ClearingScopeSpec;
import org.tradinggate.backend.clearing.service.port.ClearingInputsPort;
import org.tradinggate.backend.clearing.policy.ClearingScopeSpecParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 왜: 실데이터(ledger/포지션/심볼/시세) 연동 전에도 Clearing 파이프라인을 end-to-end로 검증하기 위한 스텁 입력 포트 구현체.
 * 규칙: scope v1을 파싱해 유니버스(AccountSymbol)를 필터링한다(ALL/account range/symbol set/chunk).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class StubClearingInputsPort implements ClearingInputsPort {

    private final ClearingScopeSpecParser scopeParser;

    @Override
    public List<AccountSymbol> resolveUniverse(String scope) {
        // TODO(B-5): 포지션/계정 레지스트리 기반으로 대상 확정하도록 교체
        ClearingScopeSpec spec = scopeParser.parse(scope);
        log.info("[CLEARING] universe resolved by stub. scope={} specType={}", scope, spec.type());

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
    public OpeningPosition loadOpening(LocalDate businessDate, Long accountId, Long symbolId) {
        // TODO(B-5): 전일 EOD 결과/포지션 스냅샷 연동
        log.info("[CLEARING] opening resolved by stub. date={} accountId={} symbolId={}", businessDate, accountId, symbolId);
        return new OpeningPosition(BigDecimal.ZERO, null);
    }

    @Override
    public TradeAgg aggregateTrades(LocalDate businessDate, Long accountId, Long symbolId, Map<String, Long> cutoffOffsets) {
        // TODO(B-5): trading_trade 집계 + cutoff 기준 반영하도록 교체
        log.info("[CLEARING] tradeAgg resolved by stub. date={} accountId={} symbolId={}", businessDate, accountId, symbolId);
        return new TradeAgg(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    public SymbolInfo loadSymbolInfo(Long symbolId) {
        // TODO(B-5): symbol master 연동
        log.info("[CLEARING] symbolInfo resolved by stub. symbolId={}", symbolId);
        return new SymbolInfo(ProductType.SPOT);
    }

    @Override
    public PriceSnapshot loadPriceSnapshot(Long marketSnapshotId, Long symbolId) {
        // TODO(B-5): market_data_snapshot 연동
        log.info("[CLEARING] priceSnapshot resolved by stub. snapshotId={} symbolId={}", marketSnapshotId, symbolId);
        BigDecimal p = BigDecimal.valueOf(100);
        return new PriceSnapshot(p, p, p, p);
    }
}
