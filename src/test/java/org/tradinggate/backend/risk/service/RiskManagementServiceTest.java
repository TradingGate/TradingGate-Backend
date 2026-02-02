package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.risk.RiskStatus;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("risk")
@Transactional
class RiskManagementServiceTest {

  @Autowired
  private RiskManagementService riskManagementService;

  @Autowired
  private UserRiskProfileRepository riskProfileRepository;

  @Autowired
  private PositionRepository positionRepository;

  @MockitoBean
  private KafkaTemplate<String, Object> kafkaTemplate;

  private final Long USER_ID = 10L;
  private final Long SYMBOL_ID = 100L;

  @BeforeEach
  void setUp() {
    // 테스트 시작 전, 유저 프로필 초기화 (기본값: 한도 10만불, 청산비율 0.05)
    UserRiskProfile profile = UserRiskProfile.createDefault(USER_ID);
    riskProfileRepository.save(profile);
  }

  @Test
  @DisplayName("B-2: 포지션 한도 초과 시 유저가 차단(BLOCKED)되어야 한다")
  void testCheckPositionLimit_Exceeded() {
    // Given: 한도($100,000)를 넘는 포지션 생성 ($110,000)
    savePosition(USER_ID, SYMBOL_ID, "2.0", "55000.0"); // 2 * 55,000 = 110,000

    // When: 한도 체크 실행
    riskManagementService.checkPositionLimit(USER_ID);

    // Then
    UserRiskProfile profile = riskProfileRepository.findById(USER_ID).orElseThrow();

    // 1. 상태가 BLOCKED로 변했는지 확인
    assertThat(profile.getStatus()).isEqualTo(RiskStatus.BLOCKED);

    // 2. Kafka로 차단 명령이 전송되었는지 확인
    verify(kafkaTemplate, atLeastOnce())
        .send(eq("risk.commands"), eq(String.valueOf(USER_ID)), anyString());
  }

  @Test
  @DisplayName("B-2: 포지션 한도 내라면 상태는 정상(NORMAL)이어야 한다")
  void testCheckPositionLimit_Normal() {
    // Given: 한도($100,000) 이내 포지션 ($50,000)
    savePosition(USER_ID, SYMBOL_ID, "1.0", "50000.0");

    // When
    riskManagementService.checkPositionLimit(USER_ID);

    // Then
    UserRiskProfile profile = riskProfileRepository.findById(USER_ID).orElseThrow();
    assertThat(profile.getStatus()).isEqualTo(RiskStatus.NORMAL);

    // Kafka 메시지 전송 없어야 함
    verify(kafkaTemplate, times(0)).send(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("B-3: 마진 비율이 청산 레벨 미만이면 강제 청산(LIQUIDATION) 명령을 보낸다")
  void testEvaluateMargin_Liquidation() {
    // Given
    // 현재 서비스 코드에 잔고가 $100,000으로 하드코딩 되어 있음.
    // 청산 레벨(0.05)을 트리거하려면: 자산 / 포지션 < 0.05
    // 100,000 / 포지션 < 0.05  => 포지션이 2,000,000 (200만불) 이상이어야 함.

    // 40 BTC * $60,000 = $2,400,000 (약 240만불 포지션)
    savePosition(USER_ID, SYMBOL_ID, "40.0", "60000.0");

    // When: 마진 평가 실행
    riskManagementService.evaluateMargin(USER_ID);

    // Then
    UserRiskProfile profile = riskProfileRepository.findById(USER_ID).orElseThrow();

    // 1. 상태가 BLOCKED로 변경 (강제 청산 중)
    assertThat(profile.getStatus()).isEqualTo(RiskStatus.BLOCKED);

    // 2. Kafka로 FORCE_CLOSE_ALL 명령 전송 확인
    verify(kafkaTemplate).send(eq("risk.commands"), eq(String.valueOf(USER_ID)), anyString());
  }

  @Test
  @DisplayName("B-3: 마진 비율이 경고 레벨이면 상태가 WARNING으로 변경된다")
  void testEvaluateMargin_Warning() {
    // Given
    // 잔고 $100,000 가정.
    // 경고 레벨(0.10) 트리거: 0.05 < (100,000 / 포지션) < 0.10
    // => 포지션이 1,000,000 (100만불) ~ 2,000,000 사이여야 함.

    // 20 BTC * $60,000 = $1,200,000 (120만불)
    // 비율 = 10만 / 120만 = 0.083 (8.3%) -> 경고 구간
    savePosition(USER_ID, SYMBOL_ID, "20.0", "60000.0");

    // When
    riskManagementService.evaluateMargin(USER_ID);

    // Then
    UserRiskProfile profile = riskProfileRepository.findById(USER_ID).orElseThrow();
    assertThat(profile.getStatus()).isEqualTo(RiskStatus.WARNING);
  }

  @Test
  @DisplayName("B-3: 마진 비율이 정상이면 NORMAL 상태를 유지한다")
  void testEvaluateMargin_Normal() {
    // Given
    // 잔고 $100,000, 포지션 $50,000
    // 비율 = 10만 / 5만 = 2.0 (200%) -> 정상
    savePosition(USER_ID, SYMBOL_ID, "1.0", "50000.0");

    // When
    riskManagementService.evaluateMargin(USER_ID);

    // Then
    UserRiskProfile profile = riskProfileRepository.findById(USER_ID).orElseThrow();
    assertThat(profile.getStatus()).isEqualTo(RiskStatus.NORMAL);

    // Kafka 메시지 없어야 함
    verify(kafkaTemplate, times(0)).send(anyString(), anyString(), anyString());
  }

  // 테스트용 포지션 생성 헬퍼
  private void savePosition(Long userId, Long symbolId, String qty, String price) {
    Position position = Position.createDefault(userId, symbolId);
    position.increasePosition(new BigDecimal(qty), new BigDecimal(price));
    positionRepository.save(position);
  }
}
