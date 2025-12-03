package org.tradinggate.backend.trading.api.validator;

import org.springframework.stereotype.Component;

/**
 * [A-1] Trading API - 주문 검증
 *
 * 역할:
 * - 심볼별 틱/스텝 규칙 검증
 * - 주문 타입별 필수 필드 검증
 *
 * TODO:
 * [ ] validate(OrderCreateRequest) 메서드 구현:
 *     1. Symbol 존재 여부 확인
 *        - SymbolRepository.findBySymbol() 또는 Redis 캐시 조회
 *        - 없으면 SymbolNotFoundException
 *     2. 가격이 priceTick 배수인지 확인
 *        - price % priceTick == 0
 *        - 아니면 InvalidOrderException("Price must be multiple of tick")
 *     3. 수량이 qtyStep 배수인지 확인
 *        - quantity % qtyStep == 0
 *     4. 주문 타입별 검증:
 *        - LIMIT: price 필수
 *        - MARKET: price 무시
 *     5. 수량/가격 범위 검증 (최소/최대)
 *
 * [ ] SymbolRepository 또는 Redis 캐시 조회
 *
 * [ ] 예외: InvalidOrderException, SymbolNotFoundException
 *
 * 참고: PDF 2-2 (기본 검증), 6 (trading_symbol_ref)
 */
@Component
public class OrderValidator {

  // TODO: SymbolRepository 주입 (또는 캐시)

  // TODO: validate() 구현
}
