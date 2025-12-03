package org.tradinggate.backend.trading.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [A-1] Trading API - 리스크 상태 체크 서비스
 *
 * 역할:
 * - Redis에서 리스크 차단 상태 확인
 * - Risk Management Layer(B-2)에서 발행한 risk.commands를
 *   Worker 프로필이 Redis에 저장 → API는 읽기만
 *
 * TODO:
 * [ ] isBlocked(Long userId, String symbol) 구현:
 *     1. Redis key 조회:
 *        - "risk:block:{userId}" (계정 전체 차단)
 *        - "risk:block:{userId}:{symbol}" (심볼별 차단)
 *     2. 값이 존재하면:
 *        → RiskBlockedException 발생
 *     3. 없으면 정상 진행
 *
 * [ ] Redis 읽기만 수행 (API Layer는 Consumer 없음)
 *
 * [ ] 예외: RiskBlockedException
 *     - errorCode: "RISK_BLOCKED"
 *     - message: "User is blocked due to risk limit"
 *
 * 참고: PDF 2-2, B-3 (리스크 제어 이벤트 발행)
 */
@Service
@Profile("api")
public class RiskCheckService {

  // TODO: RedisTemplate 주입

  // TODO: isBlocked(userId, symbol) 구현

  // TODO: RiskBlockedException 정의
}
