package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.List;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

/** Lists the roles that can be given to users. */
public interface ListRolesUseCase {

    /** All roles, sorted by name. */
    List<Role> list();
}
