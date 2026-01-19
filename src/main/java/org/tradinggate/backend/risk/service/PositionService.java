package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.Position;
import org.tradinggate.backend.risk.event.TradeExecutedEvent;
import org.tradinggate.backend.risk.repository.PositionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PositionService {

  private final PositionRepository positionRepository;
  private final PnlService pnlService; // PnL 계산은 별도 서비스로 위임

  @Transactional
  public void updatePosition(TradeExecutedEvent event) {
    Long accountId = event.getAccountId();
    Long symbolId = event.getSymbolId();

    // 1. 기존 포지션 조회
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId)
        .orElseGet(() -> Position.createDefault(accountId, symbolId));

    BigDecimal tradeQty = event.getQuantity(); // 매수는 +, 매도는 - 로 가정
    BigDecimal tradePrice = event.getPrice();

    // 2. 포지션 방향 확인 (Long vs Short)
    boolean isSameDirection = position.getQuantity().signum() == tradeQty.signum()
        || position.getQuantity().signum() == 0;

    if (isSameDirection) {
      // 포지션 증가 (진입): 평단가 갱신 필요 (가중평균)
      // 공식: (기존총액 + 신규총액) / (기존수량 + 신규수량)
      BigDecimal totalQty = position.getQuantity().add(tradeQty);
      if (totalQty.compareTo(BigDecimal.ZERO) != 0) {
        BigDecimal oldNotional = position.getQuantity().multiply(position.getAvgPrice());
        BigDecimal newNotional = tradeQty.multiply(tradePrice);
        BigDecimal newAvgPrice = oldNotional.add(newNotional)
            .divide(totalQty, 8, RoundingMode.HALF_UP); // 소수점 8자리

        position.increasePosition (totalQty, newAvgPrice);
      }
    } else {
      // 포지션 감소 (청산): 평단가 유지, 실현 손익 발생
      // 예: 롱 10개 보유 중 숏 5개 체결 -> 평단가는 그대로, 수량만 5개로 감소
      BigDecimal closeQty = tradeQty.abs().min(position.getQuantity().abs()); // 실제 청산된 수량

      // PnL 계산 위임 (청산 수량, 진입 평단가, 현재 체결가)
      BigDecimal realizedPnl = pnlService.calculateRealizedPnl(
          closeQty, position.getAvgPrice(), tradePrice, position.getQuantity().signum()
      );

      // 포지션 업데이트 (수량 감소, 실현 손익 누적)
      position.decreasePosition(tradeQty, realizedPnl);
    }
    positionRepository.save(position);
  }
}
