package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound;

@FunctionalInterface
public interface EventSubscription {

    void cancel();
}
