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
         * 특정 계정의 특정 자산 원장 조회 (시간 역순)
         */
        List<LedgerEntry> findByAccountIdAndAssetOrderByCreatedAtDesc(Long accountId, String asset);

        /**
         * 특정 계정의 모든 원장 조회 (시간 순)
         */
        List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(Long accountId);

        /**
         * 특정 시점까지의 원장 합계 (대사용)
         */
        @Query("""
                            SELECT COALESCE(SUM(l.amount), 0)
                            FROM LedgerEntry l
                            WHERE l.accountId = :accountId
                              AND upper(l.asset) = upper(:asset)
                              AND l.createdAt <= :endDateTime
                        """)
        BigDecimal sumByAccountIdAndAssetUpToDate(
                        @Param("accountId") Long accountId,
                        @Param("asset") String asset,
                        @Param("endDateTime") LocalDateTime endDateTime);

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

        /**
         * 특정 시간 이전의 마지막 LedgerEntry ID 조회 (Watermark 용도)
         */
        @Query("SELECT MAX(l.id) FROM LedgerEntry l WHERE l.createdAt < :endDateTime")
        Long findMaxIdBefore(@Param("endDateTime") LocalDateTime endDateTime);

        /**
         * 당일 분개장 집계 (정산용, Watermark 기준)
         * Object[] 순서: accountId, asset, periodNetChange, feeTotal, tradeValueGross, tradeCount
         */
        @Query("""
                            SELECT l.accountId, l.asset,
                                   COALESCE(SUM(l.amount), 0),
                                   SUM(CASE WHEN l.entryType = 'FEE' THEN ABS(l.amount) ELSE 0 END),
                                   SUM(CASE WHEN l.entryType = 'TRADE' THEN ABS(l.amount) ELSE 0 END),
                                   COUNT(DISTINCT l.tradeId)
                            FROM LedgerEntry l
                            WHERE l.createdAt >= :startDateTime AND l.createdAt < :endDateTime
                              AND l.id <= :maxId
                            GROUP BY l.accountId, l.asset
                        """)
        List<Object[]> aggregateDailyLedger(
                        @Param("startDateTime") LocalDateTime startDateTime,
                        @Param("endDateTime") LocalDateTime endDateTime,
                        @Param("maxId") Long maxId);

        /**
         * 전체 계정/자산 원장 합계 (대사용)
         */
        @Query("""
                            SELECT l.accountId, l.asset, COALESCE(SUM(l.amount), 0)
                            FROM LedgerEntry l
                            GROUP BY l.accountId, l.asset
                        """)
        List<Object[]> sumAllHoldings();

        /**
         * 지정된 식별자(Watermark) 이전까지의 원장 총합 (완벽한 Snapshot 매칭용)
         */
        @Query("""
                            SELECT l.accountId, l.asset, COALESCE(SUM(l.amount), 0)
                            FROM LedgerEntry l
                            WHERE l.id <= :maxId
                            GROUP BY l.accountId, l.asset
                        """)
        List<Object[]> sumAllHoldingsUpTo(@Param("maxId") Long maxId);

        /**
         * 지정된 식별자(Watermark) 이전까지의 특정 계정/자산 원장 총합 (정산용 스냅샷)
         */
        @Query("""
                            SELECT COALESCE(SUM(l.amount), 0)
                            FROM LedgerEntry l
                            WHERE l.accountId = :accountId
                              AND upper(l.asset) = upper(:asset)
                              AND l.id <= :maxId
                        """)
        BigDecimal sumByAccountIdAndAssetUpToId(
                        @Param("accountId") Long accountId,
                        @Param("asset") String asset,
                        @Param("maxId") Long maxId);
}
