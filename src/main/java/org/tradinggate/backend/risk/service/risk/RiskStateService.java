package org.tradinggate.backend.risk.service.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.risk.RiskState;
import org.tradinggate.backend.risk.domain.entity.risk.RiskStatus;
import org.tradinggate.backend.risk.domain.event.BalanceInsufficientEvent;
import org.tradinggate.backend.risk.repository.risk.RiskStateRepository;
import org.tradinggate.backend.risk.kafka.publisher.RiskCommandPublisher;
import java.util.List;
import java.util.Optional;

/**
 * 리스크 상태 관리 서비스
 *
 * 책임:
 * - RiskState 생성/조회/수정
 * - BalanceInsufficientEvent 리스너
 * - 계정 블락/해제 + Kafka 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskStateService {

  private final RiskStateRepository riskStateRepository;
  private final RiskCommandPublisher riskCommandPublisher;

  /**
   * 잔고 부족 이벤트 리스너
   * - BalanceInsufficientEvent 발생 시 자동 호출
   * - 계정 블락 처리
   */
  @EventListener
  @Transactional
  public void handleBalanceInsufficient(BalanceInsufficientEvent event) {
    log.warn("🚨 Handling balance insufficient event: accountId={}, asset={}, balance={}",
        event.getAccountId(), event.getAsset(), event.getCurrentBalance());

    blockAccount(event.getAccountId(), event.getReason());
  }

  /**
   * 계정 차단
   * - RiskState 업데이트
   * - Kafka로 A 모듈에 알림
   */
  @Transactional
  public void blockAccount(Long accountId, String reason) {
    RiskState riskState = riskStateRepository
        .findById(accountId)
        .orElseGet(() -> createNewRiskState(accountId));

    // 이미 차단된 상태면 중복 처리 방지
    if (riskState.isBlocked()) {
      log.debug("⚠️ Account already blocked: accountId={}", accountId);
      return;
    }

    // 차단 처리
    riskState.block(reason);
    riskStateRepository.save(riskState);

    // Kafka로 A 모듈에 알림
    riskCommandPublisher.publishBlockAccount(accountId, reason);

    log.warn("🚫 Account BLOCKED: accountId={}, reason={}", accountId, reason);
  }

  /**
   * 계정 차단 해제
   * - RiskState 업데이트
   * - Kafka로 A 모듈에 알림
   */
  @Transactional
  public void unblockAccount(Long accountId) {
    Optional<RiskState> riskStateOpt = riskStateRepository.findById(accountId);

    if (riskStateOpt.isEmpty()) {
      log.warn("⚠️ RiskState not found for unblock: accountId={}", accountId);
      return;
    }

    RiskState riskState = riskStateOpt.get();

    // 이미 정상 상태면 중복 처리 방지
    if (riskState.isNormal()) {
      log.debug("⚠️ Account already normal: accountId={}", accountId);
      return;
    }

    // 차단 해제
    riskState.unblock();
    riskStateRepository.save(riskState);

    // Kafka로 A 모듈에 알림
    riskCommandPublisher.publishUnblockAccount(accountId);

    log.info("✅ Account UNBLOCKED: accountId={}", accountId);
  }

  /**
   * 계정 리스크 상태 조회
   */
  @Transactional(readOnly = true)
  public Optional<RiskState> getRiskState(Long accountId) {
    return riskStateRepository.findById(accountId);
  }

  /**
   * 계정 차단 여부 확인
   */
  @Transactional(readOnly = true)
  public boolean isBlocked(Long accountId) {
    return riskStateRepository.findById(accountId)
        .map(RiskState::isBlocked)
        .orElse(false);  // RiskState 없으면 정상으로 간주
  }

  /**
   * 모든 차단된 계정 조회
   */
  @Transactional(readOnly = true)
  public List<RiskState> getBlockedAccounts() {
    return riskStateRepository.findBlockedAccounts();
  }

  /**
   * 새로운 RiskState 생성 (NORMAL 상태)
   */
  private RiskState createNewRiskState(Long accountId) {
    return RiskState.builder()
        .accountId(accountId)
        .status(RiskStatus.NORMAL)
        .build();
  }
}
