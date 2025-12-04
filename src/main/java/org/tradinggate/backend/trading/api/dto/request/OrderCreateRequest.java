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
 *
 * TODO:
 * [ ] 필드 추가/확인:
 *     - String clientOrderId (필수, 멱등 키)
 *     - String symbol (필수)
 *     - OrderSide side (BUY/SELL)
 *     - OrderType orderType (LIMIT/MARKET)
 *     - TimeInForce timeInForce (GTC/IOC/FOK) ✅ 추가 필요
 *     - BigDecimal price (LIMIT일 때 필수)
 *     - BigDecimal quantity (필수)
 *     - String source (추가 권장, 기본값: "API")
 *
 * [ ] Validation 어노테이션 추가:
 *     - @NotNull, @NotBlank
 *     - @Positive, @DecimalMin
 *     - @Valid
 *
 * [ ] Custom Validator 연동:
 *     - @ClientOrderIdValid (ClientOrderIdValidator)
 *     - @OrderValid (OrderValidator)
 *
 * 참고: PDF 1-1 (orders.in 스키마)
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateRequest {

  @NotBlank(message = "clientOrderId is required")
  @Size(min = 5, max = 64, message = "clientOrderId must be between 5 and 64 characters")
  private String clientOrderId;

  @NotBlank(message = "symbol is required")
  @Size(max = 32, message = "symbol must be max 32 characters")
  private String symbol;

  @NotNull(message = "side is required")
  private OrderSide side;

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
