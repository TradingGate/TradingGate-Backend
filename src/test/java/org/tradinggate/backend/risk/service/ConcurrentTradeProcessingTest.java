package org.tradinggate.backend.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.kafka.dto.TradeExecutedEvent;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;
import org.tradinggate.backend.risk.service.orchestrator.TradeProcessingOrchestrator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 테스트
 * 
 * 검증 목표:
 * - 멀티 스레드 환경에서 트랜잭션 무결성
 * - 동시 거래 처리 시 잔고 정합성
 * - Race condition 방지
 */
@SpringBootTest
@ActiveProfiles("risk")
public class ConcurrentTradeProcessingTest {

    @Autowired
    private TradeProcessingOrchestrator orchestrator;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private AccountBalanceRepository balanceRepository;

    private static final Long ACCOUNT_ID = 3000L;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        balanceRepository.deleteAll();

        // 초기 잔고 설정
        createBalance(ACCOUNT_ID, "BTC", new BigDecimal("100.0"));
        createBalance(ACCOUNT_ID, "USDT", new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("동시성: 10개 스레드 동시 매도")
    void testConcurrentSellOrders() throws InterruptedException {
        // Given: 10개 스레드에서 각각 BTC 0.1씩 매도
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    TradeExecutedEvent event = createSellEvent("TRD-CONCURRENT-" + index, "0.1");
                    boolean success = orchestrator.processTrade(event);
                    if (success) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 모든 거래 성공, 잔고 정확
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertBalance(ACCOUNT_ID, "BTC", "99.0"); // 100 - (0.1 * 10)
    }

    @Test
    @DisplayName("동시성: 중복 이벤트 동시 처리 (멱등성)")
    void testConcurrentDuplicateEvents() throws InterruptedException {
        // Given: 같은 tradeId를 가진 이벤트를 10개 스레드에서 동시 처리
        int threadCount = 10;
        String sameTradeId = "TRD-DUPLICATE";
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger processedCount = new AtomicInteger(0);

        TradeExecutedEvent event = createSellEvent(sameTradeId, "1.0");

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orchestrator.processTrade(event);
                    processedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 원장은 1개만, 잔고도 1번만 차감됨
        long ledgerCount = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "BTC").size();
        assertThat(ledgerCount).isEqualTo(1);
        assertBalance(ACCOUNT_ID, "BTC", "99.0"); // 100 - 1 (한 번만)
    }

    @Test
    @DisplayName("동시성: 매수/매도 혼합 100회")
    void testConcurrentMixedOrders() throws InterruptedException, ExecutionException {
        // Given: 50개 매수 + 50개 매도
        int totalOrders = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<Boolean>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < totalOrders; i++) {
            final int index = i;
            Future<Boolean> future = executor.submit(() -> {
                TradeExecutedEvent event;
                if (index % 2 == 0) {
                    // 매수: BTC +0.01, USDT -500
                    event = createBuyEvent("TRD-MIX-" + index, "0.01");
                } else {
                    // 매도: BTC -0.01, USDT +500
                    event = createSellEvent("TRD-MIX-" + index, "0.01");
                }
                return orchestrator.processTrade(event);
            });
            futures.add(future);
        }

        // 모든 작업 완료 대기
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        executor.shutdown();

        // Then: 모든 거래 성공
        assertThat(successCount).isEqualTo(totalOrders);

