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

  Optional<Order> findByIdempotencyKey(String idempotencyKey);

  Slice<Order> findByUserId(Long userId, Pageable pageable);

  Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

  List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

  List<Order> findByUserIdAndSymbol(Long userId, String symbol);

  List<Order> findByUserIdAndCreatedAtBetween(
      Long userId,
      LocalDateTime startDate,
      LocalDateTime endDate);

  @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status = 'PENDING' ORDER BY o.createdAt DESC")
  List<Order> findPendingOrders(@Param("userId") Long userId);

  @Query("SELECT COUNT(o) FROM Order o WHERE o.userId = :userId AND o.symbol = :symbol AND o.status = 'PENDING'")
  long countPendingOrdersBySymbol(@Param("userId") Long userId, @Param("symbol") String symbol);

  Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId);

  @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
      "AND (:symbol IS NULL OR o.symbol = :symbol) " +
      "AND (:status IS NULL OR o.status = :status) " +
      "AND (cast(:startDate as timestamp) IS NULL OR o.createdAt >= :startDate) " +
      "AND (cast(:endDate as timestamp) IS NULL OR o.createdAt <= :endDate) " +
      "ORDER BY o.createdAt DESC")
  Page<Order> findByConditions(
      @Param("userId") Long userId,
      @Param("symbol") String symbol,
      @Param("status") OrderStatus status,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      Pageable pageable);
}
