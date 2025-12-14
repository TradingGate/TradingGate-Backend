package org.tradinggate.backend.trading.api.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.global.exception.DomainErrorCode;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.OrderType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Log4j2
public class OrderValidator {

  private static final Map<String, TradingConstraints> SYMBOL_CONSTRAINTS = new HashMap<>();

  static {
    SYMBOL_CONSTRAINTS.put("BTCUSDT", TradingConstraints.builder()
        .tickSize(new BigDecimal("0.01"))
        .stepSize(new BigDecimal("0.00001"))
        .minQuantity(new BigDecimal("0.001"))
        .maxQuantity(new BigDecimal("1000"))
        .minNotional(new BigDecimal("10"))
        .build());

    SYMBOL_CONSTRAINTS.put("ETHUSDT", TradingConstraints.builder()
        .tickSize(new BigDecimal("0.01"))
        .stepSize(new BigDecimal("0.0001"))
        .minQuantity(new BigDecimal("0.01"))
        .maxQuantity(new BigDecimal("10000"))
        .minNotional(new BigDecimal("10"))
        .build());
  }

  /**
   * 주문 생성 검증
   */
  public void validate(OrderCreateRequest request) {
    validateBasicFields(request);
    validatePriceAndQuantity(request);
    validateTradingConstraints(request);
  }

  private void validateBasicFields(OrderCreateRequest request) {
    if (request.getSymbol() == null || request.getSymbol().isBlank()) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
    if (request.getOrderType() == null) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
    if (request.getSide() == null) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
  }

  private void validatePriceAndQuantity(OrderCreateRequest request) {
    if (request.getOrderType() == OrderType.LIMIT) {
      if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
        throw new CustomException(DomainErrorCode.INVALID_PARAM);
      }
    }

    if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
  }

  private void validateTradingConstraints(OrderCreateRequest request) {
    TradingConstraints constraints = SYMBOL_CONSTRAINTS.get(request.getSymbol());

    if (constraints == null) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }

    if (request.getPrice() != null) {
      validateTickSize(request.getPrice(), constraints.getTickSize());
    }

    validateStepSize(request.getQuantity(), constraints.getStepSize());
    validateQuantityRange(request.getQuantity(), constraints);
    validateMinNotional(request, constraints);
  }

  private void validateTickSize(BigDecimal price, BigDecimal tickSize) {
    BigDecimal remainder = price.remainder(tickSize);

    if (remainder.compareTo(BigDecimal.ZERO) != 0) {
      log.warn("틱 사이즈 위반: price={}, tickSize={}", price, tickSize);
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
  }

  private void validateStepSize(BigDecimal quantity, BigDecimal stepSize) {
    BigDecimal remainder = quantity.remainder(stepSize);

    if (remainder.compareTo(BigDecimal.ZERO) != 0) {
      log.warn("스텝 사이즈 위반: quantity={}, stepSize={}", quantity, stepSize);
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
  }

  private void validateQuantityRange(BigDecimal quantity, TradingConstraints constraints) {
    if (quantity.compareTo(constraints.getMinQuantity()) < 0) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }

    if (quantity.compareTo(constraints.getMaxQuantity()) > 0) {
      throw new CustomException(DomainErrorCode.INVALID_PARAM);
    }
  }

  private void validateMinNotional(OrderCreateRequest request, TradingConstraints constraints) {
    if (request.getPrice() != null) {
      BigDecimal notional = request.getPrice().multiply(request.getQuantity());

      if (notional.compareTo(constraints.getMinNotional()) < 0) {
        throw new CustomException(DomainErrorCode.INVALID_PARAM);
      }
    }
  }

  @lombok.Builder
  @lombok.Getter
  private static class TradingConstraints {
    private BigDecimal tickSize;
    private BigDecimal stepSize;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private BigDecimal minNotional;
  }
}
