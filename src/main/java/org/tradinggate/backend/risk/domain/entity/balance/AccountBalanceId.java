package org.tradinggate.backend.risk.domain.entity.balance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceId implements Serializable {
  private Long accountId;
  private String asset;
}
