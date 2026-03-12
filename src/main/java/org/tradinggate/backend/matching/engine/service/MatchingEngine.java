package org.tradinggate.backend.matching.engine.service;

import org.tradinggate.backend.matching.engine.model.MatchResult;
import org.tradinggate.backend.matching.engine.model.OrderCommand;

public interface MatchingEngine {

    MatchResult handle(OrderCommand command, long currentTimeMillis);
}
