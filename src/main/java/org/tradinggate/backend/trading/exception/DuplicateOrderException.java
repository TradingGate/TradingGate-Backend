package org.tradinggate.backend.trading.exception;

/**
 * [A-1] Trading API - 중복 주문 예외
 * 역할:
 * - 멱등성 체크 실패 시 발생
 * TODO:
 * [ ] RuntimeException 상속
 * [ ] HTTP Status: 409 Conflict
 * [ ] errorCode: "DUPLICATE_ORDER"
 */
public class DuplicateOrderException extends RuntimeException {
  public DuplicateOrderException(String message) {
    super(message);
  }
}
