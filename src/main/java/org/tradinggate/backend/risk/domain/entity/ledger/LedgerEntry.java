package org.tradinggate.backend.risk.domain.entity.ledger;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 원장 (Ledger Entry)
 *
 * 역할:
 * - 모든 자산 이동의 불변 기록 (Immutable)
 * - 이벤트 소싱: trades.executed → ledger_entry
 * - 멱등성: idempotencyKey로 중복 방지
 * - 잔고의 단일 진실 소스 (SSOT)
 *
 * 특징:
 * - INSERT ONLY (수정/삭제 불가)
 * - 합계 = 잔고 (대사의 기준)
 */
@Entity
@Table(name = "ledger_entry", indexes = {
    @Index(name = "idx_trade_id", columnList = "tradeId"),
    @Index(name = "idx_account_asset_date", columnList = "accountId,asset,createdAt"),
    @Index(name = "idx_idempotency", columnList = "idempotencyKey", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long accountId;
  
  @Column(nullable = false, length = 10)
  private String asset;
  
  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EntryType entryType;

  @Column(length = 50)
  private String tradeId;

  @Column(unique = true, length = 100, nullable = false)
  private String idempotencyKey;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (this.createdAt == null) {
      this.createdAt = LocalDateTime.now();
    }
  }

  public static String generateIdempotencyKey(
      String tradeId,
      Long accountId,
      String asset,
      EntryType entryType) {
    return String.format("%s:%s:%s:%s", tradeId, accountId, asset, entryType);
  }

  public static String generateIdempotencyKey(String tradeId, String asset, EntryType entryType) {
    return String.format("%s:%s:%s", tradeId, asset, entryType);
  }
}
