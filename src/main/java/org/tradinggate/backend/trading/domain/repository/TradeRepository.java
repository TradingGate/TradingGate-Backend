package org.tradinggate.backend.trading.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.trading.domain.entity.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


/**
 * [A-1] Trading API - 체결 Repository
 *
 * 역할:
 * - Trading DB 체결 내역 조회
 *
 * TODO:
 * [ ] 메서드 추가:
 *     - Page<Trade> findByUserId(Long userId, Pageable pageable)
 *     - List<Trade> findByOrderId(Long orderId)
 *     - Page<Trade> findByUserIdAndSymbol(Long userId, String symbol, Pageable pageable)
 *     - Page<Trade> findByUserIdAndExecTimeBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable)
 *
 * 참고: PDF 3 (trading_trade 테이블)
 */

public interface TradeRepository extends JpaRepository<Trade, Long> {

  /**
   * tradeId로 조회 (멱등성 체크용)
   */
  Optional<Trade> findByTradeId(Long tradeId);

  /**
   * 사용자 ID로 체결 내역 조회 (페이징)
   */
  Page<Trade> findByUserId(Long userId, Pageable pageable);

  /**
   * 사용자 ID + 주문 ID로 체결 내역 조회
   */
  List<Trade> findByUserIdAndOrderId(Long userId, Long orderId);

  /**
   * 주문 ID로 체결 내역 조회
   */
  List<Trade> findByOrderId(Long orderId);

  /**
   * 사용자 ID + 심볼로 체결 내역 조회
   */
  List<Trade> findByUserIdAndSymbol(Long userId, String symbol);

  /**
   * 사용자 ID + 기간으로 체결 내역 조회
   */
  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.execTime BETWEEN :startDate AND :endDate ORDER BY t.execTime DESC")
  List<Trade> findByUserIdAndExecTimeBetween(
      @Param("userId") Long userId,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate
  );

  /**
   * MAKER 체결 내역만 조회
   */
  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.liquidityFlag = 'MAKER' ORDER BY t.execTime DESC")
  List<Trade> findMakerTrades(@Param("userId") Long userId);

  /**
   * TAKER 체결 내역만 조회
   */
  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.liquidityFlag = 'TAKER' ORDER BY t.execTime DESC")
  List<Trade> findTakerTrades(@Param("userId") Long userId);

  /**
   * 주문별 총 체결 수량 조회
   */
  @Query("SELECT SUM(t.execQuantity) FROM Trade t WHERE t.orderId = :orderId")
  BigDecimal sumExecQuantityByOrderId(@Param("orderId") Long orderId);
}