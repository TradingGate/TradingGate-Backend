package org.tradinggate.backend.risk.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tradinggate.backend.risk.api.dto.response.RiskStateResponse;
import org.tradinggate.backend.risk.service.risk.RiskStateService;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 리스크 관리 API
 */
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

  private final RiskStateService riskStateService;

  /**
   * 계정 리스크 상태 조회
   */
  @GetMapping("/state/{accountId}")
  public ResponseEntity<RiskStateResponse> getRiskState(@PathVariable Long accountId) {
    return riskStateService.getRiskState(accountId)
        .map(RiskStateResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * 계정 차단 여부 확인
   */
  @GetMapping("/state/{accountId}/blocked")
  public ResponseEntity<Boolean> isBlocked(@PathVariable Long accountId) {
    boolean blocked = riskStateService.isBlocked(accountId);
    return ResponseEntity.ok(blocked);
  }

  /**
   * 모든 차단된 계정 조회 (관리자용)
   */
  @GetMapping("/blocked-accounts")
  public ResponseEntity<List<RiskStateResponse>> getBlockedAccounts() {
    List<RiskStateResponse> blockedAccounts = riskStateService.getBlockedAccounts()
        .stream()
        .map(RiskStateResponse::from)
        .collect(Collectors.toList());
    return ResponseEntity.ok(blockedAccounts);
  }
}
