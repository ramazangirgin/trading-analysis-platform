package tr.girgin.backend.trading.analysis.platform.domain.identity.core.exception;

public enum IdentityError {
    INVALID_USERNAME,
    UNKNOWN_PERMISSION,
    BAD_CREDENTIALS,
    WRONG_CURRENT_PASSWORD,
    PASSWORD_UNCHANGED,
    WEAK_PASSWORD,
    USER_NOT_FOUND,
    USERNAME_TAKEN,
    UNKNOWN_ROLE,
    CANNOT_DISABLE_SELF,
    CANNOT_REMOVE_OWN_ADMIN,
    LAST_ADMIN,
    CONCURRENT_UPDATE
}
