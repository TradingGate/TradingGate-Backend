package org.tradinggate.backend.risk.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tradinggate.backend.risk.domain.entity.anomaly.AbnormalPatternLog;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import java.time.LocalDateTime;

/**
 * 이상 패턴 응답 DTO
 */
@Getter
@AllArgsConstructor
@Builder
public class AbnormalPatternResponse {
  private Long id;
  private Long accountId;
  private String symbol;
  private PatternType patternType;
  private String description;
  private Boolean actionTaken;
  private String action;
  private LocalDateTime detectedAt;

  public static AbnormalPatternResponse from(AbnormalPatternLog log) {
    return AbnormalPatternResponse.builder()
        .id(log.getId())
        .accountId(log.getAccountId())
        .symbol(log.getSymbol())
        .patternType(log.getPatternType())
        .description(log.getDescription())
        .actionTaken(log.getActionTaken())
        .action(log.getAction())
        .detectedAt(log.getDetectedAt())
        .build();
  }
}
