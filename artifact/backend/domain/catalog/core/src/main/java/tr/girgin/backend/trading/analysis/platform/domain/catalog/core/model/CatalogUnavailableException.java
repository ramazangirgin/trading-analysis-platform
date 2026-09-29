package tr.girgin.backend.trading.analysis.platform.domain.catalog.core.model;

/** The runner could not be asked (not installed, crashed, timed out). */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
