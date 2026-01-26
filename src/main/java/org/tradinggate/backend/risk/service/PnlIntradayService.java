package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.PnlIntraday;
import org.tradinggate.backend.risk.repository.PnlIntradayRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * PnL Intraday 집계 전용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PnlIntradayService {

  private final PnlIntradayRepository pnlIntradayRepository;

  @Transactional
  public void savePnlIntraday(Long accountId, Long symbolId,
                              BigDecimal realizedPnl, BigDecimal unrealizedPnl,
                              BigDecimal fee) {
    LocalDate businessDate = LocalDate.now();

    PnlIntraday pnlIntraday = pnlIntradayRepository
        .findByBusinessDateAndAccountIdAndSymbolId(businessDate, accountId, symbolId)
        .orElseGet(() -> PnlIntraday.create(businessDate, accountId, symbolId));

    pnlIntraday.addRealizedPnl(realizedPnl);
    pnlIntraday.setUnrealizedPnl(unrealizedPnl);
    pnlIntraday.addFee(fee);

    pnlIntradayRepository.save(pnlIntraday);

    log.debug("PnL intraday saved: businessDate={}, accountId={}, symbolId={}, realizedPnl={}, unrealizedPnl={}",
        businessDate, accountId, symbolId, realizedPnl, unrealizedPnl);
  }
}
