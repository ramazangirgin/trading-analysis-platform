package tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence;

import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.AnalysisDefaults;

/** Stores the platform's analysis defaults, a single record. */
public interface AnalysisDefaultsRepositoryPort {

    /** The stored defaults, empty while nothing is saved. */
    Optional<AnalysisDefaults> find();

    /**
     * Inserts or updates the defaults and returns them as stored, with their new {@link AnalysisDefaults#version()}.
     * A {@code null} version inserts the row; a version that differs from the stored one, and a {@code null} version
     * while the row exists, fail with Spring's {@code OptimisticLockingFailureException} and leave the row unchanged.
     */
    AnalysisDefaults save(AnalysisDefaults defaults);
}
