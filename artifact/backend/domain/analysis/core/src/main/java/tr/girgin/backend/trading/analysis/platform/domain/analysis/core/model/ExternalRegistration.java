package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model;

/** What registering an external run did. */
public record ExternalRegistration(Analysis analysis, Outcome outcome) {

    public enum Outcome {
        /** A new EXTERNAL record was created. */
        CREATED,
        /** The existing EXTERNAL record was brought up to date with the files. */
        UPDATED,
        /** Nothing changed: the record is current, or the platform itself ran this ticker and date. */
        UNCHANGED
    }
}
