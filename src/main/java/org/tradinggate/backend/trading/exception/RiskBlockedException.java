package org.tradinggate.backend.trading.exception;

public class RiskBlockedException extends RuntimeException {
  public RiskBlockedException(String message) {
    super(message);
  }
}
