package org.tradinggate.backend.settlementIntegrity.recon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconRow;
import org.tradinggate.backend.settlementIntegrity.recon.service.port.ReconInputsPort;
import org.tradinggate.backend.risk.domain.entity.balance.AccountBalance;
import org.tradinggate.backend.risk.repository.balance.AccountBalanceRepository;
import org.tradinggate.backend.risk.repository.ledger.LedgerEntryRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Primary
@Component
@RequiredArgsConstructor
@Profile("recon")
public class DbReconInputsPort implements ReconInputsPort {

    private final AccountBalanceRepository accountBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    public List<ReconRow> loadSnapshot(ReconBatch reconBatch) {
        // Snapshot = 사용자/운영에서 조회하는 현재 projection(account_balance)
        List<AccountBalance> balances = accountBalanceRepository.findAll();

        return balances.stream()
                .map(b -> new ReconRow(
                        b.getAccountId(),
                        b.getAsset() == null ? null : b.getAsset().toUpperCase(),
                        null,
                        b.getTotal(),
                        null,
                        null,
                        null,
                        null))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReconRow> loadTruth(ReconBatch reconBatch) {
        // Truth = 영구 저장된 ledger_entry의 계정/자산별 합계
        List<Object[]> holdings = ledgerEntryRepository.sumAllHoldings();

        return holdings.stream()
                .map(row -> new ReconRow(
                        (Long) row[0],
                        row[1] == null ? null : ((String) row[1]).toUpperCase(),
                        null,
                        (BigDecimal) row[2],
                        null,
                        null,
                        null,
                        null))
                .collect(Collectors.toList());
    }
}
