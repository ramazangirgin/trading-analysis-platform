package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.UsersApiDelegate;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.mapper.error.IdentityExceptionToApiExceptionMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception.IdentityException;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound.UnlockUserUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;

@Service
class UsersApiDelegateImpl implements UsersApiDelegate {

    private final UnlockUserUseCase unlockUser;
    private final IdentityExceptionToApiExceptionMapper errorMapper;

    UsersApiDelegateImpl(UnlockUserUseCase unlockUser, IdentityExceptionToApiExceptionMapper errorMapper) {
        this.unlockUser = unlockUser;
        this.errorMapper = errorMapper;
    }

    @Override
    public ResponseEntity<Void> unlock(String id) {
        try {
            unlockUser.unlock(new UserId(id));
        } catch (IdentityException e) {
            throw errorMapper.map(e);
        }
        return ResponseEntity.noContent().build();
    }
}
