package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserRoleDto;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UserAccount;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.library.mapper.OptionalInstantToInstantMapperImpl;

class UserAccountToUserDtoMapperTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-02-01T10:00:00Z");

    private final UserAccountToUserDtoMapper userAccountToUserDtoMapper =
            new UserAccountToUserDtoMapperImpl(new OptionalInstantToInstantMapperImpl());

    @Test
    void mapsEveryFieldAndKeepsTheRolesInTheGivenOrder() {
        Instant lockedUntil = Instant.parse("2026-03-01T10:00:00Z");
        Instant lastLoginAt = Instant.parse("2026-02-15T10:00:00Z");

        UserDto dto = userAccountToUserDtoMapper.map(account(Optional.of(lockedUntil), Optional.of(lastLoginAt)));

        assertThat(dto.id()).isEqualTo("user-1");
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.roles())
                .containsExactly(new UserRoleDto("VIEWER", "VIEWER"), new UserRoleDto("ANALYST", "ANALYST"));
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.mustChangePassword()).isTrue();
        assertThat(dto.locked()).isTrue();
        assertThat(dto.lockedUntil()).isEqualTo(lockedUntil);
        assertThat(dto.lastLoginAt()).isEqualTo(lastLoginAt);
        assertThat(dto.createdAt()).isEqualTo(CREATED);
        assertThat(dto.updatedAt()).isEqualTo(UPDATED);
        assertThat(dto.version()).isEqualTo(4L);
    }

    @Test
    void mapsEmptyLockedUntilAndLastLoginAtToNull() {
        UserDto dto = userAccountToUserDtoMapper.map(account(Optional.empty(), Optional.empty()));

        assertThat(dto.lockedUntil()).isNull();
        assertThat(dto.lastLoginAt()).isNull();
    }

    private static UserAccount account(Optional<Instant> lockedUntil, Optional<Instant> lastLoginAt) {
        return new UserAccount(
                new UserId("user-1"),
                new Username("alice"),
                List.of(role(RoleId.VIEWER), role(RoleId.ANALYST)),
                true,
                true,
                true,
                lockedUntil,
                lastLoginAt,
                CREATED,
                UPDATED,
                4L);
    }

    private static Role role(RoleId id) {
        return new Role(id, id.value(), true, "desc", Set.of(Permission.USER_READ), 1L);
    }
}
