package org.tradinggate.backend.matching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.matching.domain.MatchResult;
import org.tradinggate.backend.matching.domain.OrderCommand;
import org.tradinggate.backend.matching.engine.MatchingEngine;

@Service
@RequiredArgsConstructor
public class MatchingWorkerService {

    private final MatchingEngine matchingEngine;
    private final MatchingEventPublisher eventPublisher;

    /**
     * 외부에서 현재 시각을 따로 넣지 않고 사용할 때 편의용 메서드
     */
    public void handle(OrderCommand command) {
        long nowMillis = System.currentTimeMillis();
        handle(command, nowMillis);
    }

    /**
     * 매칭 워커 애플리케이션 서비스의 핵심 진입점.
     * - OrderCommand → MatchingEngine.handle
     * - MatchResult → MatchingEventPublisher 로 전달
     */
    public void handle(OrderCommand command, long currentTimeMillis) {
        if (command == null) {
            throw new IllegalArgumentException("OrderCommand must not be null");
        }

        String symbol = command.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            // TODO: 잘못된 심볼 커맨드에 대한 처리 정책 (로그만 남기고 무시 or REJECT 이벤트 발행)
            return;
        }

        MatchResult result = matchingEngine.handle(command, currentTimeMillis);

        if (result.isEmpty()) {
            // 매칭/상태 변경 결과가 없으면 아무것도 발행하지 않는다.
            return;
        }

        if (!result.getOrderUpdates().isEmpty()) {
            eventPublisher.publishOrderUpdates(symbol, result.getOrderUpdates());
        }

        if (!result.getMatchFills().isEmpty()) {
            eventPublisher.publishMatchFills(symbol, result.getMatchFills());
        }
    }
}
