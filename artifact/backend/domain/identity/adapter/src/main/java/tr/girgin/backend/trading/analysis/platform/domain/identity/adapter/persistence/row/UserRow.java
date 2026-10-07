package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row;

import java.time.OffsetDateTime;

public record UserRow(
        String id,
        String username,
        String passwordHash,
        boolean enabled,
        boolean mustChangePassword,
        int failedLoginCount,
        OffsetDateTime lockedUntil,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
