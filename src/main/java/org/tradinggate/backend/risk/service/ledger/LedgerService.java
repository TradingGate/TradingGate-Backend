package org.tradinggate.backend.risk.service.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.ledger.EntryType;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import java.math.BigDecimal;

@Slf4j
@Service
@Profile("risk")
@RequiredArgsConstructor
public class LedgerService {

  private final LedgerEntryRepository ledgerRepository;

  @Transactional
  public LedgerEntry recordEntry(
      Long accountId,
      String asset,
      BigDecimal amount,
      EntryType entryType,
      String tradeId,
      String idempotencyKey) {
    // 멱등성 검사
    if (ledgerRepository.existsByIdempotencyKey(idempotencyKey)) {
      log.debug("Duplicate idempotency key detected: {}", idempotencyKey);
      return null;
    }
    LedgerEntry entry = LedgerEntry.builder()
        .accountId(accountId)
        .asset(asset)
        .amount(amount)
        .entryType(entryType)
        .tradeId(tradeId)
        .idempotencyKey(idempotencyKey)
        .build();
    LedgerEntry saved = ledgerRepository.save(entry);
    log.debug("Ledger entry recorded: {}", saved);
    return saved;
  }

  @Transactional
  public boolean recordTrade(String tradeId, Long accountId,
      String baseAsset, BigDecimal baseAmount,
      String quoteAsset, BigDecimal quoteAmount,
      BigDecimal fee, String feeAsset) {
    // 1. Base asset 변동 기록
    String baseIdempotencyKey = LedgerEntry.generateIdempotencyKey(tradeId, baseAsset, EntryType.TRADE);
    LedgerEntry baseEntry = recordEntry(accountId, baseAsset, baseAmount, EntryType.TRADE, tradeId, baseIdempotencyKey);
    if (baseEntry == null) {
      log.debug("Trade {} already recorded (idempotency key: {})", tradeId, baseIdempotencyKey);
      return false; // 중복
    }
    // 2. Quote asset 변동 기록
    String quoteIdempotencyKey = LedgerEntry.generateIdempotencyKey(tradeId, quoteAsset, EntryType.TRADE);
    recordEntry(accountId, quoteAsset, quoteAmount, EntryType.TRADE, tradeId, quoteIdempotencyKey);
    // 3. 수수료 기록 (fee > 0인 경우만)
    if (fee.compareTo(BigDecimal.ZERO) > 0) {
      String feeIdempotencyKey = LedgerEntry.generateIdempotencyKey(tradeId, feeAsset, EntryType.FEE);
      recordEntry(accountId, feeAsset, fee.negate(), EntryType.FEE, tradeId, feeIdempotencyKey);
    }
    return true; // 정상 처리
  }
}
