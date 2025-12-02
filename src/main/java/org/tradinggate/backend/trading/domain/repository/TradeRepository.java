package org.tradinggate.backend.trading.domain.repository;

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

  // TODO: 조회 메서드 정의
}
