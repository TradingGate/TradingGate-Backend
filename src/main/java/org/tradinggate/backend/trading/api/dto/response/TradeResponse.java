package org.tradinggate.backend.trading.api.dto.response;
/**
 * [A-1] Trading API - 체결 응답 DTO
 *
 * 역할:
 * - API → 클라이언트 체결 정보 응답
 *
 * TODO:
 * [ ] Trade Entity → TradeResponse 변환 메서드 추가
 *     - static TradeResponse from(Trade trade)
 *
 * [ ] 필드 정의:
 *     - tradeId, matchId, orderId, userId
 *     - symbol, side
 *     - execQuantity, execPrice, execValue
 *     - feeAmount, feeCurrency
 *     - liquidityFlag (MAKER/TAKER)
 *     - execTime
 *
 * 참고: PDF 3 (trading_trade 테이블 구조)
 */
public class TradeResponse {

  // TODO: 필드 정의

  // TODO: from(Trade) 변환 메서드
}
