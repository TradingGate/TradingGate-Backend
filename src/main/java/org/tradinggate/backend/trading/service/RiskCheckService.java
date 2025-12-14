package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.tradinggate.backend.trading.exception.RiskBlockedException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 리스크 차단 체크 서비스
 * - Redis 기반 실시간 리스크 모니터링
 * - 일일 거래 한도, 거래 빈도 제한
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class RiskCheckService {

  private final StringRedisTemplate redisTemplate;

  private static final String RISK_BLOCK_KEY_PREFIX = "risk:block:user:";
  private static final String DAILY_VOLUME_KEY_PREFIX = "risk:volume:user:";
  private static final String ORDER_COUNT_KEY_PREFIX = "risk:count:user:";

  private static final BigDecimal DAILY_VOLUME_LIMIT = new BigDecimal("100000000");
  private static final int DAILY_ORDER_COUNT_LIMIT = 1000;
  private static final int ORDER_RATE_LIMIT_PER_MINUTE = 10;

  /**
   * 사용자가 리스크 차단되었는지 확인
   */
  public boolean isBlocked(Long userId) {
    String key = RISK_BLOCK_KEY_PREFIX + userId;
    Boolean blocked = redisTemplate.hasKey(key);

    if (Boolean.TRUE.equals(blocked)) {
      log.warn("리스크 차단된 사용자: userId={}", userId);
      return true;
    }

    return false;
  }

  /**
   * 사용자가 특정 심볼에 대해 리스크 차단되었는지 확인
   */
  public boolean isBlocked(Long userId, String symbol) {
    // 현재는 심볼별 차단 로직이 없으므로 사용자 레벨 차단만 확인
    return isBlocked(userId);
  }

  /**
   * 사용자 리스크 차단
   */
  public void blockUser(Long userId, Duration duration) {
    String key = RISK_BLOCK_KEY_PREFIX + userId;
    redisTemplate.opsForValue().set(key, "BLOCKED", Objects.requireNonNull(duration));
    log.warn("사용자 리스크 차단: userId={}, duration={}", userId, duration);
  }

  /**
   * 일일 거래량 체크
   */
  public boolean checkDailyVolume(Long userId, BigDecimal orderAmount) {
    String key = DAILY_VOLUME_KEY_PREFIX + userId;
    String currentVolumeStr = redisTemplate.opsForValue().get(key);

    BigDecimal currentVolume = currentVolumeStr != null
        ? new BigDecimal(currentVolumeStr)
        : BigDecimal.ZERO;

    BigDecimal newVolume = currentVolume.add(orderAmount);

    if (newVolume.compareTo(DAILY_VOLUME_LIMIT) > 0) {
      log.warn("일일 거래량 한도 초과: userId={}, current={}, limit={}",
          userId, newVolume, DAILY_VOLUME_LIMIT);
      blockUser(userId, Objects.requireNonNull(Duration.ofHours(24)));
      return false;
    }

    redisTemplate.opsForValue().set(key, Objects.requireNonNull(newVolume.toString()),
        Objects.requireNonNull(Duration.ofDays(1)));
    return true;
  }

  /**
   * 일일 주문 횟수 체크
   */
  public boolean checkDailyOrderCount(Long userId) {
    String key = ORDER_COUNT_KEY_PREFIX + userId + ":daily";
    Long count = redisTemplate.opsForValue().increment(key);

    if (count != null && count == 1) {
      redisTemplate.expire(key, Objects.requireNonNull(Duration.ofDays(1)));
    }

    if (count != null && count > DAILY_ORDER_COUNT_LIMIT) {
      log.warn("일일 주문 횟수 한도 초과: userId={}, count={}", userId, count);
      blockUser(userId, Objects.requireNonNull(Duration.ofHours(24)));
      return false;
    }

    return true;
  }

  /**
   * 분당 주문 빈도 체크
   */
  public boolean checkOrderRateLimit(Long userId) {
    String key = ORDER_COUNT_KEY_PREFIX + userId + ":minute";
    Long count = redisTemplate.opsForValue().increment(key);

    if (count != null && count == 1) {
      redisTemplate.expire(key, 1, TimeUnit.MINUTES);
    }

    if (count != null && count > ORDER_RATE_LIMIT_PER_MINUTE) {
      log.warn("분당 주문 빈도 제한 초과: userId={}, count={}", userId, count);
      return false;
    }

    return true;
  }

  /**
   * 종합 리스크 체크
   */
  public void validateRisk(Long userId, BigDecimal orderAmount) {
    if (isBlocked(userId)) {
      throw new RiskBlockedException("리스크 차단된 사용자입니다");
    }

    if (!checkDailyVolume(userId, orderAmount)) {
      throw new RiskBlockedException("일일 거래량 한도를 초과했습니다");
    }

    if (!checkDailyOrderCount(userId)) {
      throw new RiskBlockedException("일일 주문 횟수 한도를 초과했습니다");
    }

    if (!checkOrderRateLimit(userId)) {
      throw new RiskBlockedException("주문 빈도가 너무 높습니다");
    }
  }
}
