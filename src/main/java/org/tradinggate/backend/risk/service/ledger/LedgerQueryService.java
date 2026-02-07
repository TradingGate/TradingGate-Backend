package org.tradinggate.backend.risk.service.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.ledger.LedgerEntry;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 원장 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerQueryService {

  private final LedgerEntryRepository ledgerRepository;

  /**
   * 거래 ID로 원장 조회
   */
  public List<LedgerEntry> getEntriesByTradeId(String tradeId) {
    return ledgerRepository.findByTradeId(tradeId);
  }

  /**
   * 계정/자산별 원장 조회
   */
  public List<LedgerEntry> getEntriesByAccountAndAsset(Long accountId, String asset) {
    return ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(accountId, asset);
  }

  /**
   * 특정 시점까지의 원장 합계 (대사용)
   */
  public BigDecimal sumUpToDate(Long accountId, String asset, LocalDateTime endDateTime) {
    return ledgerRepository.sumByAccountIdAndAssetUpToDate(accountId, asset, endDateTime);
  }
}
