package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.Position;
import org.tradinggate.backend.risk.event.TradeExecutedEvent; // 패키지 경로 확인!
import org.tradinggate.backend.risk.repository.PositionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // 테스트 끝나면 DB 롤백
class PositionServiceTest {

  @Autowired
  private PositionService positionService;

  @Autowired
  private PositionRepository positionRepository;

  @Autowired
  private ApplicationEventPublisher eventPublisher; // 이벤트 발행기

  @Test
  @DisplayName("1. 초기 진입: 롱 포지션이 새로 생성되어야 한다")
  void testInitialEntry() {
    // Given
    Long accountId = 1L;
    Long symbolId = 100L;
    TradeExecutedEvent event = TradeExecutedEvent.builder()
        .accountId(accountId)
        .symbolId(symbolId)
        .quantity(new BigDecimal("1.0")) // 1 BTC 매수
        .price(new BigDecimal("50000.0")) // $50,000
        .tradeTime(LocalDateTime.now())
        .build();

    // When (이벤트 리스너가 호출하는 메서드를 직접 호출하거나, 이벤트를 발행)
    // 여기서는 서비스 메서드를 직접 호출해서 테스트 (리스너 비동기 대기 문제 회피)
    positionService.updatePosition(event);

    // Then
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId).orElseThrow();
    assertThat(position.getQuantity()).isEqualByComparingTo("1.0");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("50000.0");
  }

  @Test
  @DisplayName("2. 물타기: 평단가가 갱신되어야 한다 (5만 + 6만 = 5.5만)")
  void testAveragePriceUpdate() {
    // Given (초기 포지션 셋팅)
    Long accountId = 2L;
    Long symbolId = 100L;

    // 1차 진입
    positionService.updatePosition(new TradeExecutedEvent(accountId, symbolId,
        new BigDecimal("1.0"), new BigDecimal("50000.0"), LocalDateTime.now()));

    // When (2차 진입 - 가격 상승)
    TradeExecutedEvent event = new TradeExecutedEvent(accountId, symbolId,
        new BigDecimal("1.0"), new BigDecimal("60000.0"), LocalDateTime.now());

    positionService.updatePosition(event);

    // Then
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId).orElseThrow();

    // 수량 2.0, 평단가 55,000
    assertThat(position.getQuantity()).isEqualByComparingTo("2.0");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("55000.0");
  }

  @Test
  @DisplayName("3. 부분 청산: 평단가는 유지되고, 실현손익이 발생해야 한다")
  void testPartialClose() {
    // Given (2개, 평단 55,000 보유 상태 가정)
    Long accountId = 3L;
    Long symbolId = 100L;

    // 초기 셋팅 (2개, 55000불)
    positionService.updatePosition(new TradeExecutedEvent(accountId, symbolId,
        new BigDecimal("2.0"), new BigDecimal("55000.0"), LocalDateTime.now()));

    // When (1개 매도 @ 70,000불 - 익절)
    // 매도니까 quantity는 음수(-1.0)
    TradeExecutedEvent event = new TradeExecutedEvent(accountId, symbolId,
        new BigDecimal("-1.0"), new BigDecimal("70000.0"), LocalDateTime.now());

    positionService.updatePosition(event);

    // Then
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId).orElseThrow();

    // 남은 수량 1.0
    assertThat(position.getQuantity()).isEqualByComparingTo("1.0");

    // 평단가는 변하지 않음 (55,000 유지)
    assertThat(position.getAvgPrice()).isEqualByComparingTo("55000.0");

    // 실현손익: (70,000 - 55,000) * 1개 = 15,000 이득
    assertThat(position.getRealizedPnl()).isEqualByComparingTo("15000.0");
  }
}
