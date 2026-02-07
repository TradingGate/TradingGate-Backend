package org.tradinggate.backend.risk.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tradinggate.backend.risk.api.dto.response.BalanceResponse;
import org.tradinggate.backend.risk.service.balance.BalanceQueryService;
import java.util.List;

/**
 * 잔고 조회 API
 */

@RestController
@RequestMapping("/api/risk/balance")
@RequiredArgsConstructor
public class BalanceController {

  private final BalanceQueryService balanceQueryService;

  /**
   * 특정 계정의 모든 자산 잔고 조회
   */
  @GetMapping("/account/{accountId}")
  public ResponseEntity<List<BalanceResponse>> getAccountBalance(
      @PathVariable Long accountId) {
    List<BalanceResponse> balances = balanceQueryService.getAccountBalance(accountId);
    return ResponseEntity.ok(balances);
  }

  /**
   * 특정 계정의 특정 자산 잔고 조회
   */
  @GetMapping("/account/{accountId}/asset/{asset}")
  public ResponseEntity<BalanceResponse> getAssetBalance(
      @PathVariable Long accountId,
      @PathVariable String asset) {
    BalanceResponse balance = balanceQueryService.getAssetBalance(accountId, asset);
    return ResponseEntity.ok(balance);
  }
}
