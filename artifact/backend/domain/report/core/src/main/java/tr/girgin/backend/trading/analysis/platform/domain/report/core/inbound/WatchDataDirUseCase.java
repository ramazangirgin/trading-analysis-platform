package tr.girgin.backend.trading.analysis.platform.domain.report.core.inbound;

public interface WatchDataDirUseCase {

    /**
     * Calls {@code onSettled}, from a background thread, once the data dir has been quiet for a
     * moment after a change (or has kept changing for a while), until the handle is closed.
     */
    AutoCloseable watch(Runnable onSettled);
}
