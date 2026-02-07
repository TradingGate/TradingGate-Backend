package org.tradinggate.backend.risk.service.anomaly;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.anomaly.AbnormalPatternLog;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import org.tradinggate.backend.risk.repository.anomaly.AbnormalPatternLogRepository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 이상 로그 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyLogService {

  private final AbnormalPatternLogRepository anomalyLogRepository;

  /**
   * 특정 계정의 최근 N시간 이상 로그 조회
   */
  public List<AbnormalPatternLog> getRecentLogs(Long accountId, int hours) {
    LocalDateTime since = LocalDateTime.now().minusHours(hours);
    return anomalyLogRepository.findByAccountIdAndDetectedAtAfter(accountId, since);
  }

  /**
   * 모든 미조치 이상 패턴 조회
   */
  public List<AbnormalPatternLog> getUnactionedLogs() {
    return anomalyLogRepository.findByActionTakenFalseOrderByDetectedAtDesc();
  }

  /**
   * 특정 패턴의 미조치 로그 조회
   */
  public List<AbnormalPatternLog> getUnactionedLogsByPattern(PatternType patternType) {
    return anomalyLogRepository.findByPatternTypeAndActionTakenFalse(patternType);
  }

  /**
   * 특정 계정의 최근 1시간 주문 폭주 횟수
   */
  public long getOrderFloodCountLastHour(Long accountId) {
    return anomalyLogRepository.countByAccountIdAndPatternTypeSince(
        accountId,
        PatternType.ORDER_FLOOD,
        LocalDateTime.now().minusHours(1)
    );
  }
}
