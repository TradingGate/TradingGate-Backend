package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.trading.exception.DuplicateOrderException;

import java.time.Duration;

/**
 * [A-1] Trading API - 멱등성 관리 서비스
 * 역할:
 * - Redis를 이용해 중복 요청 방지
 * - (userId, clientOrderId) 조합으로 체크
 * TODO:
 * [✅️] checkAndLock(Long userId, String clientOrderId) 구현:
 *     1. Redis key 생성: "order:idempotency:{userId}:{clientOrderId}"
 *     2. Redis GET - 이미 존재하면:
 *        → DuplicateOrderException 발생 (이전 결과 반환 가능)
 *     3. 없으면:
 *        → Redis SET key "PENDING" EX 1800 (TTL 30분)
 *        → 정상 진행
 * [ ] markCompleted(Long userId, String clientOrderId)
 *     - 주문 처리 완료 시 상태 변경
 *     - Redis SET key "COMPLETED" (선택)
 * [✅️] markFailed(Long userId, String clientOrderId)
 *     - 실패 시 상태 삭제
 * [ ] TTL 관리: 30분 권장 (1800초)
 */
@Slf4j
@Service
@Profile("api")
@RequiredArgsConstructor
public class IdempotencyService {

  private final StringRedisTemplate redisTemplate;

  private static final String KEY_PREFIX = "order:idempotency:";
  private static final Duration TTL = Duration.ofMinutes(30);

  /**멱등성 체크 및 잠금*/
  public void checkAndLock(Long userId, String clientOrderId) {
    String key = KEY_PREFIX + userId + ":" + clientOrderId;

    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "PENDING", TTL);

    if (Boolean.FALSE.equals(isNew)) {
      log.warn("Duplicate order detected: userId={}, clientOrderId={}", userId, clientOrderId);
      throw new DuplicateOrderException(
          String.format("Duplicate order: clientOrderId=%s already exists", clientOrderId)
      );
    }

    log.info("Idempotency lock acquired: userId={}, clientOrderId={}", userId, clientOrderId);
  }

  /*** 실패 시 삭제*/
  public void markFailed(Long userId, String clientOrderId) {
    String key = KEY_PREFIX + userId + ":" + clientOrderId;
    redisTemplate.delete(key);
    log.debug("Idempotency lock released: userId={}, clientOrderId={}", userId, clientOrderId);
  }
}
