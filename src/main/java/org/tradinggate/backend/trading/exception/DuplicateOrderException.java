package org.tradinggate.backend.trading.exception;

public class DuplicateOrderException extends RuntimeException {
  public DuplicateOrderException(String message) {
    super(message);
  }
}
