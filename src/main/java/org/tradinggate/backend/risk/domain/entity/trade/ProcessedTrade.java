package org.tradinggate.backend.risk.domain.entity.trade;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * trades.executed 원본 데이터 저장
 *
 * 특징:
 * - trade_id는 matchId * 10 + roleCode로 생성 (0=MAKER, 1=TAKER)
 * - 멱등성 보장: trade_event_id, kafka offset 기반
 */
@Entity
@Getter
@Table(
    name = "processed_trade",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_trade_id", columnNames = "tradeId"),
        @UniqueConstraint(name = "uk_trade_event_id", columnNames = "tradeEventId"),
        @UniqueConstraint(name = "uk_kafka_offset",
            columnNames = {"kafkaTopic", "kafkaPartition", "kafkaOffset"})
    },
    indexes = {
        @Index(name = "idx_match_id", columnList = "matchId"),
        @Index(name = "idx_account_exec_time", columnList = "accountId, execTime"),
        @Index(name = "idx_processed_at", columnList = "processedAt"),
        @Index(name = "idx_symbol_exec_time", columnList = "symbol, execTime")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedTrade {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * 비즈니스 키: matchId * 10 + roleCode
   * roleCode: 0= MAKER, 1 = TAKER
   * 예: matchId=12345, TAKER면 tradeId=123451
   */
  @Column(nullable = false, unique = true)
  private Long tradeId;

  /**
   * 매칭 ID (taker/maker 양쪽이 동일한 matchId를 공유)
   */
  @Column(nullable = false)
  private Long matchId;

  /**
   * 계정 ID
   */
  @Column(nullable = false)
  private Long accountId;

  /**
   * 심볼명 (예: BTCUSDT)
   */
  @Column(nullable = false, length = 32)
  private String symbol;

  /**
   * 매매 방향: BUY, SELL
   */
  @Column(nullable = false, length = 10)
  private String side;

  /**
   * 유동성 제공자 구분: MAKER, TAKER
   */
  @Column(nullable = false, length = 10)
  private String liquidityFlag;

  /**
   * 체결 수량
   */
  @Column(nullable = false, precision = 18, scale = 8)
  private BigDecimal execQuantity;

  /**
   * 체결 가격
   */
  @Column(nullable = false, precision = 18, scale = 8)
  private BigDecimal execPrice;

  /**
   * 체결 금액 (execQuantity * execPrice)
   */
  @Column(nullable = false, precision = 18, scale = 8)
  private BigDecimal execValue;

  /**
   * 체결 시각 (거래소 기준)
   */
  @Column(nullable = false)
  private LocalDateTime execTime;

  /**
   * DB 저장 시각
   */
  @Column(nullable = false)
  private LocalDateTime processedAt;

  // ========== 멱등성을 위한 Kafka 메타데이터 ==========

  /**
   * Kafka 토픽명 (예: trades.executed)
   */
  @Column(nullable = false, length = 64)
  private String kafkaTopic;

  /**
   * Kafka 파티션 번호
   */
  @Column(nullable = false)
  private Integer kafkaPartition;

  /**
   * Kafka 오프셋
   */
  @Column(nullable = false)
  private Long kafkaOffset;

  /**
   * 이벤트 ID (takerEventId 또는 makerEventId)
   */
  @Column(nullable = false, unique = true, length = 64)
  private String tradeEventId;

  // ========== 생성자 및 팩토리 메서드 ==========

  /**
   * tradeId 생성 로직
   * @param matchId 매칭 ID
   * @param isTaker true면 TAKER(roleCode=1), false면 MAKER(roleCode=0)
   * @return matchId * 10 + roleCode
   */
  public static Long generateTradeId(Long matchId, boolean isTaker) {
    int roleCode = isTaker ? 1 : 0;
    return matchId * 10 + roleCode;
  }

  /**
   * Factory method for creating ProcessedTrade
   */
  public static ProcessedTrade create(
      Long matchId,
      String symbol,
      BigDecimal execQuantity,
      BigDecimal execPrice,
      Long accountId,
      String side,
      boolean isTaker,
      LocalDateTime execTime,
      String kafkaTopic,
      Integer kafkaPartition,
      Long kafkaOffset,
      String tradeEventId
  ) {
    ProcessedTrade trade = new ProcessedTrade();

    // 비즈니스 키 생성
    trade.tradeId = generateTradeId(matchId, isTaker);
    trade.matchId = matchId;

    // 계정/심볼 정보
    trade.accountId = accountId;
    trade.symbol = symbol;

    // 체결 정보
    trade.side = side;
    trade.liquidityFlag = isTaker ? "TAKER" : "MAKER";
    trade.execQuantity = execQuantity;
    trade.execPrice = execPrice;
    trade.execValue = execQuantity.multiply(execPrice);

    // 시간
    trade.execTime = execTime;
    trade.processedAt = LocalDateTime.now();

    // Kafka 메타데이터 (멱등성)
    trade.kafkaTopic = kafkaTopic;
    trade.kafkaPartition = kafkaPartition;
    trade.kafkaOffset = kafkaOffset;
    trade.tradeEventId = tradeEventId;

    return trade;
  }
}
