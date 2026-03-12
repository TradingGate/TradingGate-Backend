package org.tradinggate.backend.trading.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.trading.api.dto.response.TradeResponse;
import org.tradinggate.backend.trading.domain.entity.Trade;
import org.tradinggate.backend.trading.domain.repository.TradeRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Log4j2
@Profile("api")
public class TradeQueryService {

  private final TradeRepository tradeRepository;

  public Page<TradeResponse> getMyTrades(Long userId, Pageable pageable) {
    Page<Trade> trades = tradeRepository.findByUserId(userId, pageable);
    return trades.map(TradeResponse::from);
  }

  public List<TradeResponse> getTradesByOrder(Long userId, Long orderId) {
    List<Trade> trades = tradeRepository.findByUserIdAndOrderId(userId, orderId);
    return trades.stream()
        .map(TradeResponse::from)
        .collect(Collectors.toList());
  }
}
