package org.tradinggate.backend.trading.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.Order;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;
import org.tradinggate.backend.trading.domain.repository.OrderRepository;
import org.tradinggate.backend.global.exception.CustomException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 멱등성 테스트
 *
 * Profile: api (Trading API Layer만 테스트)
 * Matching Engine 컴포넌트는 제외하고 테스트
 */
@SpringBootTest
@ActiveProfiles("api")
@EmbeddedKafka(
    partitions = 1,
    topics = {"orders.in"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.enabled=false"
})
@ComponentScan(
    basePackages = "org.tradinggate.backend",
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "org\\.tradinggate\\.backend\\.matching\\..*"
        )
    }
)
@DisplayName("주문 서비스 - 멱등성 테스트 (API Layer)")
class OrderServiceIdempotencyTest {

  @Autowired
  private OrderService orderService;

  @Autowired
  private OrderRepository orderRepository;

  @MockitoBean
  private KafkaTemplate<String, String> kafkaTemplate;

  @MockitoBean
  private RedisTemplate<String, Object> redisTemplate;

  @MockitoBean
  private RedissonClient redissonClient;

  @Mock
  private ValueOperations<String, Object> valueOperations;

  @Mock
  private RLock rLock;

  private static final Long TEST_USER_ID = 1L;
  private static final Long TEST_USER_ID_2 = 2L;
  private static final Long TEST_USER_ID_100 = 100L;
  private static final Long TEST_USER_ID_200 = 200L;

  @BeforeEach
  void setUp() throws InterruptedException {
    // 테스트 데이터 정리
    orderRepository.deleteAll();

    // Redis Mock 설정 - 멱등성 키 체크
    when(redisTemplate.hasKey(anyString())).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any()))
        .thenReturn(true);

    // RedissonClient Config Mock
    Config mockConfig = new Config();
    mockConfig.useSingleServer().setAddress("redis://localhost:6379");
    when(redissonClient.getConfig()).thenReturn(mockConfig);

    // Distributed Lock Mock
    when(redissonClient.getLock(anyString())).thenReturn(rLock);
    when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
    doNothing().when(rLock).unlock();

    // Kafka Producer Mock - orders.in으로 발행만 확인
    when(kafkaTemplate.send(eq("orders.in"), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("동일한 clientOrderId로 중복 요청 시 예외 발생")
  void createOrder_withDuplicateClientOrderId_shouldThrowException() {
    // Given
    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId("cli-test-001")
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();

    // When - 첫 번째 주문 생성 성공
    assertDoesNotThrow(() -> orderService.createOrder(request, TEST_USER_ID));

    // Redis Mock 동작 변경 - 이미 존재하는 키로 설정
    when(redisTemplate.hasKey("order:idempotency:" + TEST_USER_ID + ":cli-test-001"))
        .thenReturn(true);

    // Then - 동일한 clientOrderId로 재시도 시 예외 발생
    CustomException exception = assertThrows(CustomException.class,
        () -> orderService.createOrder(request, TEST_USER_ID));

    assertTrue(exception.getMessage().contains("중복") ||
            exception.getMessage().contains("이미"),
        "중복 주문 예외 메시지 확인");

    // DB에 1개만 저장되어야 함
    List<Order> orders = orderRepository.findAll();
    assertEquals(1, orders.size(), "DB에 정확히 1개의 주문만 존재해야 함");

    // Kafka는 1번만 호출되어야 함
    verify(kafkaTemplate, times(1))
        .send(eq("orders.in"), anyString(), anyString());
  }

  @Test
  @DisplayName("동시 요청 시 하나만 성공 (Distributed Lock)")
  void createOrder_withConcurrentRequests_shouldOnlyOneSucceed()
      throws InterruptedException {

    // Given
    int threadCount = 5;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // Lock Mock 동작: 첫 번째 스레드만 락 획득 성공
    AtomicInteger lockAttempts = new AtomicInteger(0);
    when(rLock.tryLock(anyLong(), anyLong(), any())).thenAnswer(invocation -> {
      return lockAttempts.getAndIncrement() == 0; // 첫 번째만 true
    });

    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId("cli-concurrent-001")
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();

    // When - 동시에 5개의 동일한 요청 실행
    for (int i = 0; i < threadCount; i++) {
      executorService.submit(() -> {
        try {
          orderService.createOrder(request, TEST_USER_ID_2);
          successCount.incrementAndGet();
        } catch (CustomException e) {
          // 락 획득 실패 또는 중복 주문 예외
          failCount.incrementAndGet();
        } catch (Exception e) {
          System.err.println("Unexpected exception: " + e.getMessage());
          e.printStackTrace();
          failCount.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    boolean finished = latch.await(10, TimeUnit.SECONDS);
    executorService.shutdown();
    executorService.awaitTermination(5, TimeUnit.SECONDS);

    // Then
    assertTrue(finished, "모든 스레드가 10초 내에 완료되어야 함");
    assertEquals(1, successCount.get(), "정확히 1개의 요청만 성공해야 함");
    assertEquals(threadCount - 1, failCount.get(),
        "나머지 " + (threadCount - 1) + "개는 실패해야 함");

    // DB에도 1개만 저장되어야 함
    List<Order> orders = orderRepository.findAll();
    assertEquals(1, orders.size(), "DB에 1개의 주문만 저장되어야 함");

    // Kafka도 1번만 호출
    verify(kafkaTemplate, times(1))
        .send(eq("orders.in"), anyString(), anyString());
  }

  @Test
  @DisplayName("다른 userId는 같은 clientOrderId 사용 가능")
  void createOrder_withDifferentUser_shouldAllowSameClientOrderId() {
    // Given
    String sameClientOrderId = "cli-multi-user-001";

    OrderCreateRequest request1 = OrderCreateRequest.builder()
        .clientOrderId(sameClientOrderId)
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();

    OrderCreateRequest request2 = OrderCreateRequest.builder()
        .clientOrderId(sameClientOrderId)  // 같은 clientOrderId
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();

    // Redis Mock: 각 userId별로 다른 키로 관리
    when(redisTemplate.hasKey("order:idempotency:" + TEST_USER_ID_100 + ":" + sameClientOrderId))
        .thenReturn(false);
    when(redisTemplate.hasKey("order:idempotency:" + TEST_USER_ID_200 + ":" + sameClientOrderId))
        .thenReturn(false);

    // When & Then - 둘 다 성공해야 함
    assertDoesNotThrow(() -> orderService.createOrder(request1, TEST_USER_ID_100),
        "User 100의 주문은 성공해야 함");
    assertDoesNotThrow(() -> orderService.createOrder(request2, TEST_USER_ID_200),
        "User 200의 주문은 성공해야 함");

    // 2개의 주문이 모두 생성되어야 함
    List<Order> orders = orderRepository.findAll();
    assertEquals(2, orders.size(), "2개의 주문이 모두 생성되어야 함");

    // 각 유저의 주문 확인
    assertTrue(orders.stream().anyMatch(o -> o.getUserId().equals(TEST_USER_ID_100)),
        "User 100의 주문이 존재해야 함");
    assertTrue(orders.stream().anyMatch(o -> o.getUserId().equals(TEST_USER_ID_200)),
        "User 200의 주문이 존재해야 함");

    // Kafka는 2번 호출 (각 유저별 1번씩)
    verify(kafkaTemplate, times(2))
        .send(eq("orders.in"), anyString(), anyString());
  }
}
