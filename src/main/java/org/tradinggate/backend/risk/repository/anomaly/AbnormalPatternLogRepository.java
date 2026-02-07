package org.tradinggate.backend.risk.repository.anomaly;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.risk.domain.entity.anomaly.AbnormalPatternLog;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AbnormalPatternLog Repository
 */
public interface AbnormalPatternLogRepository extends JpaRepository<AbnormalPatternLog, Long> {

  /**
   * 특정 시간 이후 특정 계정의 로그 조회
   */
  List<AbnormalPatternLog> findByAccountIdAndDetectedAtAfter(
      Long accountId,
      LocalDateTime after
  );

  /**
   * 특정 패턴의 미조치 로그 조회
   */
  List<AbnormalPatternLog> findByPatternTypeAndActionTakenFalse(PatternType patternType);

  /**
   * 모든 미조치 로그 조회
   */
  List<AbnormalPatternLog> findByActionTakenFalseOrderByDetectedAtDesc();

  /**
   * 특정 계정의 특정 시간 이후 특정 패턴 카운트
   * 예: 최근 1시간 ORDER_FLOOD 횟수
   */
  @Query("""
        SELECT COUNT(a)
        FROM AbnormalPatternLog a
        WHERE a.accountId = :accountId
          AND a.patternType = :patternType
          AND a.detectedAt >= :since
    """)
  long countByAccountIdAndPatternTypeSince(
      @Param("accountId") Long accountId,
      @Param("patternType") PatternType patternType,
      @Param("since") LocalDateTime since
  );

  /**
   * 특정 계정의 특정 심볼, 특정 시간 이후 특정 패턴 카운트
   */
  @Query("""
        SELECT COUNT(a)
        FROM AbnormalPatternLog a
        WHERE a.accountId = :accountId
          AND a.symbol = :symbol
          AND a.patternType = :patternType
          AND a.detectedAt >= :since
    """)
  long countByAccountIdAndSymbolAndPatternTypeSince(
      @Param("accountId") Long accountId,
      @Param("symbol") String symbol,
      @Param("patternType") PatternType patternType,
      @Param("since") LocalDateTime since
  );
}
