package org.tradinggate.backend.risk.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.risk.service.PositionService;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("risk")
public class RiskEventListener {

  private final PositionService positionService;

  @Async
  @EventListener
  public void handleTradeExecution(TradeExecutedEvent event) {
    log.info("Event received: {}", event);
    positionService.updatePosition(event);
  }
}
