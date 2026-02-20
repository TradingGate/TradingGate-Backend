package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.anomaly.AbnormalPatternLog;
import org.tradinggate.backend.risk.domain.entity.anomaly.PatternType;
import org.tradinggate.backend.risk.domain.entity.risk.RiskState;
import org.tradinggate.backend.risk.domain.entity.risk.RiskStatus;
import org.tradinggate.backend.risk.repository.anomaly.AbnormalPatternLogRepository;
import org.tradinggate.backend.risk.repository.risk.RiskStateRepository;
import org.tradinggate.backend.risk.service.anomaly.AnomalyDetectionService;
import org.tradinggate.backend.risk.service.risk.RiskStateService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이상 감지 + 리스크 제어 통합 테스트
 * 
 * 검증 목표:
 * - 이상 거래 감지 (주문 폭주, 취소 반복, 대량 주문)
 */
@SpringBootTest
@ActiveProfiles("risk")
@Transactional
public class AnomalyAndRiskIntegrationTest {

    @Autowired
    private AnomalyDetectionService anomalyService;

    @Autowired
    private RiskStateService riskStateService;

    @Autowired
    private AbnormalPatternLogRepository anomalyLogRepository;

    @Autowired
    private RiskStateRepository riskStateRepository;

    private static final Long ACCOUNT_ID = 4000L;

    @BeforeEach
    void setUp() {
        anomalyLogRepository.deleteAll();
        riskStateRepository.deleteAll();
    }

