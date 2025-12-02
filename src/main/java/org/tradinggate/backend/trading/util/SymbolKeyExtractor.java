package org.tradinggate.backend.trading.util;

import org.springframework.stereotype.Component;

/**
 * [A-1] Trading API - Symbol Key 추출
 *
 * 역할:
 * - Kafka 발행 시 key 추출
 * - ⚠️ key = symbol (같은 심볼은 같은 파티션으로) ⚠️
 *
 * TODO:
 * [ ] extractKey(OrderEvent) 메서드 구현:
 *     public String extractKey(OrderEvent event) {
 *         return event.getSymbol();
 *     }
 *
 * [ ] extractKey(OrderCancelEvent) 메서드 구현:
 *     public String extractKey(OrderCancelEvent event) {
 *         return event.getSymbol();
 *     }
 *
 * 참고: PDF 1-1 (key = symbol)
 */
@Component
public class SymbolKeyExtractor {

  // TODO: extractKey() 구현
}
