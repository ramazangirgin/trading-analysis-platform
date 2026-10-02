package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

/** Versions of the installed runner, its event protocol, and TradingAgents. */
public record EngineVersion(String runnerVersion, int protocolVersion, String upstreamVersion) {}
