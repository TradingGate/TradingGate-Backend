package org.tradinggate.backend.trading.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * [A-1] Trading API - 멱등성 관리 서비스
 *
 * 역할:
 * - Redis를 이용해 중복 요청 방지
 * - (userId, clientOrderId) 조합으로 체크
 *
 * TODO:
 * [ ] checkAndLock(Long userId, String clientOrderId) 구현:
 *     1. Redis key 생성: "order:idempotency:{userId}:{clientOrderId}"
 *     2. Redis GET - 이미 존재하면:
 *        → DuplicateOrderException 발생 (이전 결과 반환 가능)
 *     3. 없으면:
 *        → Redis SET key "PENDING" EX 1800 (TTL 30분)
 *        → 정상 진행
 *
 * [ ] markCompleted(Long userId, String clientOrderId)
 *     - 주문 처리 완료 시 상태 변경
 *     - Redis SET key "COMPLETED" (선택)
 *
 * [ ] markFailed(Long userId, String clientOrderId)
 *     - 실패 시 상태 변경 또는 삭제
 *
 * [ ] TTL 관리: 30분 권장 (1800초)
 *
 * 참고: PDF 2-2 (멱등성 1차 체크)
 */
@Service
@Profile("api")
public class IdempotencyService {

  // TODO: RedisTemplate<String, String> 주입

  // TODO: checkAndLock() 구현

  // TODO: markCompleted() 구현 (선택)

  // TODO: markFailed() 구현 (선택)
}
