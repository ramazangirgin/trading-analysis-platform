package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

/** A live subscription to a run's events, ended by {@link #cancel()}. */
@FunctionalInterface
public interface EventSubscription {

    void cancel();
}
