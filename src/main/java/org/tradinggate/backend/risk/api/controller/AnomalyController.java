package org.tradinggate.backend.risk.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tradinggate.backend.risk.api.dto.response.AbnormalPatternResponse;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import org.tradinggate.backend.risk.service.anomaly.AnomalyDetectionService;
import org.tradinggate.backend.risk.service.anomaly.AnomalyLogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이상감지 API
 */
@RestController
@RequestMapping("/api/risk/anomaly")
@RequiredArgsConstructor
public class AnomalyController {

  private final AnomalyDetectionService anomalyDetectionService;
  private final AnomalyLogService anomalyLogService;

  // === 조회 API ===

  /**
   * 특정 계정의 최근 이상 로그 조회
   * GET /api/risk/anomaly/account/{accountId}?hours=24
   */
  @GetMapping("/account/{accountId}")
  public ResponseEntity<List<AbnormalPatternResponse>> getAccountAnomalies(
      @PathVariable Long accountId,
      @RequestParam(defaultValue = "24") int hours) {

    List<AbnormalPatternResponse> logs = anomalyLogService
        .getRecentLogs(accountId, hours)
        .stream()
        .map(AbnormalPatternResponse::from)
        .collect(Collectors.toList());

    return ResponseEntity.ok(logs);
  }

  /**
   * 모든 미조치 이상 패턴 조회
   * GET /api/risk/anomaly/unactioned
   */
  @GetMapping("/unactioned")
  public ResponseEntity<List<AbnormalPatternResponse>> getUnactionedAnomalies() {
    List<AbnormalPatternResponse> logs = anomalyLogService
        .getUnactionedLogs()
        .stream()
        .map(AbnormalPatternResponse::from)
        .collect(Collectors.toList());

    return ResponseEntity.ok(logs);
  }

  /**
   * 특정 패턴의 미조치 로그 조회
   * GET /api/risk/anomaly/unactioned/{patternType}
   */
  @GetMapping("/unactioned/{patternType}")
  public ResponseEntity<List<AbnormalPatternResponse>> getUnactionedByPattern(
      @PathVariable PatternType patternType) {

    List<AbnormalPatternResponse> logs = anomalyLogService
        .getUnactionedLogsByPattern(patternType)
        .stream()
        .map(AbnormalPatternResponse::from)
        .collect(Collectors.toList());

    return ResponseEntity.ok(logs);
  }

  /**
   * 특정 계정의 최근 1시간 주문 폭주 횟수
   * GET /api/risk/anomaly/account/{accountId}/flood-count
   */
  @GetMapping("/account/{accountId}/flood-count")
  public ResponseEntity<Long> getOrderFloodCount(@PathVariable Long accountId) {
    long count = anomalyLogService.getOrderFloodCountLastHour(accountId);
    return ResponseEntity.ok(count);
  }

  // === 감지 트리거 API (테스트/수동 실행용) ===

  /**
   * 주문 폭주 감지 트리거 (수동 실행)
   * POST /api/risk/anomaly/detect/order-flood
   */
  @PostMapping("/detect/order-flood")
  public ResponseEntity<Void> detectOrderFlood(
      @RequestParam Long accountId,
      @RequestParam int orderCount) {

    anomalyDetectionService.detectOrderFlood(accountId, orderCount);
    return ResponseEntity.ok().build();
  }

  /**
   * 심볼별 주문 폭주 감지 트리거
   * POST /api/risk/anomaly/detect/order-flood-symbol
   */
  @PostMapping("/detect/order-flood-symbol")
  public ResponseEntity<Void> detectOrderFloodBySymbol(
      @RequestParam Long accountId,
      @RequestParam String symbol,
      @RequestParam int orderCount) {

    anomalyDetectionService.detectOrderFloodBySymbol(accountId, symbol, orderCount);
    return ResponseEntity.ok().build();
  }

  /**
   * 취소 반복 감지 트리거
   * POST /api/risk/anomaly/detect/cancel-repeat
   */
  @PostMapping("/detect/cancel-repeat")
  public ResponseEntity<Void> detectCancelRepeat(
      @RequestParam Long accountId,
      @RequestParam String symbol,
      @RequestParam int cancelCount) {

    anomalyDetectionService.detectCancelRepeat(accountId, symbol, cancelCount);
    return ResponseEntity.ok().build();
  }

  /**
   * 대량 주문 감지 트리거
   * POST /api/risk/anomaly/detect/large-order
   */
  @PostMapping("/detect/large-order")
  public ResponseEntity<Void> detectLargeOrder(
      @RequestParam Long accountId,
      @RequestParam String symbol,
      @RequestParam BigDecimal quantity,
      @RequestParam BigDecimal averageQuantity) {

    anomalyDetectionService.detectLargeOrder(accountId, symbol, quantity, averageQuantity);
    return ResponseEntity.ok().build();
  }
}
