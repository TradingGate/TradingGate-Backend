package org.tradinggate.backend.risk.repository.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LedgerEntry Repository
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  /**
   * 멱등성 키 존재 여부 확인
   */
  boolean existsByIdempotencyKey(String idempotencyKey);

  /**
   * 거래 ID로 원장 항목 조회
   */
  List<LedgerEntry> findByTradeId(String tradeId);

  /**
   * 특정 계정의 특정 자산 원장 조회
   */
  List<LedgerEntry> findByAccountIdAndAssetOrderByCreatedAtDesc(Long accountId, String asset);

  /**
   * 특정 시점까지의 원장 합계 (대사용)
   */
  @Query("""
        SELECT COALESCE(SUM(l.amount), 0)
        FROM LedgerEntry l
        WHERE l.accountId = :accountId
          AND l.asset = :asset
          AND l.createdAt <= :endDateTime
    """)
  BigDecimal sumByAccountIdAndAssetUpToDate(
      @Param("accountId") Long accountId,
      @Param("asset") String asset,
      @Param("endDateTime") LocalDateTime endDateTime
  );

  /**
   * 특정 계정의 모든 자산 원장 합계 (현재 잔고 계산용)
   */
  @Query("""
        SELECT l.asset, COALESCE(SUM(l.amount), 0)
        FROM LedgerEntry l
        WHERE l.accountId = :accountId
        GROUP BY l.asset
    """)
  List<Object[]> sumAllAssetsByAccountId(@Param("accountId") Long accountId);
}
