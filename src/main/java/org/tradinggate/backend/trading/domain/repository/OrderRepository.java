package org.tradinggate.backend.trading.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

  Optional<Order> findByClientOrderId(String clientOrderId);

  // 사용자별 주문 조회 (Slice 페이징)
  Slice<Order> findByUserId(Long userId, Pageable pageable);

  // 사용자별 주문 조회 (Page 페이징, 생성일 내림차순)
  Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  // 상태별 조회
  List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

  // 심볼별 조회
  List<Order> findByUserIdAndSymbol(Long userId, String symbol);

  // 기간별 조회
  List<Order> findByUserIdAndCreatedAtBetween(
      Long userId,
      LocalDateTime startDate,
      LocalDateTime endDate);

  // 미체결 주문 조회
  @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status = 'NEW' ORDER BY o.createdAt DESC")
  List<Order> findPendingOrders(@Param("userId") Long userId);

  // 심볼별 미체결 주문 개수
  @Query("SELECT COUNT(o) FROM Order o WHERE o.userId = :userId AND o.symbol = :symbol AND o.status = 'NEW'")
  long countPendingOrdersBySymbol(@Param("userId") Long userId, @Param("symbol") String symbol);

  Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId);

  // 복합 조건 조회
  @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
      "AND (:symbol IS NULL OR o.symbol = :symbol) " +
      "AND (:status IS NULL OR o.status = :status) " +
      "AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
      "AND (:endDate IS NULL OR o.createdAt <= :endDate) " +
      "ORDER BY o.createdAt DESC")
  Page<Order> findByConditions(
      @Param("userId") Long userId,
      @Param("symbol") String symbol,
      @Param("status") OrderStatus status,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      Pageable pageable);
}
