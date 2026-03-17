package org.tradinggate.backend.matching.engine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.matching.engine.model.MatchResult;
import org.tradinggate.backend.matching.engine.model.OrderCommand;

/**
 * - "엔진 처리" 결과를 "Kafka 이벤트 발행"으로 변환하는 애플리케이션 서비스.
 *
 * [정합성 규칙]
 * - 본 메서드가 성공적으로 리턴되면, 해당 sourceOffset은 "처리 완료"로 간주될 수 있다.
 * - publish 중 예외가 발생하면 throw하여 리스너 레벨에서 ack/commit이 일어나지 않도록 해야 한다.
 *
 * [멱등성]
 * - Kafka 재시도로 인해 동일 레코드가 재처리될 수 있으므로,
 *   엔진(또는 소비/발행 프로토콜)에서 멱등성 기준을 반드시 유지해야 한다.
 */
@Service
@RequiredArgsConstructor
@Profile("worker")
public class MatchingWorkerService {

    private final MatchingEngine matchingEngine;
    private final MatchingEventPublisher eventPublisher;

    /**
     * @param command 처리할 주문 커맨드
     * @param currentTimeMillis 워커가 기준으로 삼는 "현재 시각"
     * @param topic source topic(추적/멱등성 힌트)
     * @param partition source partition(추적/멱등성 힌트)
     * @param offset source offset(추적/멱등성 힌트)
     * @sideEffects 오더북(메모리 상태) 변경 + Kafka output 이벤트 발행
     *
     * [규칙]
     * - engine 결과가 비어있으면 publish하지 않는다(불필요한 이벤트/노이즈 방지).
     * - publish 메서드는 실패 시 예외를 던져야 하며, 여기서 삼키면 "처리 누락"이 발생한다.
     */
    public MatchingHandleMetrics handle(OrderCommand command, long currentTimeMillis, String topic, int partition, long offset) {
        if (command == null) {
            throw new IllegalArgumentException("OrderCommand must not be null");
        }

        String symbol = command.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be null or empty");
        }

        MatchResult result = matchingEngine.handle(command, currentTimeMillis);
        long engineCompletedAtMillis = System.currentTimeMillis();
        if (result.isEmpty()) {
            return new MatchingHandleMetrics(result, currentTimeMillis, engineCompletedAtMillis, engineCompletedAtMillis);
        }

        // publish 실패는 곧 "처리 실패"로 본다 → 예외를 상위로 전달.
        if (!result.getOrderUpdates().isEmpty()) {
            eventPublisher.publishOrderUpdates(symbol, result.getOrderUpdates(), topic, partition, offset);
        }
        if (!result.getMatchFills().isEmpty()) {
            eventPublisher.publishMatchFills(symbol, result.getMatchFills(), topic, partition, offset);
        }

        long publishCompletedAtMillis = System.currentTimeMillis();
        return new MatchingHandleMetrics(result, currentTimeMillis, engineCompletedAtMillis, publishCompletedAtMillis);
    }
}
