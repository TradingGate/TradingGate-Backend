package org.tradinggate.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TradingErrorCode implements ErrorCode{
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
  INVALID_ORDER(HttpStatus.BAD_REQUEST, "유효하지 않은 주문입니다."),
  DUPLICATE_ORDER(HttpStatus.CONFLICT, "중복된 주문입니다."),

  // 리스크 관련
  RISK_BLOCKED(HttpStatus.FORBIDDEN, "리스크 정책에 의해 차단되었습니다."),
  INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "잔액이 부족합니다."),
  PRICE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "가격 제한을 초과했습니다."),
  QUANTITY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "수량 제한을 초과했습니다.");

  private final HttpStatus status;
  private final String message;
}
