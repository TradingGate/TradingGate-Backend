package org.tradinggate.backend.trading.exception;

/**
 * [A-1] Trading API - 리스크 차단 예외
 *
 * 역할:
 * - 리스크 차단 상태에서 주문 시도 시 발생
 *
 * TODO:
 * [ ] RuntimeException 상속
 * [ ] HTTP Status: 403 Forbidden
 * [ ] errorCode: "RISK_BLOCKED"
 *
 * 참고: PDF 2-2 (리스크 block 정보 기반 주문 거절)
 */
public class RiskBlockedException extends RuntimeException {

  // TODO: 생성자
}
