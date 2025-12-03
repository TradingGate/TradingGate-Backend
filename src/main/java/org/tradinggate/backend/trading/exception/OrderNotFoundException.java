package org.tradinggate.backend.trading.exception;

/**
 * [A-1] Trading API - 주문 없음 예외
 *
 * 역할:
 * - 주문 조회 실패 시 발생
 *
 * TODO:
 * [ ] RuntimeException 상속
 * [ ] HTTP Status: 404 Not Found
 * [ ] errorCode: "ORDER_NOT_FOUND"
 */
public class OrderNotFoundException extends RuntimeException {

  // TODO: 생성자
}
