package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.domain.event.BalanceInsufficientEvent;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.service.risk.RiskCheckService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskCheckServiceTest {

  @Mock
  private AccountBalanceRepository balanceRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private RiskCheckService riskCheckService;

  @Test
  @DisplayName("음수 잔고 감지 시 이벤트 발행")
  void testNegativeBalance_EventPublished() {
    // Given
    AccountBalance negative = AccountBalance.builder()
        .accountId(1001L)
        .asset("ETH")
        .available(new BigDecimal("-0.5"))
        .locked(BigDecimal.ZERO)
        .build();

    when(balanceRepository.findAllByAccountId(1001L)).thenReturn(List.of(negative));

    // When
    riskCheckService.checkNegativeBalance(1001L);

    // Then
    verify(eventPublisher, times(1)).publishEvent(any(BalanceInsufficientEvent.class));
  }

  @Test
  @DisplayName("정상 잔고는 이벤트 없음")
  void testPositiveBalance_NoEvent() {
    // Given
    AccountBalance positive = AccountBalance.builder()
        .accountId(1001L)
        .asset("BTC")
        .available(new BigDecimal("1.0"))
        .locked(BigDecimal.ZERO)
        .build();

    when(balanceRepository.findAllByAccountId(1001L)).thenReturn(List.of(positive));

    // When
    riskCheckService.checkNegativeBalance(1001L);

    // Then
    verify(eventPublisher, never()).publishEvent(any());
  }
}
