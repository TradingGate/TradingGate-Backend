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