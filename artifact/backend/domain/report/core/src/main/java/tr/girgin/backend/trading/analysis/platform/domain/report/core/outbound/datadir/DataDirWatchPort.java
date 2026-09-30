package tr.girgin.backend.trading.analysis.platform.domain.report.core.outbound.datadir;

/** Notices changes to the data dir files the import reads. Never writes to the data dir. */
public interface DataDirWatchPort {

    /**
     * Calls {@code onChange}, from a background thread, for every change noticed until the
     * returned handle is closed. Bursts are passed on as they come; nothing is coalesced here.
     */
    AutoCloseable watch(Runnable onChange);
}
