package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import org.tradinggate.backend.risk.repository.anomaly.AbnormalPatternLogRepository;
import org.tradinggate.backend.risk.service.anomaly.AnomalyDetectionService;
import org.tradinggate.backend.risk.service.risk.RiskStateService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

  @Mock
  private AbnormalPatternLogRepository anomalyLogRepository;

  @Mock
  private RiskStateService riskStateService;

  @InjectMocks
  private AnomalyDetectionService anomalyDetectionService;

  @Test
  @DisplayName("주문 폭주 감지 - 임계값 초과")
  void testOrderFlood_Detected() {
    // Given
    when(anomalyLogRepository.countByAccountIdAndPatternTypeSince(
        eq(1001L), eq(PatternType.ORDER_FLOOD), any(LocalDateTime.class)
    )).thenReturn(2L);  // 이미 2회 발생

    // When: 3번째 폭주 (블락 임계값 도달)
    anomalyDetectionService.detectOrderFlood(1001L, 150);

    // Then: 로그 기록 + 블락
    verify(anomalyLogRepository, times(1)).save(any());
    verify(riskStateService, times(1)).blockAccount(eq(1001L), anyString());
  }

  @Test
  @DisplayName("주문 폭주 감지 - 정상 범위")
  void testOrderFlood_Normal() {
    anomalyDetectionService.detectOrderFlood(1001L, 50);

    verify(anomalyLogRepository, never()).save(any());
    verify(riskStateService, never()).blockAccount(any(), any());
  }
}
