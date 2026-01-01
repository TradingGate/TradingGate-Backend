package org.tradinggate.backend.trading.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.tradinggate.backend.global.exception.CustomException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyService 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("멱등성 서비스 테스트")
@SuppressWarnings("null")
class IdempotencyServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private IdempotencyService idempotencyService;

  @Test
  @DisplayName("신규 주문 - 멱등성 체크 성공")
  void checkAndLock_NewOrder_Success() {
    // given
    Long userId = 1L;
    String clientOrderId = "cli-20241204-0001";

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), eq("NEW"), any(Duration.class)))
        .thenReturn(true);

    // when & then
    assertDoesNotThrow(() -> idempotencyService.checkAndLock(userId, clientOrderId));

    verify(valueOperations).setIfAbsent(
        eq("order:idempotency:1:cli-20241204-0001"),
        eq("NEW"),
        eq(Duration.ofMinutes(30)));
  }

  @Test
  @DisplayName("중복 주문 - DuplicateOrderException 발생")
  void checkAndLock_DuplicateOrder_ThrowsException() {
    // given
    Long userId = 1L;
    String clientOrderId = "cli-20241204-0001";

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), eq("NEW"), any(Duration.class)))
        .thenReturn(false);

    // when & then
    CustomException exception = assertThrows(
        CustomException.class,
        () -> idempotencyService.checkAndLock(userId, clientOrderId));

    assertTrue(exception.getMessage().contains("already exists"));
    verify(valueOperations).setIfAbsent(anyString(), eq("NEW"), any(Duration.class));
  }

  @Test
  @DisplayName("실패 시 Redis 키 삭제")
  void markFailed_DeletesKey() {
    // given
    Long userId = 1L;
    String clientOrderId = "cli-20241204-0001";
    String expectedKey = "order:idempotency:1:cli-20241204-0001";

    // when
    idempotencyService.markFailed(userId, clientOrderId);

    // then
    verify(redisTemplate, times(1)).delete(expectedKey);
    verifyNoMoreInteractions(redisTemplate);
  }
}