        // 최종 잔고: BTC는 변화 없음 (매수 50 * 0.01 - 매도 50 * 0.01 = 0)
        assertBalance(ACCOUNT_ID, "BTC", "100.0");
    }

    @Test
    @DisplayName("동시성: 다중 계정 동시 거래")
    void testConcurrentMultipleAccounts() throws InterruptedException {
        // Given: 계정 3개, 각 10회 거래
        Long[] accounts = { 3001L, 3002L, 3003L };
        for (Long accountId : accounts) {
            createBalance(accountId, "BTC", new BigDecimal("10.0"));
            createBalance(accountId, "USDT", new BigDecimal("100000"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(30);
        CountDownLatch latch = new CountDownLatch(30);

        // When
        for (Long accountId : accounts) {
            for (int i = 0; i < 10; i++) {
                final Long acc = accountId;
                final int idx = i;
                executor.submit(() -> {
                    try {
                        TradeExecutedEvent event = TradeExecutedEvent.builder()
                                .tradeId("TRD-" + acc + "-" + idx)
                                .accountId(acc)
                                .symbol("BTCUSDT")
                                .side("SELL")
                                .quantity(new BigDecimal("0.1"))
                                .price(new BigDecimal("50000"))
                                .fee(new BigDecimal("5"))
                                .feeAsset("USDT")
                                .executedAt(LocalDateTime.now())
                                .build();
                        orchestrator.processTrade(event);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 각 계정별 잔고 정확
        for (Long accountId : accounts) {
            assertBalance(accountId, "BTC", "9.0"); // 10 - (0.1 * 10)
        }
    }

    @Test
    @DisplayName("동시성: 스레드 안전성 - 잔고 증감 1000회")
    void testThreadSafety_BalanceUpdates() throws InterruptedException {
        // Given
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(iterations);

        // When: 500회 매도 + 500회 매수 (각 0.001 BTC)
        for (int i = 0; i < iterations; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    TradeExecutedEvent event;
                    if (index % 2 == 0) {
                        event = createSellEvent("TRD-SAFE-" + index, "0.001");
                    } else {
                        event = createBuyEvent("TRD-SAFE-" + index, "0.001");
                    }
                    orchestrator.processTrade(event);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 원장 개수 = 1000개
        long ledgerCount = ledgerRepository.findByAccountIdAndAssetOrderByCreatedAtDesc(ACCOUNT_ID, "BTC").size();
        assertThat(ledgerCount).isEqualTo(iterations);

        // 잔고: 100 + (500 * 0.001 - 500 * 0.001) = 100
        assertBalance(ACCOUNT_ID, "BTC", "100.0");
    }

    @Test
    @DisplayName("동시성: 대량 거래 부하 테스트 (1000건)")
    void testHighLoad_1000Trades() throws InterruptedException {
        // Given
        int tradeCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(tradeCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // When
        for (int i = 0; i < tradeCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    TradeExecutedEvent event = createSellEvent("TRD-BULK-" + index, "0.001");
                    if (orchestrator.processTrade(event)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(successCount.get()).isEqualTo(tradeCount);
        assertBalance(ACCOUNT_ID, "BTC", "99.0"); // 100 - (1000 * 0.001)

        System.out.printf("✅ 1000건 처리 완료: %dms (평균 %.2fms/건)%n", duration, (double) duration / tradeCount);
    }

    // === Helper Methods ===

    private TradeExecutedEvent createBuyEvent(String tradeId, String quantity) {
        return TradeExecutedEvent.builder()
                .tradeId(tradeId)
                .accountId(ACCOUNT_ID)
                .symbol("BTCUSDT")
                .side("BUY")
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal("50000"))
                .fee(new BigDecimal("0.01"))
                .feeAsset("USDT")
                .executedAt(LocalDateTime.now())
                .build();
    }

    private TradeExecutedEvent createSellEvent(String tradeId, String quantity) {
        return TradeExecutedEvent.builder()
                .tradeId(tradeId)
                .accountId(ACCOUNT_ID)
                .symbol("BTCUSDT")
                .side("SELL")
                .quantity(new BigDecimal(quantity))
                .price(new BigDecimal("50000"))
                .fee(new BigDecimal("0.01"))
                .feeAsset("USDT")
                .executedAt(LocalDateTime.now())
                .build();
    }

    private void createBalance(Long accountId, String asset, BigDecimal amount) {
        AccountBalance balance = AccountBalance.builder()
                .accountId(accountId)
                .asset(asset)
                .available(amount)
                .locked(BigDecimal.ZERO)
                .build();
        balanceRepository.save(balance);
    }

    private void assertBalance(Long accountId, String asset, String expected) {
        BigDecimal actual = balanceRepository.findByAccountIdAndAsset(accountId, asset)
                .map(AccountBalance::getAvailable)
                .orElse(BigDecimal.ZERO);
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
