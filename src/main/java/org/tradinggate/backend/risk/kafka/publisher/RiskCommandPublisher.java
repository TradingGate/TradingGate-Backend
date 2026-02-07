package org.tradinggate.backend.risk.kafka.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskCommandPublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Value("${tradinggate.topics.risk-commands:risk.commands}")
  private String riskCommandsTopic;

  // 계정 차단 명령 발행
  public void publishBlockAccount(Long accountId, String reason) {
    RiskCommand command = RiskCommand.builder()
        .commandType("BLOCK_ACCOUNT")
        .accountId(accountId)
        .reason(reason)
        .timestamp(LocalDateTime.now())
        .build();

    sendCommand(command);
  }

  // 계정 차단 해제 명령 발행
  public void publishUnblockAccount(Long accountId) {
    RiskCommand command = RiskCommand.builder()
        .commandType("UNBLOCK_ACCOUNT")
        .accountId(accountId)
        .timestamp(LocalDateTime.now())
        .build();

    sendCommand(command);
  }

  // Kafka로 명령 전송
  private void sendCommand(RiskCommand command) {
    try {
      String message = objectMapper.writeValueAsString(command);

      kafkaTemplate.send(
          riskCommandsTopic,
          command.getAccountId().toString(),
          message);

      log.info("Risk command sent: type={}, accountId={}",
          command.getCommandType(), command.getAccountId());

    } catch (JsonProcessingException e) {
      log.error("Failed to serialize risk command: {}", command, e);
      throw new RuntimeException("Failed to publish risk command", e);
    }
  }
}

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class RiskCommand {
  private String commandType; // BLOCK_ACCOUNT, UNBLOCK_ACCOUNT
  private Long accountId;
  private String reason;
  private LocalDateTime timestamp;
}
