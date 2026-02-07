package org.tradinggate.backend.risk.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.risk.domain.entity.risk.RiskState;
import org.tradinggate.backend.risk.domain.entity.risk.RiskStatus;
import java.time.LocalDateTime;

/**
 * 리스크 상태 응답 DTO
 */
@Getter
@AllArgsConstructor
@Builder
public class RiskStateResponse {
  private Long accountId;
  private RiskStatus status;
  private String blockReason;
  private LocalDateTime updatedAt;

  public static RiskStateResponse from(RiskState riskState) {
    return RiskStateResponse.builder()
        .accountId(riskState.getAccountId())
        .status(riskState.getStatus())
        .blockReason(riskState.getBlockReason())
        .updatedAt(riskState.getUpdatedAt())
        .build();
  }
}
