package org.tradinggate.backend.risk.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
@Profile("risk")
public class PnlService {

  /**
   * 실현 손익 계산
   * @param quantity 청산 수량 (항상 양수)
   * @param entryPrice 진입 가격 (내 평단가)
   * @param exitPrice 청산 가격 (현재 체결가)
   * @param positionSign 포지션 방향 (1: 롱, -1: 숏)
   */
  public BigDecimal calculateRealizedPnl(BigDecimal quantity, BigDecimal entryPrice,
                                         BigDecimal exitPrice, int positionSign) {
    // 롱(1)일 때: (판 가격 - 산 가격) * 수량
    // 숏(-1)일 때: (산 가격 - 판 가격) * 수량
    BigDecimal priceDiff = exitPrice.subtract(entryPrice);

    if (positionSign < 0) { // 숏 포지션이었다면 반대로
      priceDiff = priceDiff.negate();
    }

    return priceDiff.multiply(quantity);
  }
}
