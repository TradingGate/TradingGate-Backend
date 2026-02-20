package org.tradinggate.backend.trading.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("api")
@DisplayName("주문 서비스 - 멱등성 로직 검증 (Mock Redis)")
class OrderServiceIdempotencyTest {

  @Autowired
  private OrderService orderService;

  @Autowired
  private OrderRepository orderRepository;

  @MockitoBean
  private KafkaTemplate<String, String> kafkaTemplate;

  // 서비스 코드에서 RedisTemplate을 통해 중복 키 검사를 한다면 필요합니다.
  @MockitoBean
  private RedisTemplate<String, Object> redisTemplate;

  // 분산 락(Redisson) Mock
  @MockitoBean
  private RedissonClient redissonClient;

  @MockitoBean
  private OrderRiskValidationService riskCheckService;

  @Mock
  private RLock rLock;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  private static final Long TEST_USER_ID = 1L;

  @BeforeEach
  void setUp() throws InterruptedException {
    // 1. 데이터 초기화
    orderRepository.deleteAll();

    // 2. RedisTemplate Mock 설정 (중복 검사 로직 통과용)
    // opsForValue() 호출 시 mock 객체 반환
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    // 기본적으로 키가 없다고 설정 (hasKey -> false)
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    // setIfAbsent(락 점유 등)는 성공(true)으로 설정
    when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);

    // 3. Redisson 락 Mock 설정
    when(redissonClient.getLock(anyString())).thenReturn(rLock);

    // 중요: tryLock이 InterruptedException을 던질 수 있으므로 메서드 시그니처에 throws 추가 필요
    // 기본적으로 락 획득 성공(true) 설정
    when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
    // unlock은 아무 일도 안 함
    doNothing().when(rLock).unlock();

    // 4. Risk Check 통과 설정
    when(riskCheckService.isBlocked(anyLong(), anyString())).thenReturn(false);

    // 5. Kafka 전송 성공 설정
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("중복 주문 요청 시 서비스 로직이 예외를 던지는지 확인")
  void createOrder_duplicate_shouldThrow() {
    // Given
    OrderCreateRequest request = createSampleRequest();

    // When 1: 첫 번째 주문 생성 (성공해야 함)
    assertDoesNotThrow(() -> orderService.createOrder(request, TEST_USER_ID));

    // Mock 상태 변경: 이제 Redis에 해당 키가 존재한다고 가정
    // (서비스 코드 구현 방식에 따라 redisTemplate.hasKey 등을 통해 중복을 감지한다고 가정)
    when(redisTemplate.hasKey(anyString())).thenReturn(true);

    // When 2: 동일한 주문 ID로 재요청
    // Then: CustomException 발생 예상
    CustomException exception = assertThrows(CustomException.class,
        () -> orderService.createOrder(request, TEST_USER_ID));

    // 예외 메시지 검증 (필요시 수정)
    // assertTrue(exception.getMessage().contains("중복") || exception.getMessage().contains("이미"));

    // DB 검증: 주문은 1개만 저장되어야 함
    assertEquals(1, orderRepository.findAll().size());
  }

  @Test
  @DisplayName("락 획득 실패 시 주문이 생성되지 않아야 함 (동시성 시뮬레이션)")
  void createOrder_concurrency_simulation() throws InterruptedException {
    // Given
    int threadCount = 5;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // 핵심: 락 획득 시나리오 조작
    // 첫 번째 호출만 true, 나머지는 false 리턴하도록 조작
    AtomicInteger lockAttempts = new AtomicInteger(0);

    // 여기서도 throws InterruptedException 때문에 메서드 시그니처 수정 필수
    when(rLock.tryLock(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
      // 첫 번째 스레드만 true(락 획득), 나머지는 false(실패)
      return lockAttempts.getAndIncrement() == 0;
    });

    OrderCreateRequest request = createSampleRequest();

    // When
    for (int i = 0; i < threadCount; i++) {
      executorService.submit(() -> {
        try {
          orderService.createOrder(request, TEST_USER_ID);
          successCount.incrementAndGet();
        } catch (CustomException e) {
          // 락 획득 실패로 인한 예외 (서비스에서 락 실패시 예외 던진다고 가정)
          failCount.incrementAndGet();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          latch.countDown();
        }
      });
    }

    // 대기
    latch.await(5, TimeUnit.SECONDS);

    // 스레드 풀 정리
    executorService.shutdown();

    // Then
    // 락 시나리오대로라면 1명만 성공해야 함
    assertEquals(1, successCount.get(), "락을 획득한 1개의 요청만 성공해야 합니다.");
    assertEquals(threadCount - 1, failCount.get(), "나머지 요청은 락 획득 실패로 거절되어야 합니다.");

    // 실제 DB 저장 건수 확인 (가장 중요)
    assertEquals(1, orderRepository.findAll().size(), "DB에는 1개의 주문만 저장되어야 합니다.");
  }

  // 테스트용 요청 객체 생성 헬퍼 메서드
  private OrderCreateRequest createSampleRequest() {
    return OrderCreateRequest.builder()
        .clientOrderId("cli-test-001")
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();
  }
}