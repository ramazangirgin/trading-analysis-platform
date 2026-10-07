package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.StringToRoleIdMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserRowToUserMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserToUserRowMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.UserRoleRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.UserRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Component
class JdbcUserRepositoryAdapter implements UserRepositoryPort {

    private final JdbcClient jdbc;
    private final UserToUserRowMapper toRow;
    private final UserRowToUserMapper toUser;
    private final StringToRoleIdMapper toRoleId;

    JdbcUserRepositoryAdapter(
            JdbcClient jdbc, UserToUserRowMapper toRow, UserRowToUserMapper toUser, StringToRoleIdMapper toRoleId) {
        this.jdbc = jdbc;
        this.toRow = toRow;
        this.toUser = toUser;
        this.toRoleId = toRoleId;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return withRoles(jdbc.sql("SELECT * FROM users WHERE id = :id")
                        .param("id", id.value())
                        .query(UserRow.class)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return withRoles(jdbc.sql("SELECT * FROM users WHERE lower(username) = lower(:username)")
                        .param("username", username.value())
                        .query(UserRow.class)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return withRoles(jdbc.sql("SELECT * FROM users ORDER BY lower(username)")
                .query(UserRow.class)
                .list());
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM users").query(Long.class).single();
    }

    @Override
    @Transactional
    public void save(User user) {
        jdbc.sql("""
                INSERT INTO users (id, username, password_hash, enabled, must_change_password, failed_login_count,
                    locked_until, created_at, updated_at)
                VALUES (:id, :username, :passwordHash, :enabled, :mustChangePassword, :failedLoginCount,
                    :lockedUntil, :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET username = excluded.username, password_hash = excluded.password_hash,
                    enabled = excluded.enabled, must_change_password = excluded.must_change_password,
                    failed_login_count = excluded.failed_login_count, locked_until = excluded.locked_until,
                    created_at = excluded.created_at, updated_at = excluded.updated_at
                """).paramSource(toRow.map(user)).update();
        jdbc.sql("DELETE FROM user_roles WHERE user_id = :userId")
                .param("userId", user.id().value())
                .update();
        for (RoleId roleId : user.roleIds()) {
            jdbc.sql("INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId)")
                    .param("userId", user.id().value())
                    .param("roleId", roleId.value())
                    .update();
        }
    }

    /** Maps the rows and reads all their role assignments in one query. */
    private List<User> withRoles(List<UserRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(UserRow::id).toList();
        Map<String, Set<RoleId>> rolesByUser = roleIdsByUser(ids);
        return rows.stream()
                .map(row -> toUser.map(row).withRoleIds(rolesByUser.getOrDefault(row.id(), Set.of())))
                .toList();
    }

    private Map<String, Set<RoleId>> roleIdsByUser(Collection<String> userIds) {
        Map<String, Set<RoleId>> result = new HashMap<>();
        List<UserRoleRow> assignments = jdbc.sql("SELECT user_id, role_id FROM user_roles WHERE user_id IN (:userIds)")
                .param("userIds", userIds)
                .query(UserRoleRow.class)
                .list();
        for (UserRoleRow assignment : assignments) {
            result.computeIfAbsent(assignment.userId(), _ -> new HashSet<>()).add(toRoleId.map(assignment.roleId()));
        }
        return result;
    }
}
