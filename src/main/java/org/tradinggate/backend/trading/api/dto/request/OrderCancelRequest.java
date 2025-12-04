package org.tradinggate.backend.trading.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [A-1] Trading API - 주문 취소 요청 DTO
 * 역할:
 * - 클라이언트 → API 취소 요청
 * TODO:
 * [✅️] 필드 구조 확인:
 *     - String clientOrderId (clientOrderId로 취소)
 *     - String symbol (필수)
 *     - Long orderId (orderId로 취소, 선택)
 * [ ] Validation:
 *     - clientOrderId 또는 orderId 둘 중 하나 필수
 *     - @NotBlank(message = "symbol is required")
 * [ ] CancelTarget 구조 추가 (선택):
 *     - cancelTarget.by ("CLIENT_ORDER_ID" or "ORDER_ID")
 *     - cancelTarget.value
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelRequest {

  @NotBlank(message = "clientOrderId is required")
  @Size(max = 64, message = "clientOrderId must be max 64 characters")
  private String clientOrderId;

  @NotBlank(message = "symbol is required")
  @Size(max = 32, message = "symbol must be max 32 characters")
  private String symbol;
}