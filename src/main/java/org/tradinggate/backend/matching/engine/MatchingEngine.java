package org.tradinggate.backend.matching.engine;

import org.tradinggate.backend.matching.domain.MatchResult;
import org.tradinggate.backend.matching.domain.OrderCommand;

public interface MatchingEngine {

    MatchResult handle(OrderCommand command, long currentTimeMillis);
}
