package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradinggate.backend.risk.domain.entity.AccountBalance;
import org.tradinggate.backend.risk.event.TradeExecutedEvent;
import org.tradinggate.backend.risk.repository.AccountBalanceRepository;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountBalanceService {

  private final AccountBalanceRepository accountBalanceRepository;

  /**
   * B-1: trades.executed 기반으로 잔고 업데이트
   * BUY: quoteasset(USDT) 감소, baseasset(BTC) 증가
   * SELL: baseasset(BTC) 감소, quoteasset(USDT) 증가
   */
  @Transactional
  public void updateBalance(TradeExecutedEvent event) {
    Long accountId = event.getAccountId();
    String symbol = event.getSymbol(); // "BTCUSDT"
    String side = event.getSide(); // "BUY" or "SELL"
    BigDecimal execQuantity = event.getExecQuantity();
    BigDecimal execPrice = event.getExecPrice();
    BigDecimal feeAmount = event.getFeeAmount();
    String feeCurrency = event.getFeeCurrency();

    // symbol 파싱: BTCUSDT -> BTC, USDT
    String baseAsset = parseBaseAsset(symbol);   // BTC
    String quoteAsset = parseQuoteAsset(symbol); // USDT

    BigDecimal execValue = execQuantity.multiply(execPrice);

    log.debug("Updating balance: accountId={}, side={}, symbol={}, execValue={}",
        accountId, side, symbol, execValue);

    if ("BUY".equals(side)) {
      // BUY: quoteAsset(USDT) 감소, baseAsset(BTC) 증가
      decreaseBalance(accountId, quoteAsset, execValue.add(feeAmount));
      increaseBalance(accountId, baseAsset, execQuantity);

    } else if ("SELL".equals(side)) {
      // SELL: baseAsset(BTC) 감소, quoteAsset(USDT) 증가
      decreaseBalance(accountId, baseAsset, execQuantity);
      increaseBalance(accountId, quoteAsset, execValue.subtract(feeAmount));
    }

    log.info("Balance updated: accountId={}, side={}, baseAsset={}, quoteAsset={}",
        accountId, side, baseAsset, quoteAsset);
  }

  private void increaseBalance(Long accountId, String asset, BigDecimal amount) {
    AccountBalance balance = accountBalanceRepository
        .findByAccountIdAndAsset(accountId, asset)
        .orElseGet(() -> AccountBalance.create(accountId, asset));

    balance.addAvailableBalance(amount);
    balance.addTotalBalance(amount);

    accountBalanceRepository.save(balance);
    log.debug("Balance increased: accountId={}, asset={}, amount={}", accountId, asset, amount);
  }

  private void decreaseBalance(Long accountId, String asset, BigDecimal amount) {
    AccountBalance balance = accountBalanceRepository
        .findByAccountIdAndAsset(accountId, asset)
        .orElseThrow(() -> new IllegalStateException(
            String.format("Insufficient balance: accountId=%d, asset=%s", accountId, asset)));

    if (balance.getAvailableBalance().compareTo(amount) < 0) {
      throw new IllegalStateException(
          String.format("Insufficient available balance: accountId=%d, asset=%s, required=%s, available=%s",
              accountId, asset, amount, balance.getAvailableBalance()));
    }

    balance.subtractAvailableBalance(amount);
    balance.subtractTotalBalance(amount);

    accountBalanceRepository.save(balance);
    log.debug("Balance decreased: accountId={}, asset={}, amount={}", accountId, asset, amount);
  }

  /**
   * symbol에서 base asset 추출
   * 예: BTCUSDT -> BTC
   */
  private String parseBaseAsset(String symbol) {
    // 간단한 파싱 (실제로는 symbol 테이블 조회 필요)
    if (symbol.endsWith("USDT")) {
      return symbol.substring(0, symbol.length() - 4);
    } else if (symbol.endsWith("USD")) {
      return symbol.substring(0, symbol.length() - 3);
    }
    // TODO: 실제로는 SymbolService에서 정확히 파싱
    return symbol.substring(0, 3);
  }

  /**
   * symbol에서 quote asset 추출
   * 예: BTCUSDT -> USDT
   */
  private String parseQuoteAsset(String symbol) {
    // 간단한 파싱 (실제로는 symbol 테이블 조회 필요)
    if (symbol.endsWith("USDT")) {
      return "USDT";
    } else if (symbol.endsWith("USD")) {
      return "USD";
    }
    return "USDT"; // 기본값
  }
}
