package tr.girgin.backend.trading.analysis.platform.domain.identity.core.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.ListRolesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.RoleRepositoryPort;

@Service
class RoleQueryService implements ListRolesUseCase {

    private final RoleRepositoryPort roles;

    RoleQueryService(RoleRepositoryPort roles) {
        this.roles = roles;
    }

    @Override
    public List<Role> list() {
        return roles.findAll().stream().sorted(Comparator.comparing(Role::name)).toList();
    }
}
