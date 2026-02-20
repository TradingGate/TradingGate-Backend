package org.tradinggate.backend.risk.domain.entity.risk;

public enum RiskStatus {
  NORMAL,      // 정상
  BLOCKED      // 거래 차단 (강제 청산 진행 중)
}