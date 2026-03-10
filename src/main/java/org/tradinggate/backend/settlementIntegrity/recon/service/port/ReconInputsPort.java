package org.tradinggate.backend.settlementIntegrity.recon.service.port;

import org.tradinggate.backend.settlementIntegrity.recon.domain.ReconBatch;
import org.tradinggate.backend.settlementIntegrity.recon.dto.ReconRow;

import java.util.List;

public interface ReconInputsPort {
    List<ReconRow> loadSnapshot(ReconBatch reconBatch);
    List<ReconRow> loadTruth(ReconBatch reconBatch);
}
