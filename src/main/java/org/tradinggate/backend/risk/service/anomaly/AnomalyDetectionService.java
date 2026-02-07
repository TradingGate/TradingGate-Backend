package org.tradinggate.backend.risk.service.anomaly;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.anomaly.AbnormalPatternLog;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import org.tradinggate.backend.risk.repository.anomaly.AbnormalPatternLogRepository;
import org.tradinggate.backend.risk.service.risk.RiskStateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이상 감지 서비스
 * - 주문 폭주 감지 (ORDER_FLOOD)
 * - 임계값 초과 시 계정 블락
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

  private final AbnormalPatternLogRepository anomalyLogRepository;
  private final RiskStateService riskStateService;

  // === 임계값 설정 ===

  /**
   * 주문 폭주 1차 임계값 (경고)
   * - 1분에 100개 이상 주문
   */
  private static final int ORDER_FLOOD_WARNING = 100;

  /**
   * 주문 폭주 2차 임계값 (블락)
   * - 1시간에 3회 이상 폭주 발생 시 계정 블락
   */
  private static final int ORDER_FLOOD_BLOCK_THRESHOLD = 3;

  /**
   * 취소 반복 임계값
   * - 1분에 50개 이상 취소
   */
  private static final int CANCEL_REPEAT_THRESHOLD = 50;

  /**
   * 주문 폭주 감지
   *
   * @param accountId 계정 ID
   * @param orderCount 최근 1분간 주문 수
   */
  @Transactional
  public void detectOrderFlood(Long accountId, int orderCount) {
    if (orderCount <= ORDER_FLOOD_WARNING) {
      return;  // 정상 범위
    }

    log.warn("주문 폭주 감지: accountId={}, count={}", accountId, orderCount);

    // 로그 기록
    AbnormalPatternLog logEntry = AbnormalPatternLog.builder()
        .accountId(accountId)
        .patternType(PatternType.ORDER_FLOOD)
        .description(String.format("주문 폭주: %d orders in 1 minute", orderCount))
        .actionTaken(false)
        .build();

    anomalyLogRepository.save(logEntry);

    // 최근 1시간 폭주 횟수 확인
    long floodCount = anomalyLogRepository.countByAccountIdAndPatternTypeSince(
        accountId,
        PatternType.ORDER_FLOOD,
        LocalDateTime.now().minusHours(1)
    );

    log.info(" 최근 1시간 주문 폭주 횟수: accountId={}, count={}",
        accountId, floodCount);

    // 임계값 초과 시 계정 블락
    if (floodCount >= ORDER_FLOOD_BLOCK_THRESHOLD) {
      String reason = String.format(
          "주문 폭주 감지 %d times in 1 hour (threshold: %d)",
          floodCount, ORDER_FLOOD_BLOCK_THRESHOLD
      );

      riskStateService.blockAccount(accountId, reason);

      logEntry.markActionTaken("Account blocked");
      anomalyLogRepository.save(logEntry);

      log.error("주문 폭주로 계정 블락 : accountId={}, count={}",
          accountId, floodCount);
    }
  }

  /**
   * 심볼별 주문 폭주 감지
   *
   * @param accountId 계정 ID
   * @param symbol 심볼
   * @param orderCount 최근 1분간 해당 심볼 주문 수
   */
  @Transactional
  public void detectOrderFloodBySymbol(Long accountId, String symbol, int orderCount) {
    if (orderCount <= ORDER_FLOOD_WARNING) {
      return;
    }

    log.warn("심볼별 주문 폭주 감지: accountId={}, symbol={}, count={}",
        accountId, symbol, orderCount);

    AbnormalPatternLog logEntry = AbnormalPatternLog.builder()
        .accountId(accountId)
        .symbol(symbol)
        .patternType(PatternType.ORDER_FLOOD)
        .description(String.format(
            "Order flood on %s: %d orders in 1 minute", symbol, orderCount))
        .actionTaken(false)
        .build();

    anomalyLogRepository.save(logEntry);
  }

  /**
   * 취소 반복 감지
   *
   * @param accountId
   * @param symbol
   * @param cancelCount 최근 1분간 취소 수
   */
  @Transactional
  public void detectCancelRepeat(Long accountId, String symbol, int cancelCount) {
    if (cancelCount <= CANCEL_REPEAT_THRESHOLD) {
      return;
    }

    log.warn("취소 반복 감지: accountId={}, symbol={}, count={}",
        accountId, symbol, cancelCount);

    AbnormalPatternLog logEntry = AbnormalPatternLog.builder()
        .accountId(accountId)
        .symbol(symbol)
        .patternType(PatternType.CANCEL_REPEAT)
        .description(String.format(
            "Cancel repeat on %s: %d cancels in 1 minute", symbol, cancelCount))
        .actionTaken(false)
        .build();

    anomalyLogRepository.save(logEntry);
  }


  /**
   * 대량 주문 감지
   *
   * @param accountId 계정 ID
   * @param symbol 심볼
   * @param quantity 주문 수량
   * @param averageQuantity 평균 주문 수량 (기준)
   */
  @Transactional
  public void detectLargeOrder(Long accountId, String symbol,
                               BigDecimal quantity, BigDecimal averageQuantity) {

    BigDecimal threshold = averageQuantity.multiply(new BigDecimal("100"));

    if (quantity.compareTo(threshold) <= 0) {
      return;
    }

    log.warn("대량 주문 감지: accountId={}, symbol={}, qty={}, avg={}",
        accountId, symbol, quantity, averageQuantity);

    AbnormalPatternLog logEntry = AbnormalPatternLog.builder()
        .accountId(accountId)
        .symbol(symbol)
        .patternType(PatternType.LARGE_ORDER)
        .description(String.format(
            "Large order on %s: qty=%s (avg=%s)",
            symbol, quantity, averageQuantity))
        .actionTaken(false)
        .build();

    anomalyLogRepository.save(logEntry);
  }
}
