package org.tradinggate.backend.trading.api.dto.request;
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
public class OrderCreateRequest {

  // TODO: 필드 정의

  // TODO: Validation 어노테이션 추가

  // TODO: Getter/Setter 또는 @Data
}
