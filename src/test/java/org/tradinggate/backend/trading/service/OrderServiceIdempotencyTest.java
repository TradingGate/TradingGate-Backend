package org.tradinggate.backend.trading.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.global.exception.CustomException;
import org.tradinggate.backend.trading.api.dto.request.OrderCreateRequest;
import org.tradinggate.backend.trading.domain.entity.OrderSide;
import org.tradinggate.backend.trading.domain.entity.OrderType;
import org.tradinggate.backend.trading.domain.entity.TimeInForce;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("api")
@DisplayName("주문 서비스 - 멱등성 테스트")
class OrderServiceIdempotencyTest {

  @Autowired
  private OrderService orderService;

  @Test
  @DisplayName("동일한 clientOrderId로 중복 요청 시 예외 발생")
  void createOrder_DuplicateClientOrderId_ThrowsException() throws InterruptedException {
    // given
    Long userId = 1L;
    String clientOrderId = "cli-test-001";

    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId(clientOrderId)
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();

    // when: 첫 번째 요청 성공
    OrderService.OrderCreateResponse response1 = orderService.createOrder(request, userId);
    assertNotNull(response1);
    assertTrue(response1.getReceived());

    // then: 두 번째 요청 실패 (중복)
    Thread.sleep(100); // Lock 처리 대기

    CustomException exception = assertThrows(CustomException.class,
        () -> orderService.createOrder(request, userId));

    assertEquals("중복 요청이 감지되었습니다.", exception.getMessage());
  }

  @Test
  @DisplayName("동시 요청 시 하나만 성공")
  void createOrder_ConcurrentRequests_OnlyOneSucceeds() throws InterruptedException {
    // given
    Long userId = 2L;
    String clientOrderId = "cli-concurrent-001";
    int threadCount = 5;

    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId(clientOrderId)
        .symbol("ETHUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(3000))
        .quantity(BigDecimal.valueOf(1.0))
        .build();

    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // when: 동시에 5개 요청
    for (int i = 0; i < threadCount; i++) {
      executorService.submit(() -> {
        try {
          orderService.createOrder(request, userId);
          successCount.incrementAndGet();
        } catch (CustomException e) {
          failCount.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();
    executorService.shutdown();

    // then: 1개만 성공, 4개는 실패
    assertEquals(1, successCount.get(), "정확히 1개의 요청만 성공해야 함");
    assertEquals(4, failCount.get(), "나머지 4개는 중복으로 실패해야 함");
  }

  @Test
  @DisplayName("다른 userId는 같은 clientOrderId 사용 가능")
  void createOrder_DifferentUser_SameClientOrderId_Success() {
    // given
    String clientOrderId = "cli-multi-user-001";

    OrderCreateRequest request = OrderCreateRequest.builder()
        .clientOrderId(clientOrderId)
        .symbol("BTCUSDT")
        .orderSide(OrderSide.BUY)
        .orderType(OrderType.LIMIT)
        .timeInForce(TimeInForce.GTC)
        .price(BigDecimal.valueOf(50000))
        .quantity(BigDecimal.valueOf(0.1))
        .build();

    // when & then: 다른 유저는 같은 clientOrderId 사용 가능
    assertDoesNotThrow(() -> orderService.createOrder(request, 100L));
    assertDoesNotThrow(() -> orderService.createOrder(request, 200L));
  }
}
