package org.tradinggate.backend.recon.service.port;

import org.tradinggate.backend.recon.domain.ReconBatch;
import org.tradinggate.backend.recon.dto.ReconRow;

import java.util.List;

public interface ReconInputsPort {
    List<ReconRow> loadSnapshot(ReconBatch reconBatch);
    List<ReconRow> loadTruth(ReconBatch reconBatch);
}
