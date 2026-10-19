package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/** Looks users up for other domains' data: who the first admin is and what users are called. */
public interface UserDirectoryUseCase {

    /** The enabled ADMIN created first; empty when there is none yet. */
    Optional<UserId> firstAdmin();

    /** The usernames of the given users; an ID without a user is left out. */
    Map<UserId, Username> usernames(Collection<UserId> ids);
}
