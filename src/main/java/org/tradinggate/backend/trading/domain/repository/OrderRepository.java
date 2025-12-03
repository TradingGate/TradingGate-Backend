package org.tradinggate.backend.trading.domain.repository;
/**
 * [A-1] Trading API - 주문 Repository
 *
 * 역할:
 * - Trading DB 조회
 * - Read-Only (API Layer는 쓰기 안 함)
 *
 * TODO:
 * [ ] 메서드 추가:
 *     - Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId)
 *     - Optional<Order> findByOrderId(Long orderId)
 *     - Page<Order> findByUserId(Long userId, Pageable pageable)
 *     - Page<Order> findByUserIdAndSymbol(Long userId, String symbol, Pageable pageable)
 *     - Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable)
 *     - Page<Order> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable)
 *
 * [ ] Query Method 또는 @Query 사용
 *
 * 참고: PDF 2-4 (Trading DB 조회)
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

  // TODO: 조회 메서드 정의
}
