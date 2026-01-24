package org.tradinggate.backend.risk.domain.entity;

public enum RiskStatus {
  NORMAL,      // 정상
  WARNING,     // 마진콜 경고 (거래 가능하지만 위험)
  BLOCKED      // 거래 차단 (강제 청산 진행 중)
}