package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row;

public record UserRow(
        String id,
        String username,
        String passwordHash,
        boolean enabled,
        boolean mustChangePassword,
        int failedLoginCount,
        String lockedUntil,
        String createdAt,
        String updatedAt) {}
