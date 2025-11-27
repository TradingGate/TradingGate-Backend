package org.tradinggate.backend.matching.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradinggate.backend.matching.domain.MatchFill;
import org.tradinggate.backend.matching.domain.Order;
import org.tradinggate.backend.matching.domain.OrderUpdate;
import org.tradinggate.backend.matching.domain.e.OrderSide;
import org.tradinggate.backend.matching.domain.e.OrderStatus;
import org.tradinggate.backend.matching.domain.e.OrderType;
import org.tradinggate.backend.matching.domain.e.TimeInForce;
import org.tradinggate.backend.matching.service.KafkaMatchingEventPublisher;
import org.tradinggate.backend.matching.service.KafkaMessageProducer;
import org.tradinggate.backend.matching.util.MatchingProperties;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaMatchingEventPublisherTest {

    @Mock
    private KafkaMessageProducer kafkaMessageProducer;

    // ObjectMapper는 진짜 인스턴스 사용 (직렬화/역직렬화 검증용)
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("orders.updated - 단일 OrderUpdate가 올바른 topic/key/payload로 발행된다")
    void publishOrderUpdates_singleUpdate() throws Exception {
        // given
        MatchingProperties props = new MatchingProperties();
        props.setOrdersUpdatedTopic("orders.updated-test");
        props.setTradesExecutedTopic("trades.executed-test");

        KafkaMatchingEventPublisher publisher =
                new KafkaMatchingEventPublisher(kafkaMessageProducer, objectMapper, props);

        long accountId = 1001L;
        long orderId = 1L;
        String clientOrderId = "cli-001";
        String symbol = "BTCUSDT";

        long receivedAtMillis = Instant.now().toEpochMilli();
        long createdAtMillis = receivedAtMillis;

        Order order = Order.createNew(
                orderId,
                accountId,
                clientOrderId,
                symbol,
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                50_000_000_000L,
                100_000_000L,
                receivedAtMillis,
                createdAtMillis
        );

        OrderStatus previousStatus = OrderStatus.NEW;
        String reasonCode = null;
        String eventType = "STATUS_CHANGED";
        long eventSeq = 1L;
        long eventTimeMillis = Instant.now().toEpochMilli();

        String eventId = "orderEvent-test-1";
        OrderUpdate update = OrderUpdate.of(
                eventId,
                order,
                previousStatus,
                reasonCode,
                eventType,
                eventTimeMillis
        );

        // when
        publisher.publishOrderUpdates(symbol, List.of(update));

        // then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaMessageProducer, times(1))
                .send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        String topic = topicCaptor.getValue();
        String key = keyCaptor.getValue();
        String payload = payloadCaptor.getValue();

        assertThat(topic).isEqualTo("orders.updated-test");
        assertThat(key).isEqualTo(String.valueOf(accountId));

        JsonNode json = objectMapper.readTree(payload);
        assertThat(json.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(json.get("accountId").asLong()).isEqualTo(accountId);
        assertThat(json.get("clientOrderId").asText()).isEqualTo(clientOrderId);
        assertThat(json.get("symbol").asText()).isEqualTo(symbol);
        assertThat(json.get("newStatus").asText()).isEqualTo(order.getStatus().name());
        assertThat(json.get("previousStatus").asText()).isEqualTo(previousStatus.name());
        assertThat(json.get("eventType").asText()).isEqualTo(eventType);
        assertThat(json.get("eventSeq").asLong()).isEqualTo(eventSeq);
    }

    @Test
    @DisplayName("trades.executed - 단일 MatchFill에 대해 taker/maker 2건이 발행된다")
    void publishMatchFills_singleFill_takerAndMakerEvents() throws Exception {
        // given
        MatchingProperties props = new MatchingProperties();
        props.setOrdersUpdatedTopic("orders.updated-test");
        props.setTradesExecutedTopic("trades.executed-test");

        KafkaMatchingEventPublisher publisher =
                new KafkaMatchingEventPublisher(kafkaMessageProducer, objectMapper, props);

        String symbol = "BTCUSDT";

        long takerAccountId = 2001L;
        long makerAccountId = 3001L;

        long takerReceivedAtMillis = Instant.now().toEpochMilli();
        long takerCreatedAtMillis = takerReceivedAtMillis;

        long makerReceivedAtMillis = Instant.now().toEpochMilli();
        long makerCreatedAtMillis = makerReceivedAtMillis;

        Order taker = Order.createNew(
                10L,
                takerAccountId,
                "cli-taker",
                symbol,
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                49_500_000_000L,
                100_000_000L,
                takerReceivedAtMillis,
                takerCreatedAtMillis
        );

        Order maker = Order.createNew(
                11L,
                makerAccountId,
                "cli-maker",
                symbol,
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                49_500_000_000L,
                100_000_000L,
                makerReceivedAtMillis,
                makerCreatedAtMillis
        );

        long matchId = 1L;
        long tradePrice = 49_500_000_000L;
        long tradeQty = 100_000_000L;
        long executedAtMillis = Instant.now().toEpochMilli();

        MatchFill fill = MatchFill.of(
                matchId,
                symbol,
                tradePrice,
                tradeQty,
                executedAtMillis,
                taker,
                maker
        );

        // when
        publisher.publishMatchFills(symbol, List.of(fill));

        // then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaMessageProducer, times(2))
                .send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        List<String> topics = topicCaptor.getAllValues();
        List<String> keys = keyCaptor.getAllValues();
        List<String> payloads = payloadCaptor.getAllValues();

        // 1) topic 모두 trades.executed-test 여야 함
        assertThat(topics).allMatch(t -> t.equals("trades.executed-test"));

        // 2) key는 takerAccountId, makerAccountId 각각 한 번씩
        assertThat(keys).containsExactlyInAnyOrder(
                String.valueOf(takerAccountId),
                String.valueOf(makerAccountId)
        );

        // 3) payload 내용 검증 (간단 버전)
        //    - 두 이벤트 모두 symbol, matchId, trade 가격/수량은 동일
        for (String payload : payloads) {
            JsonNode json = objectMapper.readTree(payload);
            assertThat(json.get("symbol").asText()).isEqualTo(symbol);
            assertThat(json.get("matchId").asLong()).isEqualTo(matchId);
            assertThat(json.get("execQuantity").asText()).isEqualTo(String.valueOf(tradeQty));
            assertThat(json.get("execPrice").asText()).isEqualTo(String.valueOf(tradePrice));

            // 이벤트 ID는 UUID 기반 문자열인지 정도만 가볍게 체크
            assertThat(json.get("eventId").asText()).startsWith("tradeEventID-");
        }

        // 4) taker 이벤트 / maker 이벤트 각각 검증 (side, liquidityFlag 등)
        JsonNode event1 = objectMapper.readTree(payloads.get(0));
        JsonNode event2 = objectMapper.readTree(payloads.get(1));

        // userId / side / liquidityFlag 가 서로 반대가 되는지 정도 확인
        if (event1.get("userId").asLong() == takerAccountId) {
            assertThat(event1.get("side").asText()).isEqualTo(OrderSide.BUY.name());
            assertThat(event1.get("liquidityFlag").asText()).isEqualTo("TAKER");

            assertThat(event2.get("userId").asLong()).isEqualTo(makerAccountId);
            assertThat(event2.get("side").asText()).isEqualTo(OrderSide.SELL.name());
            assertThat(event2.get("liquidityFlag").asText()).isEqualTo("MAKER");
        } else {
            assertThat(event1.get("userId").asLong()).isEqualTo(makerAccountId);
            assertThat(event1.get("side").asText()).isEqualTo(OrderSide.SELL.name());
            assertThat(event1.get("liquidityFlag").asText()).isEqualTo("MAKER");

            assertThat(event2.get("userId").asLong()).isEqualTo(takerAccountId);
            assertThat(event2.get("side").asText()).isEqualTo(OrderSide.BUY.name());
            assertThat(event2.get("liquidityFlag").asText()).isEqualTo("TAKER");
        }
    }

    @Test
    @DisplayName("orders.updated - 빈 리스트가 들어오면 Kafka 전송이 발생하지 않는다")
    void publishOrderUpdates_emptyList_noSend() {
        // given
        MatchingProperties props = new MatchingProperties();
        props.setOrdersUpdatedTopic("orders.updated-test");
        props.setTradesExecutedTopic("trades.executed-test");

        KafkaMatchingEventPublisher publisher =
                new KafkaMatchingEventPublisher(kafkaMessageProducer, objectMapper, props);

        String symbol = "BTCUSDT";

        // when
        publisher.publishOrderUpdates(symbol, List.of());

        // then
        verify(kafkaMessageProducer, times(0))
                .send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("trades.executed - 빈 리스트면 Kafka 전송이 발생하지 않는다")
    void publishMatchFills_emptyList_noSend() {
        // given
        MatchingProperties props = new MatchingProperties();
        props.setOrdersUpdatedTopic("orders.updated-test");
        props.setTradesExecutedTopic("trades.executed-test");

        KafkaMatchingEventPublisher publisher =
                new KafkaMatchingEventPublisher(kafkaMessageProducer, objectMapper, props);

        String symbol = "BTCUSDT";

        // when
        publisher.publishMatchFills(symbol, List.of());

        // then
        verify(kafkaMessageProducer, times(0))
                .send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("orders.updated - 서로 다른 계정의 OrderUpdate가 각각 올바른 key로 발행된다")
    void publishOrderUpdates_multiAccounts() throws Exception {
        // given
        MatchingProperties props = new MatchingProperties();
        props.setOrdersUpdatedTopic("orders.updated-test");
        props.setTradesExecutedTopic("trades.executed-test");

        KafkaMatchingEventPublisher publisher =
                new KafkaMatchingEventPublisher(kafkaMessageProducer, objectMapper, props);

        String symbol = "BTCUSDT";

        long accountId1 = 1001L;
        long accountId2 = 2001L;

        long receivedAtMillis = Instant.now().toEpochMilli();
        long createdAtMillis = receivedAtMillis;

        Order order1 = Order.createNew(
                1L,
                accountId1,
                "cli-acc1",
                symbol,
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                50_000_000_000L,
                100_000_000L,
                receivedAtMillis,
                createdAtMillis
        );

        Order order2 = Order.createNew(
                2L,
                accountId2,
                "cli-acc2",
                symbol,
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                50_000_000_000L,
                100_000_000L,
                receivedAtMillis,
                createdAtMillis
        );

        OrderStatus previousStatus = OrderStatus.NEW;
        String reasonCode = null;
        String eventType = "STATUS_CHANGED";
        long eventTimeMillis = Instant.now().toEpochMilli();

        OrderUpdate update1 = OrderUpdate.of(
                "ordevt-test-acc1",
                order1,
                previousStatus,
                reasonCode,
                eventType,
                eventTimeMillis
        );
        OrderUpdate update2 = OrderUpdate.of(
                "ordevt-test-acc2",
                order2,
                previousStatus,
                reasonCode,
                eventType,
                eventTimeMillis
        );

        // when
        publisher.publishOrderUpdates(symbol, List.of(update1, update2));

        // then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaMessageProducer, times(2))
                .send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        List<String> topics = topicCaptor.getAllValues();
        List<String> keys = keyCaptor.getAllValues();
        List<String> payloads = payloadCaptor.getAllValues();

        // topic 모두 orders.updated-test 여야 함
        assertThat(topics).allMatch(t -> t.equals("orders.updated-test"));

        // key는 각 accountId가 한 번씩
        assertThat(keys).containsExactlyInAnyOrder(
                String.valueOf(accountId1),
                String.valueOf(accountId2)
        );

        // payload 내부에서도 accountId가 올바르게 들어갔는지 확인
        JsonNode json1 = objectMapper.readTree(payloads.get(0));
        JsonNode json2 = objectMapper.readTree(payloads.get(1));

        assertThat(List.of(json1.get("accountId").asLong(), json2.get("accountId").asLong()))
                .containsExactlyInAnyOrder(accountId1, accountId2);
    }

    @Test
    @DisplayName("trades.executed - MatchFill 2건이면 taker/maker 합쳐서 4건 이벤트가 발행된다")
    void publishMatchFills_twoFills_fourEvents() throws Exception {
        // given
        MatchingProperties props = new MatchingProperties();
        props.setOrdersUpdatedTopic("orders.updated-test");
        props.setTradesExecutedTopic("trades.executed-test");

        KafkaMatchingEventPublisher publisher =
                new KafkaMatchingEventPublisher(kafkaMessageProducer, objectMapper, props);

        String symbol = "BTCUSDT";

        long takerAccountId = 2001L;
        long makerAccountId = 3001L;

        long takerReceivedAtMillis = Instant.now().toEpochMilli();
        long takerCreatedAtMillis = takerReceivedAtMillis;

        long makerReceivedAtMillis = Instant.now().toEpochMilli();
        long makerCreatedAtMillis = makerReceivedAtMillis;

        Order taker = Order.createNew(
                10L,
                takerAccountId,
                "cli-taker",
                symbol,
                OrderSide.BUY,
                OrderType.LIMIT,
                TimeInForce.GTC,
                49_500_000_000L,
                100_000_000L,
                takerReceivedAtMillis,
                takerCreatedAtMillis
        );

        Order maker = Order.createNew(
                11L,
                makerAccountId,
                "cli-maker",
                symbol,
                OrderSide.SELL,
                OrderType.LIMIT,
                TimeInForce.GTC,
                49_500_000_000L,
                100_000_000L,
                makerReceivedAtMillis,
                makerCreatedAtMillis
        );

        long matchId1 = 1L;
        long matchId2 = 2L;
        long tradePrice = 49_500_000_000L;
        long tradeQty = 100_000_000L;
        long executedAtMillis = Instant.now().toEpochMilli();

        MatchFill fill1 = MatchFill.of(
                matchId1,
                symbol,
                tradePrice,
                tradeQty,
                executedAtMillis,
                taker,
                maker
        );
        MatchFill fill2 = MatchFill.of(
                matchId2,
                symbol,
                tradePrice,
                tradeQty,
                executedAtMillis,
                taker,
                maker
        );

        // when
        publisher.publishMatchFills(symbol, List.of(fill1, fill2));

        // then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaMessageProducer, times(4))
                .send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        List<String> topics = topicCaptor.getAllValues();
        List<String> keys = keyCaptor.getAllValues();
        List<String> payloads = payloadCaptor.getAllValues();

        // topic 모두 trades.executed-test 여야 함
        assertThat(topics).allMatch(t -> t.equals("trades.executed-test"));

        // key에는 takerAccountId와 makerAccountId가 최소 한 번씩은 포함되어야 한다
        assertThat(keys).contains(
                String.valueOf(takerAccountId),
                String.valueOf(makerAccountId)
        );

        // 각 payload마다 symbol과 matchId, execQuantity, execPrice가 올바른지 간단히 확인
        for (String payload : payloads) {
            JsonNode json = objectMapper.readTree(payload);
            assertThat(json.get("symbol").asText()).isEqualTo(symbol);
            assertThat(json.get("execQuantity").asText()).isEqualTo(String.valueOf(tradeQty));
            assertThat(json.get("execPrice").asText()).isEqualTo(String.valueOf(tradePrice));
        }
    }
}
