package org.tradinggate.backend.trading.domain.entity;

import jakarta.persistence.Entity;

/**
 * [A-1] Trading API - 체결 Entity
 *
 * 역할:
 * - Trading DB trading_trade 테이블 매핑
 * - Projection Consumer가 trades.executed 이벤트를 받아 저장
 *
 * TODO:
 * [ ] 필드 확인 (PDF 스키마 기준):
 *     - Long id (PK)
 *     - Long tradeId (매칭 엔진이 발행한 체결 ID)
 *     - Long matchId (매칭 단위 ID)
 *     - Long orderId
 *     - Long userId
 *     - String symbol
 *     - OrderSide side (BUY/SELL)
 *     - BigDecimal execQuantity
 *     - BigDecimal execPrice
 *     - BigDecimal execValue (qty * price)
 *     - BigDecimal feeAmount
 *     - String feeCurrency
 *     - String liquidityFlag (MAKER/TAKER)
 *     - LocalDateTime execTime
 *
 * [ ] JPA 어노테이션:
 *     - @Entity
 *     - @Table(name = "trading_trade")
 *     - UNIQUE: tradeId
 *
 * [ ] 인덱스:
 *     - (tradeId)
 *     - (orderId)
 *     - (userId, execTime DESC)
 *
 * 참고: PDF 3 (trading_trade 테이블 구조)
 */
@Entity
@Table(name = "trading_trade")
public class Trade {

  // TODO: 필드 정의

  // TODO: JPA 어노테이션

  // TODO: Getter/Setter
}
