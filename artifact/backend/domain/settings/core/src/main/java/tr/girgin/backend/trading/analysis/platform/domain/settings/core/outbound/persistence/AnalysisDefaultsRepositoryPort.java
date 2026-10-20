package tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;

/** Stores the platform's analysis defaults, a single record. */
public interface AnalysisDefaultsRepositoryPort {

    /** The stored defaults, empty while nothing is saved. */
    Optional<AnalysisDefaults> find();

    /**
     * Inserts or updates the defaults and returns them as stored, with their new {@link AnalysisDefaults#version()}.
     * A {@code null} version inserts the row. A version that differs from the stored one fails with Spring's
     * {@code OptimisticLockingFailureException}; a {@code null} version while the row exists, or a concurrent first
     * insert, fails with its {@code DataIntegrityViolationException}. The row stays unchanged.
     */
    AnalysisDefaults save(AnalysisDefaults defaults);
}
