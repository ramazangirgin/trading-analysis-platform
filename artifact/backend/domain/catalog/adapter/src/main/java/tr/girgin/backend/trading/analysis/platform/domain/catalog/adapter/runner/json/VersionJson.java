package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner.json;

/** Output of {@code ta-runner version}. */
public record VersionJson(String runnerVersion, Integer protocolVersion, String upstreamVersion) {}
