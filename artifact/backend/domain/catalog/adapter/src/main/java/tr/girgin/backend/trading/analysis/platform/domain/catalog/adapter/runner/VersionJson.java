package tr.girgin.backend.trading.analysis.platform.domain.catalog.adapter.runner;

/** Output of {@code ta-runner version}. */
record VersionJson(String runnerVersion, Integer protocolVersion, String upstreamVersion) {
}
