package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.service.balance.BalanceService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

  @Mock
  private AccountBalanceRepository balanceRepository;

  @InjectMocks
  private BalanceService balanceService;

  @Test
  @DisplayName("잔고 업데이트 - 기존 자산")
  void testUpdateBalance_Existing() {
    // Given
    AccountBalance existing = AccountBalance.builder()
        .accountId(1001L)
        .asset("BTC")
        .available(new BigDecimal("1.0"))
        .locked(BigDecimal.ZERO)
        .build();

    when(balanceRepository.findByAccountIdAndAsset(1001L, "BTC"))
        .thenReturn(Optional.of(existing));
    when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    balanceService.updateBalance(1001L, "BTC", new BigDecimal("0.5"));

    // Then
    assertThat(existing.getAvailable()).isEqualByComparingTo("1.5");
    verify(balanceRepository, times(1)).save(any());
  }

  @Test
  @DisplayName("잔고 업데이트 - 새 자산")
  void testUpdateBalance_New() {
    when(balanceRepository.findByAccountIdAndAsset(1001L, "ETH"))
        .thenReturn(Optional.empty());
    when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    balanceService.updateBalance(1001L, "ETH", new BigDecimal("5.0"));

    verify(balanceRepository, times(1)).save(any());
  }
}
