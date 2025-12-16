package org.tradinggate.backend.trading.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tradinggate.backend.global.common.CommonResponse;
import org.tradinggate.backend.trading.api.dto.response.TradeResponse;
import org.tradinggate.backend.trading.service.TradeQueryService;

import java.util.List;

/**
 * [A-1] Trading API - 체결 조회 Controller
 *
 * 역할:
 * - Trading DB에서 체결 내역 조회
 *
 * 참고: PDF 1-3 (trades.executed 구조)
 */
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeQueryController {

  private final TradeQueryService tradeQueryService;

  @GetMapping
  public ResponseEntity<CommonResponse<Page<TradeResponse>>> getMyTrades(
      @RequestHeader("userId") Long userId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    Page<TradeResponse> trades = tradeQueryService.getMyTrades(userId, pageable);
    return ResponseEntity.ok(CommonResponse.success(trades));
  }

  @GetMapping("/order/{orderId}")
  public ResponseEntity<CommonResponse<List<TradeResponse>>> getTradesByOrder(
      @RequestHeader("userId") Long userId,
      @PathVariable Long orderId) {
    List<TradeResponse> trades = tradeQueryService.getTradesByOrder(userId, orderId);
    return ResponseEntity.ok(CommonResponse.success(trades));
  }
}
