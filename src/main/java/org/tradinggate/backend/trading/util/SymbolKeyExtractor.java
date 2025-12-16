package org.tradinggate.backend.trading.util;

import org.springframework.stereotype.Component;
import org.tradinggate.backend.trading.kafka.event.OrderCancelEvent;
import org.tradinggate.backend.trading.kafka.event.OrderEvent;

/**
 * [A-1] Trading API - Symbol Key 추출
 *
 * 역할:
 * - Kafka 발행 시 key 추출
 * - ⚠️ key = symbol (같은 심볼은 같은 파티션으로) ⚠️
 *
 * 참고: PDF 1-1 (key = symbol)
 */
@Component
public class SymbolKeyExtractor {

  public String extractKey(OrderEvent event) {
    return event.getSymbol();
  }

  public String extractKey(OrderCancelEvent event) {
    return event.getSymbol();
  }
}
