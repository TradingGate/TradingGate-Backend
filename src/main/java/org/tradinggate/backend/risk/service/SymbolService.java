package org.tradinggate.backend.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolService {

  // TODO: 실제로는 DB symbol 테이블에서 조회
  // 임시로 메모리 캐시 사용
  private final Map<String, Long> symbolCache = new ConcurrentHashMap<>();

  public Long getSymbolId(String symbol) {
    // TODO: DB 조회 로직으로 변경
    // SELECT id FROM symbol WHERE symbol = ?
    return symbolCache.computeIfAbsent(symbol, s -> {
      // 임시: symbol 해시코드를 ID로 사용
      long id = Math.abs(symbol.hashCode()) % 1000000L;
      log.debug("Symbol mapped: {} -> {}", symbol, id);
      return id;
    });
  }
}
