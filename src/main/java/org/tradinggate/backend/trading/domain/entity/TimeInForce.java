package org.tradinggate.backend.trading.domain.entity;

public enum TimeInForce {
  GTC,        //Good Till Canceled 주문이 체결되거나 수동으로 취소할 때까지 유효한 방식
  LOC,        //Immediate  or Cancel 주문을 즉시 체결 가능한 수량만 체결하고 나머지는즉시 취소
  FOK         //Fill or Kill 주문 전체를 즉시 체결하거나, 불가능하면 전체 취소
}
