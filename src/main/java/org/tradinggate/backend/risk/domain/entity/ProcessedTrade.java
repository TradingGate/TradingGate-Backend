package org.tradinggate.backend.risk.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
    name = "processed_trade",
    indexes = @Index(name = "idx_trade_id", columnList = "tradeId", unique = true)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedTrade {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long tradeId;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public ProcessedTrade(Long tradeId) {
    this.tradeId = tradeId;
    this.processedAt = LocalDateTime.now();
  }
}
