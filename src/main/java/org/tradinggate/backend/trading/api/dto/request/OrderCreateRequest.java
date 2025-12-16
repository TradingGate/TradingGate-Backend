package org.tradinggate.backend.trading.api.dto.request;

import lombok.*;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * [A-1] Trading API - 신규 주문 요청 DTO
 *
 * 역할:
 * - 클라이언트 → API 신규 주문 요청
 * - 필수 필드 검증
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.tradinggate.backend.trading.api.validator.OrderValid
public class OrderCreateRequest {

  @NotBlank(message = "clientOrderId is required")
  @Size(min = 5, max = 64, message = "clientOrderId must be between 5 and 64 characters")
  @org.tradinggate.backend.trading.api.validator.ClientOrderIdValid
  private String clientOrderId;

  @NotBlank(message = "symbol is required")
  @Size(max = 32, message = "symbol must be max 32 characters")
  private String symbol;

  @NotNull(message = "side is required")
  private OrderSide orderSide;

  @NotNull(message = "orderType is required")
  private OrderType orderType;

  @NotNull(message = "timeInForce is required")
  private TimeInForce timeInForce;

  @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive")
  private BigDecimal price;

  @NotNull(message = "quantity is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "quantity must be positive")
  private BigDecimal quantity;
}
