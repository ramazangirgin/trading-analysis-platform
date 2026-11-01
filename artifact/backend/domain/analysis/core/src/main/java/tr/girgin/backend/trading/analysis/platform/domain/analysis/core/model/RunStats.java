package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

import java.math.BigDecimal;
import java.time.Duration;

/** LLM usage and wall time of a run. {@code costUsd} is null until pricing is known. */
public record RunStats(
        long llmCalls, long toolCalls, long tokensIn, long tokensOut, BigDecimal costUsd, Duration elapsed) {}
