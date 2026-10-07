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
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.RoleRowToRoleMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.RoleToRoleRowMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.StringToPermissionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.RolePermissionRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.RoleRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;

@Component
class JdbcRoleRepositoryAdapter implements RoleRepositoryPort {

    private final JdbcClient jdbc;
    private final RoleToRoleRowMapper toRow;
    private final RoleRowToRoleMapper toRole;
    private final StringToPermissionMapper toPermission;

    JdbcRoleRepositoryAdapter(
            JdbcClient jdbc,
            RoleToRoleRowMapper toRow,
            RoleRowToRoleMapper toRole,
            StringToPermissionMapper toPermission) {
        this.jdbc = jdbc;
        this.toRow = toRow;
        this.toRole = toRole;
        this.toPermission = toPermission;
    }

    @Override
    public Optional<Role> findById(RoleId id) {
        return withPermissions(jdbc.sql("SELECT * FROM roles WHERE id = :id")
                        .param("id", id.value())
                        .query(RoleRow.class)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Role> findByName(String name) {
        return withPermissions(jdbc.sql("SELECT * FROM roles WHERE name = :name")
                        .param("name", name)
                        .query(RoleRow.class)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public List<Role> findAll() {
        return withPermissions(jdbc.sql("SELECT * FROM roles ORDER BY name")
                .query(RoleRow.class)
                .list());
    }

    @Override
    @Transactional
    public void save(Role role) {
        jdbc.sql("""
                INSERT INTO roles (id, name, built_in, description) VALUES (:id, :name, :builtIn, :description)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, built_in = excluded.built_in,
                    description = excluded.description
                """).paramSource(toRow.map(role)).update();
        jdbc.sql("DELETE FROM role_permissions WHERE role_id = :roleId")
                .param("roleId", role.id().value())
                .update();
        for (Permission permission : role.permissions()) {
            jdbc.sql("INSERT INTO role_permissions (role_id, permission) VALUES (:roleId, :permission)")
                    .param("roleId", role.id().value())
                    .param("permission", permission.key())
                    .update();
        }
    }

    /** Maps the rows and reads all their permissions in one query. */
    private List<Role> withPermissions(List<RoleRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(RoleRow::id).toList();
        Map<String, Set<Permission>> permissionsByRole = permissionsByRole(ids);
        return rows.stream()
                .map(row -> toRole.map(row).withPermissions(permissionsByRole.getOrDefault(row.id(), Set.of())))
                .toList();
    }

    private Map<String, Set<Permission>> permissionsByRole(Collection<String> roleIds) {
        Map<String, Set<Permission>> result = new HashMap<>();
        List<RolePermissionRow> grants = jdbc.sql(
                        "SELECT role_id, permission FROM role_permissions WHERE role_id IN (:roleIds)")
                .param("roleIds", roleIds)
                .query(RolePermissionRow.class)
                .list();
        for (RolePermissionRow grant : grants) {
            result.computeIfAbsent(grant.roleId(), _ -> new HashSet<>()).add(toPermission.map(grant.permission()));
        }
        return result;
    }
}
