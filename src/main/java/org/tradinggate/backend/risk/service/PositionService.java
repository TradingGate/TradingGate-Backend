package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.api.dto.PositionUpdateResult;
import org.tradinggate.backend.risk.domain.entity.Position;
import org.tradinggate.backend.risk.repository.PositionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 포지션 계산 및 관리 전용 서비스
 * - 단일 책임: 포지션 수량 및 평단가 계산
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

  private final PositionRepository positionRepository;
  private final PnlService pnlService;

  /**
   * 포지션 업데이트 및 PnL 계산
   * @return 업데이트된 포지션 결과
   */
  @Transactional
  public PositionUpdateResult updatePosition(Long accountId, Long symbolId,
                                             BigDecimal tradeQty, BigDecimal tradePrice) {
    // 1. 기존 포지션 조회 또는 생성
    Position position = positionRepository.findByAccountIdAndSymbolId(accountId, symbolId)
        .orElseGet(() -> Position.createDefault(accountId, symbolId));

    // 2. 포지션 방향 확인
    boolean isSameDirection = position.getQuantity().signum() == tradeQty.signum()
        || position.getQuantity().signum() == 0;

    BigDecimal realizedPnl = BigDecimal.ZERO;

    if (isSameDirection) {
      // 포지션 증가: 평단가 갱신
      realizedPnl = increasePosition(position, tradeQty, tradePrice);
    } else {
      // 포지션 감소: 실현 손익 발생
      realizedPnl = decreasePosition(position, tradeQty, tradePrice);
    }

    // 3. 미실현 손익 계산
    BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, tradePrice);
    position.updateValuation(tradePrice, unrealizedPnl);

    // 4. 저장
    positionRepository.save(position);

    log.debug("Position updated: qty={}, avgPrice={}, realizedPnl={}, unrealizedPnl={}",
        position.getQuantity(), position.getAvgPrice(), realizedPnl, unrealizedPnl);

    return new PositionUpdateResult(
        position.getQuantity(),
        position.getAvgPrice(),
        realizedPnl,
        unrealizedPnl
    );
  }

  /**
   * 포지션 증가 (가중평균 계산)
   */
  private BigDecimal increasePosition(Position position, BigDecimal tradeQty, BigDecimal tradePrice) {
    BigDecimal totalQty = position.getQuantity().add(tradeQty);

    if (totalQty.compareTo(BigDecimal.ZERO) != 0) {
      BigDecimal oldNotional = position.getQuantity().multiply(position.getAvgPrice());
      BigDecimal newNotional = tradeQty.multiply(tradePrice);
      BigDecimal newAvgPrice = oldNotional.add(newNotional)
          .divide(totalQty, 8, RoundingMode.HALF_UP);

      position.increasePosition(totalQty, newAvgPrice);
      log.debug("Position increased: qty={}, avgPrice={}", totalQty, newAvgPrice);
    }

    return BigDecimal.ZERO; // 포지션 증가 시 실현손익 없음
  }

  /**
   * 포지션 감소 (실현손익 계산)
   * - 부분 청산: 기존 포지션의 일부만 청산
   * - 전체 청산: 기존 포지션 전체 청산
   * - 반대 포지션 진입: 전체 청산 후 남은 수량으로 반대 방향 포지션 시작
   */
  private BigDecimal decreasePosition(Position position, BigDecimal tradeQty, BigDecimal tradePrice) {
    BigDecimal currentQty = position.getQuantity();
    BigDecimal newQty = currentQty.add(tradeQty);

    // 🔥 케이스 1: 전체 청산 후 반대 포지션 진입 (방향 전환)
    if (currentQty.signum() != newQty.signum() && newQty.signum() != 0) {
      // 1) 기존 포지션 전체 청산에 대한 실현손익 계산
      BigDecimal closeQty = currentQty.abs();
      BigDecimal realizedPnl = pnlService.calculateRealizedPnl(
          closeQty, position.getAvgPrice(), tradePrice, currentQty.signum()
      );

      // 2) 포지션 상태 업데이트 (수량 + 실현손익)
      position.decreasePosition(tradeQty, realizedPnl);

      // 3) 🔥 핵심: 반대 포지션 진입 시 평단가를 새 진입가로 초기화
      position.resetAvgPrice(tradePrice);

      log.debug("Position reversed: closedQty={}, realizedPnl={}, newQty={}, newAvgPrice={}",
          closeQty, realizedPnl, newQty, tradePrice);

      return realizedPnl;
    }

    // 케이스 2: 부분 청산 또는 전체 청산 (방향 유지 또는 제로)
    else {
      BigDecimal closeQty = tradeQty.abs().min(currentQty.abs());

      BigDecimal realizedPnl = pnlService.calculateRealizedPnl(
          closeQty, position.getAvgPrice(), tradePrice, currentQty.signum()
      );

      position.decreasePosition(tradeQty, realizedPnl);
      log.debug("Position decreased: qty={}, realizedPnl={}", position.getQuantity(), realizedPnl);

      return realizedPnl;
    }
  }

  /**
   * 미실현 손익 계산
   */
  private BigDecimal calculateUnrealizedPnl(Position position, BigDecimal currentPrice) {
    if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return position.getQuantity()
        .multiply(currentPrice.subtract(position.getAvgPrice()));
  }
}
