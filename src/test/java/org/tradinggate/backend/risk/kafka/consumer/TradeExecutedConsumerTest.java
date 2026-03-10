package org.tradinggate.backend.risk.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.tradinggate.backend.risk.kafka.dto.TradeExecutedEvent;
import org.tradinggate.backend.risk.service.orchestrator.TradeProcessingOrchestrator;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutedConsumerTest {

    @Mock
    private TradeProcessingOrchestrator orchestrator;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    @DisplayName("worker envelope 형식의 trades.executed를 파싱해 orchestrator에 전달한다")
    void consume_parsesWorkerEnvelope_andAcknowledges() {
        TradeExecutedConsumer consumer = new TradeExecutedConsumer(
                orchestrator,
                new ObjectMapper().findAndRegisterModules()
        );
        when(orchestrator.processTrade(org.mockito.ArgumentMatchers.any(TradeExecutedEvent.class))).thenReturn(true);

        String message = """
                {
                  "side":"MAKER",
                  "sourcePartition":2,
                  "symbol":"BTCUSDT",
                  "sourceTopic":"orders.in",
                  "body":{
                    "eventId":"tradeEventID-1",
                    "symbol":"BTCUSDT",
                    "execQuantity":"1",
                    "side":"BUY",
                    "execPrice":"50000",
                    "liquidityFlag":"MAKER",
                    "execTime":"2026-03-10T07:11:03.570Z",
                    "takerOrderId":10,
                    "makerOrderId":9,
                    "userId":4001,
                    "tradeId":3,
                    "matchId":3
                  },
                  "sourceOffset":9
                }
                """;

        consumer.consume(message, 8, 0L, acknowledgment);

        ArgumentCaptor<TradeExecutedEvent> captor = ArgumentCaptor.forClass(TradeExecutedEvent.class);
        verify(orchestrator).processTrade(captor.capture());
        verify(acknowledgment).acknowledge();

        TradeExecutedEvent event = captor.getValue();
        assertThat(event.getTradeId()).isEqualTo("3");
        assertThat(event.getAccountId()).isEqualTo(4001L);
        assertThat(event.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(event.getSide()).isEqualTo("BUY");
        assertThat(event.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(event.getPrice()).isEqualByComparingTo("50000");
        assertThat(event.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
