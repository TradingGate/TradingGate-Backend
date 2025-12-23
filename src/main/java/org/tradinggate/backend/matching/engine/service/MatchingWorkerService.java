package org.tradinggate.backend.matching.engine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.matching.engine.model.MatchResult;
import org.tradinggate.backend.matching.engine.model.OrderCommand;

@Service
@RequiredArgsConstructor
public class MatchingWorkerService {

    private final MatchingEngine matchingEngine;
    private final MatchingEventPublisher eventPublisher;

    public void handle(OrderCommand command, long currentTimeMillis, String topic, int partition, long offset) {
        if (command == null) {
            throw new IllegalArgumentException("OrderCommand must not be null");
        }

        String symbol = command.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be null or empty");
        }

        MatchResult result = matchingEngine.handle(command, currentTimeMillis);
        if (result.isEmpty()) return;

        if (!result.getOrderUpdates().isEmpty()) {
            eventPublisher.publishOrderUpdates(symbol, result.getOrderUpdates(), topic, partition, offset);
        }
        if (!result.getMatchFills().isEmpty()) {
            eventPublisher.publishMatchFills(symbol, result.getMatchFills(), topic, partition, offset);
        }
    }
}
