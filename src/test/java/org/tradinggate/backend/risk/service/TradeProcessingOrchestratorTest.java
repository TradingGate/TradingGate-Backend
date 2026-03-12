package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.risk.kafka.dto.TradeExecutedEvent;
import org.tradinggate.backend.risk.service.balance.BalanceService;
import org.tradinggate.backend.risk.service.ledger.LedgerService;
import org.tradinggate.backend.risk.service.orchestrator.TradeProcessingOrchestrator;
import org.tradinggate.backend.risk.service.risk.RiskCheckService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeProcessingOrchestratorTest {

  @Mock
  private LedgerService ledgerService;

  @Mock
  private BalanceService balanceService;

  @Mock
  private RiskCheckService riskCheckService;

  @InjectMocks
  private TradeProcessingOrchestrator orchestrator;

  @Test
  @DisplayName("오케스트레이터 - 서비스 호출 순서 검증")
  void testCallOrder() {
    // Given
    TradeExecutedEvent event = TradeExecutedEvent.builder()
        .tradeId("TRD-001")
        .accountId(1001L)
        .symbol("BTCUSDT")
        .side("BUY")
        .quantity(new BigDecimal("0.1"))
        .price(new BigDecimal("50000"))
        .fee(new BigDecimal("5"))
        .feeAsset("USDT")
        .executedAt(LocalDateTime.now())
        .build();

    doNothing().when(ledgerService).recordTrade(any(), any(), any(), any(), any(), any(), any(), any());
    doNothing().when(balanceService).updateBalances(any(), any());
    doNothing().when(riskCheckService).checkNegativeBalance(any());

    // When
    boolean result = orchestrator.processTrade(event);

    // Then
    assertThat(result).isTrue();

    // 호출 순서 검증
    InOrder inOrder = inOrder(ledgerService, balanceService, riskCheckService);
    inOrder.verify(ledgerService).recordTrade(any(), any(), any(), any(), any(), any(), any(), any());
    inOrder.verify(balanceService).updateBalances(any(), any());
    inOrder.verify(riskCheckService).checkNegativeBalance(any());
  }
}
