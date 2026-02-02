package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.global.config.TestContainersConfig;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PositionService 단위 테스트
 * - 포지션 계산 로직만 테스트 (단일 책임)
 */
@SpringBootTest
@Transactional
@Import(TestContainersConfig.class)
@ActiveProfiles("risk")
class PositionServiceTest {

  @Autowired
  private PositionService positionService;

  @Autowired
  private PositionRepository positionRepository;

  @MockitoBean
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Test
  @DisplayName("1. 초기 진입: 롱 포지션이 새로 생성되어야 한다")
  void testInitialEntry() {
    // Given
    Long accountId = 1L;
    Long symbolId = 100L;
    BigDecimal quantity = new BigDecimal("1.0");
    BigDecimal price = new BigDecimal("50000.0");

    // When
    PositionUpdateResult result = positionService.updatePosition(
        accountId, symbolId, quantity, price
    );

    // Then
    assertThat(result.getNewQuantity()).isEqualByComparingTo("1.0");
    assertThat(result.getNewAvgPrice()).isEqualByComparingTo("50000.0");
    assertThat(result.getRealizedPnl()).isEqualByComparingTo("0.0");

    // DB 확인
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId).orElseThrow();
    assertThat(position.getQuantity()).isEqualByComparingTo("1.0");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("50000.0");
  }

  @Test
  @DisplayName("2. 물타기: 평단가가 갱신되어야 한다 (5만 + 6만 = 5.5만)")
  void testAveragePriceUpdate() {
    // Given
    Long accountId = 2L;
    Long symbolId = 100L;

    // 1차 진입: 1개 @ 50,000
    positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("1.0"),
        new BigDecimal("50000.0")
    );

    // When: 2차 진입 - 1개 @ 60,000
    PositionUpdateResult result = positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("1.0"),
        new BigDecimal("60000.0")
    );

    // Then
    // 수량: 2.0, 평단가: (50,000 + 60,000) / 2 = 55,000
    assertThat(result.getNewQuantity()).isEqualByComparingTo("2.0");
    assertThat(result.getNewAvgPrice()).isEqualByComparingTo("55000.0");
    assertThat(result.getRealizedPnl()).isEqualByComparingTo("0.0"); // 포지션 증가는 실현손익 없음

    // DB 확인
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId).orElseThrow();
    assertThat(position.getQuantity()).isEqualByComparingTo("2.0");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("55000.0");
  }

  @Test
  @DisplayName("3. 부분 청산: 평단가는 유지되고, 실현손익이 발생해야 한다")
  void testPartialClose() {
    // Given: 2개 @ 평단 55,000 보유 상태 만들기
    Long accountId = 3L;
    Long symbolId = 100L;

    // 초기 포지션 생성 (2개 직접 삽입)
    Position position = Position.createDefault(accountId, symbolId);
    position.increasePosition(new BigDecimal("2.0"), new BigDecimal("55000.0"));
    positionRepository.save(position);

    // When: 1개 매도 @ 70,000 (SELL = 음수 수량)
    PositionUpdateResult result = positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("-1.0"),  // SELL
        new BigDecimal("70000.0")
    );

    // Then
    // 남은 수량: 1.0
    assertThat(result.getNewQuantity()).isEqualByComparingTo("1.0");

    // 평단가는 유지: 55,000
    assertThat(result.getNewAvgPrice()).isEqualByComparingTo("55000.0");

    // 실현손익: (70,000 - 55,000) * 1개 = 15,000
    assertThat(result.getRealizedPnl()).isEqualByComparingTo("15000.0");

    // DB 확인
    Position updatedPosition = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId).orElseThrow();
    assertThat(updatedPosition.getQuantity()).isEqualByComparingTo("1.0");
    assertThat(updatedPosition.getAvgPrice()).isEqualByComparingTo("55000.0");
    assertThat(updatedPosition.getRealizedPnl()).isEqualByComparingTo("15000.0");
  }

  @Test
  @DisplayName("4. 전체 청산 후 반대 포지션: 실현손익 정산 후 새 포지션 시작")
  void testFullCloseAndReverse() {
    // Given: 2개 롱 포지션 @ 50,000
    Long accountId = 4L;
    Long symbolId = 100L;

    Position position = Position.createDefault(accountId, symbolId);
    position.increasePosition(new BigDecimal("2.0"), new BigDecimal("50000.0"));
    positionRepository.save(position);

    // When: 3개 매도 @ 60,000 (2개 청산 + 1개 숏 진입)
    PositionUpdateResult result = positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("-3.0"),
        new BigDecimal("60000.0")
    );

    // Then
    // 남은 수량: -1.0 (숏 1개)
    assertThat(result.getNewQuantity()).isEqualByComparingTo("-1.0");

    // 실현손익: (60,000 - 50,000) * 2개 = 20,000
    assertThat(result.getRealizedPnl()).isEqualByComparingTo("20000.0");

    // 새 평단가: 60,000
    assertThat(result.getNewAvgPrice()).isEqualByComparingTo("60000.0");
  }

  @Test
  @DisplayName("5. 숏 포지션 진입 및 청산")
  void testShortPositionEntry() {
    // Given
    Long accountId = 5L;
    Long symbolId = 100L;

    // When: 숏 1개 진입 @ 50,000
    PositionUpdateResult result1 = positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("-1.0"),  // SELL (숏)
        new BigDecimal("50000.0")
    );

    // Then
    assertThat(result1.getNewQuantity()).isEqualByComparingTo("-1.0");
    assertThat(result1.getNewAvgPrice()).isEqualByComparingTo("50000.0");

    // When: 숏 청산 @ 48,000 (가격 하락 = 이익)
    PositionUpdateResult result2 = positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("1.0"),  // BUY (숏 청산)
        new BigDecimal("48000.0")
    );

    // Then
    assertThat(result2.getNewQuantity()).isEqualByComparingTo("0.0");

    // 숏 실현손익: (50,000 - 48,000) * 1개 = 2,000
    assertThat(result2.getRealizedPnl()).isEqualByComparingTo("2000.0");
  }

  @Test
  @DisplayName("6. 미실현 손익 계산")
  void testUnrealizedPnl() {
    // Given: 1개 롱 @ 50,000
    Long accountId = 6L;
    Long symbolId = 100L;

    positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("1.0"),
        new BigDecimal("50000.0")
    );

    // When: 가격이 55,000으로 상승
    PositionUpdateResult result = positionService.updatePosition(
        accountId, symbolId,
        new BigDecimal("0.0"),  // 거래 없음 (가격 업데이트만)
        new BigDecimal("55000.0")
    );

    // Then
    // 미실현 손익: (55,000 - 50,000) * 1개 = 5,000
    assertThat(result.getUnrealizedPnl()).isEqualByComparingTo("5000.0");
  }
}
