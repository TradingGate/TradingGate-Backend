package org.tradinggate.backend.trading.api.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [A-1] Trading API - 체결 조회 Controller
 *
 * 역할:
 * - Trading DB에서 체결 내역 조회
 *
 * TODO:
 * [ ] GET /api/trades - 체결 목록 조회
 *     - Query Params: userId, symbol, orderId, startDate, endDate
 *     - TradeQueryService.getTrades() 호출 (TradeQueryService 생성 필요)
 *     - Pagination 지원
 *
 * [ ] GET /api/trades/{tradeId} - 단일 체결 조회 (선택)
 *
 * 참고: PDF 1-3 (trades.executed 구조)
 */
@RestController
@RequestMapping("/api/trades")
@Profile("api")
public class TradeQueryController {

  // TODO: TradeQueryService 주입 (생성 필요)

  // TODO: getTrades() 구현
}
