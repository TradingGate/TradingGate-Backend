package org.tradinggate.backend.trading.util;

import org.springframework.stereotype.Component;

/**
 * [A-1] Trading API - 주문 ID 생성 (선택)
 *
 * 역할:
 * - 임시 Order ID 생성
 * - ⚠️ 실제 orderId는 Matching Worker(A-2)가 부여 ⚠️
 *
 * TODO:
 * [ ] 사용 여부 확인:
 *     - API Layer에서는 orderId를 생성하지 않음
 *     - clientOrderId만 사용
 *     - 필요 시 삭제 또는 주석 처리
 *
 * 참고: PDF 2-3 (A-2가 orderId 부여)
 */
@Component
public class OrderIdGenerator {

  // TODO: 사용 여부 확인 (불필요할 수 있음)
}
