package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.outbound.runner;

/** Runner-specific reference to a started run (process id, container id). */
public record RunHandle(String ref) {}
