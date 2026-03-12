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
 */

public interface TradeRepository extends JpaRepository<Trade, Long> {

  Optional<Trade> findByEventId(String eventId);

  Optional<Trade> findByTradeId(Long tradeId);

  Optional<Trade> findByTradeIdAndUserId(Long tradeId, Long userId);

  Page<Trade> findByUserId(Long userId, Pageable pageable);

  List<Trade> findByUserIdAndOrderId(Long userId, Long orderId);

  List<Trade> findByOrderId(Long orderId);

  List<Trade> findByUserIdAndSymbol(Long userId, String symbol);

  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.execTime BETWEEN :startDate AND :endDate ORDER BY t.execTime DESC")
  List<Trade> findByUserIdAndExecTimeBetween(
      @Param("userId") Long userId,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.liquidityFlag = 'MAKER' ORDER BY t.execTime DESC")
  List<Trade> findMakerTrades(@Param("userId") Long userId);

  @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.liquidityFlag = 'TAKER' ORDER BY t.execTime DESC")
  List<Trade> findTakerTrades(@Param("userId") Long userId);

  @Query("SELECT SUM(t.execQuantity) FROM Trade t WHERE t.orderId = :orderId")
  BigDecimal sumExecQuantityByOrderId(@Param("orderId") Long orderId);
}
