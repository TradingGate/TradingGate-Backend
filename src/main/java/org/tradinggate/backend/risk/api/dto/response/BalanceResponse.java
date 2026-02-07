package org.tradinggate.backend.risk.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 잔고 응답 DTO
 */
@Getter
@AllArgsConstructor
@Builder
public class BalanceResponse {
  private Long accountId;
  private String asset;
  private BigDecimal available;
  private BigDecimal locked;
  private BigDecimal total;
  private LocalDateTime updatedAt;

  public static BalanceResponse from(AccountBalance balance) {
    return BalanceResponse.builder()
        .accountId(balance.getAccountId())
        .asset(balance.getAsset())
        .available(balance.getAvailable())
        .locked(balance.getLocked())
        .total(balance.getTotal())
        .updatedAt(balance.getUpdatedAt())
        .build();
  }
}