    @Test
    @DisplayName("이상감지: 주문 폭주 1차 경고 (100건)")
    void testOrderFlood_Warning() {
        // 1분에 100건 주문
        anomalyService.detectOrderFlood(ACCOUNT_ID, 100);

        // 로그 기록됨
        List<AbnormalPatternLog> logs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.ORDER_FLOOD);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getDescription()).contains("100 orders");
        assertThat(logs.get(0).isActionTaken()).isFalse();
    }

    @Test
    @DisplayName("이상감지: 주문 폭주 3회 → 계정 블락")
    void testOrderFlood_Block() {
        // 1시간 내 3회 폭주 발생
        for (int i = 0; i < 3; i++) {
            anomalyService.detectOrderFlood(ACCOUNT_ID, 150);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 계정 블락됨
        RiskState riskState = riskStateRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(riskState.getStatus()).isEqualTo(RiskStatus.BLOCKED);
        assertThat(riskState.getBlockReason()).contains("주문 폭주");

        List<AbnormalPatternLog> logs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.ORDER_FLOOD);
        assertThat(logs).hasSizeGreaterThanOrEqualTo(1);

        long actionTakenCount = logs.stream().filter(AbnormalPatternLog::isActionTaken).count();
        assertThat(actionTakenCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("이상감지: 심볼별 주문 폭주 (로그만)")
    void testOrderFloodBySymbol() {
        anomalyService.detectOrderFloodBySymbol(ACCOUNT_ID, "BTCUSDT", 120);

        // 로그만 기록, 블락 없음
        List<AbnormalPatternLog> logs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.ORDER_FLOOD);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getSymbol()).isEqualTo("BTCUSDT");
        assertThat(logs.get(0).isActionTaken()).isFalse();

        // 계정 상태는 NORMAL 유지
        RiskState riskState = riskStateRepository.findById(ACCOUNT_ID).orElse(null);
        if (riskState != null) {
            assertThat(riskState.getStatus()).isEqualTo(RiskStatus.NORMAL);
        }
    }

    @Test
    @DisplayName("이상감지: 취소 반복 50회 → 로그만")
    void testCancelRepeat() {
        // 1분에 50회 취소
        anomalyService.detectCancelRepeat(ACCOUNT_ID, "ETHUSDT", 55);

        // 로그 기록
        List<AbnormalPatternLog> logs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.CANCEL_REPEAT);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getDescription()).contains("55 cancels");
        assertThat(logs.get(0).getSymbol()).isEqualTo("ETHUSDT");
        assertThat(logs.get(0).isActionTaken()).isFalse(); // MVP: 로그만
    }

    @Test
    @DisplayName("이상감지: 대량 주문 (평균 대비 100배)")
    void testLargeOrder() {
        // 평균 주문량 0.1 BTC
        BigDecimal averageQty = new BigDecimal("0.1");
        BigDecimal largeQty = new BigDecimal("15.0"); // 150배

        //
        anomalyService.detectLargeOrder(ACCOUNT_ID, "BTCUSDT", largeQty, averageQty);

        // 로그 기록
        List<AbnormalPatternLog> logs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.LARGE_ORDER);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getDescription()).contains("qty=15.0");
        assertThat(logs.get(0).isActionTaken()).isFalse();
    }

    @Test
    @DisplayName("이상감지: 임계값 미만 → 로그 없음")
    void testNoAnomalyDetected() {
        // 정상 범위
        anomalyService.detectOrderFlood(ACCOUNT_ID, 50);
        anomalyService.detectCancelRepeat(ACCOUNT_ID, "SOLUSDT", 30);

        // 로그 없음
        List<AbnormalPatternLog> floodLogs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.ORDER_FLOOD);
        List<AbnormalPatternLog> cancelLogs = anomalyLogRepository
                .findByAccountIdAndPatternTypeOrderByDetectedAtDesc(ACCOUNT_ID, PatternType.CANCEL_REPEAT);

        assertThat(floodLogs).isEmpty();
        assertThat(cancelLogs).isEmpty();
    }

    @Test
    @DisplayName("리스크 제어: 수동 계정 블락")
    void testManualAccountBlock() {
        riskStateService.blockAccount(ACCOUNT_ID, "Manual block - suspicious activity");

        RiskState riskState = riskStateRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(riskState.getStatus()).isEqualTo(RiskStatus.BLOCKED);
        assertThat(riskState.getBlockReason()).isEqualTo("Manual block - suspicious activity");
    }

    @Test
    @DisplayName("리스크 제어: 수동 계정 언블락")
    void testAccountUnblock() {
        riskStateService.blockAccount(ACCOUNT_ID, "Test block");

        riskStateService.unblockAccount(ACCOUNT_ID);

        RiskState riskState = riskStateRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(riskState.getStatus()).isEqualTo(RiskStatus.NORMAL);
        assertThat(riskState.getBlockReason()).isNull();
    }

    @Test
    @DisplayName("리스크 제어: 중복 블락 방지")
    void testDuplicateBlock() {
        riskStateService.blockAccount(ACCOUNT_ID, "First block");

        // 2차 블락 시도
        riskStateService.blockAccount(ACCOUNT_ID, "Second block");

        // 마지막 이유로 업데이트
        RiskState riskState = riskStateRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(riskState.getBlockReason()).isEqualTo("Second block");
    }

    @Test
    @DisplayName("통합: 주문 폭주 → 블락 → 언블락 흐름")
    void testFullFlow_FloodToUnblock() {
        // Step 1: 주문 폭주 3회
        for (int i = 0; i < 3; i++) {
            anomalyService.detectOrderFlood(ACCOUNT_ID, 150);
        }

        // Step 2: 블락 확인
        RiskState blockedState = riskStateRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(blockedState.getStatus()).isEqualTo(RiskStatus.BLOCKED);

        // Step 3: 언블락
        riskStateService.unblockAccount(ACCOUNT_ID);

        // Step 4: 정상화 확인
        RiskState normalState = riskStateRepository.findById(ACCOUNT_ID).orElseThrow();
        assertThat(normalState.getStatus()).isEqualTo(RiskStatus.NORMAL);
    }

    @Test
    @DisplayName("통합: 다양한 이상 패턴 혼합")
    void testMixedAnomalyPatterns() {
        // 여러 패턴 동시 발생
        anomalyService.detectOrderFlood(ACCOUNT_ID, 110);
        anomalyService.detectOrderFloodBySymbol(ACCOUNT_ID, "BTCUSDT", 120);
        anomalyService.detectCancelRepeat(ACCOUNT_ID, "ETHUSDT", 60);
        anomalyService.detectLargeOrder(ACCOUNT_ID, "SOLUSDT",
                new BigDecimal("1000"), new BigDecimal("5"));

        // 모든 패턴 로그 기록
        long floodCount = anomalyLogRepository.countByAccountIdAndPatternType(ACCOUNT_ID, PatternType.ORDER_FLOOD);
        long cancelCount = anomalyLogRepository.countByAccountIdAndPatternType(ACCOUNT_ID, PatternType.CANCEL_REPEAT);
        long largeOrderCount = anomalyLogRepository.countByAccountIdAndPatternType(ACCOUNT_ID, PatternType.LARGE_ORDER);

        assertThat(floodCount).isEqualTo(2); // ORDER_FLOOD 2건
        assertThat(cancelCount).isEqualTo(1);
        assertThat(largeOrderCount).isEqualTo(1);
    }

    @Test
    @DisplayName("통합: 시간대별 패턴 조회")
    void testPatternQueryByTime() {
        // 여러 시점에 패턴 발생
        anomalyService.detectOrderFlood(ACCOUNT_ID, 110);

        LocalDateTime since = LocalDateTime.now().minusMinutes(1);

        // 최근 1분 조회
        long recentCount = anomalyLogRepository.countByAccountIdAndPatternTypeSince(
                ACCOUNT_ID, PatternType.ORDER_FLOOD, since);

        assertThat(recentCount).isGreaterThan(0);
    }
}
