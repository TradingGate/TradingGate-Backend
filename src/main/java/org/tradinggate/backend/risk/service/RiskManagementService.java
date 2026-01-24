package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.Position;
import org.tradinggate.backend.risk.domain.entity.RiskStatus;
import org.tradinggate.backend.risk.domain.entity.UserRiskProfile;
import org.tradinggate.backend.risk.repository.PositionRepository;
import org.tradinggate.backend.risk.repository.UserRiskProfileRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RiskManagementService {

  private final UserRiskProfileRepository riskProfileRepository;
  private final PositionRepository positionRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  private static final String RISK_COMMAND_TOPIC = "risk.commands";

  /**
   * B-2: 포지션 한도 체크
   * 새로운 체결이 발생했을 때, 유저가 한도를 초과했는지 검사합니다.
   */
  public void checkPositionLimit(Long userId) {
    UserRiskProfile profile = getProfileOrThrow(userId);

    // 유저의 모든 포지션 가치 합산 (절대값)
    List<Position> positions = positionRepository.findAllByAccountId(userId);
    BigDecimal totalExposure = positions.stream()
        .map(p -> p.getQuantity().abs().multiply(p.getAvgPrice())) // 수량(절대값) * 평단가
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    log.info("Risk Check [Limit]: User={}, Exposure={}, Max={}", userId, totalExposure, profile.getMaxPositionValue());

    if (totalExposure.compareTo(profile.getMaxPositionValue()) > 0) {
      log.warn("🚨 Limit Exceeded! Blocking user {}", userId);
      blockUser(profile, "POSITION_LIMIT_EXCEEDED");
    }
  }

  /**
   * B-3: 마진콜 및 강제 청산 체크
   * 현재가(Mark Price)가 변동되거나 PnL이 변했을 때 호출됩니다.
   */
  public void evaluateMargin(Long userId) {
    UserRiskProfile profile = getProfileOrThrow(userId);

    // TODO: 나중에 WalletService를 연결해야 함. 지금은 임시로 잔고 100,000 가정
    BigDecimal walletBalance = new BigDecimal("100000");

    // 포지션 조회
    List<Position> positions = positionRepository.findAllByAccountId(userId);

    // 총 포지션 가치 계산
    BigDecimal totalExposure = positions.stream()
        .map(p -> p.getQuantity().abs().multiply(p.getAvgPrice()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalExposure.compareTo(BigDecimal.ZERO) == 0) return;

    // 미실현 손익 (Unrealized PnL) - 임시로 0 처리
    BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;

    // 자산(Equity) 계산
    BigDecimal equity = walletBalance.add(totalUnrealizedPnl);

    // 마진 비율 계산
    BigDecimal marginRatio = equity.divide(totalExposure, 4, RoundingMode.HALF_DOWN);

    log.debug("Risk Check [Margin]: User={}, Ratio={}, Equity={}", userId, marginRatio, equity);

    // 상태 체크 로직 (BLOCKED, WARNING, NORMAL)
    if (marginRatio.compareTo(profile.getLiquidationThreshold()) < 0) {
      log.error("💀 LIQUIDATION TRIGGERED! User={}", userId);
      blockUser(profile, "LIQUIDATION");
      sendRiskCommand(userId, "FORCE_CLOSE_ALL", "Margin ratio fell below threshold");
    } else if (marginRatio.compareTo(profile.getMarginCallThreshold()) < 0) {
      if (profile.getStatus() == RiskStatus.NORMAL) {
        log.warn("⚠️ MARGIN CALL! User={}", userId);
        profile.updateStatus(RiskStatus.WARNING);
      }
    } else {
      if (profile.getStatus() != RiskStatus.NORMAL && profile.getStatus() != RiskStatus.BLOCKED) {
        log.info("✅ Risk Status Restored: User={}", userId);
        profile.updateStatus(RiskStatus.NORMAL);
      }
    }
  }

  private void blockUser(UserRiskProfile profile, String reason) {
    if (profile.getStatus() != RiskStatus.BLOCKED) {
      profile.updateStatus(RiskStatus.BLOCKED);
      sendRiskCommand(profile.getUserId(), "BLOCK_TRADING", reason);
    }
  }

  private void sendRiskCommand(Long userId, String action, String reason) {
    // 실제로는 별도의 DTO 객체를 만들어 보내는 것이 좋습니다.
    String message = String.format("{\"userId\":%d, \"action\":\"%s\", \"reason\":\"%s\"}", userId, action, reason);
    kafkaTemplate.send(RISK_COMMAND_TOPIC, String.valueOf(userId), message);
  }

  private UserRiskProfile getProfileOrThrow(Long userId) {
    return riskProfileRepository.findById(userId)
        .orElseGet(() -> {
          // 프로필 없으면 기본값 생성
          UserRiskProfile newProfile = UserRiskProfile.createDefault(userId);
          return riskProfileRepository.save(newProfile);
        });
  }
}