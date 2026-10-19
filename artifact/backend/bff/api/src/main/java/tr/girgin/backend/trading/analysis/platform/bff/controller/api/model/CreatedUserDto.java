package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

/**
 * A user that was just created. {@code oneTimePassword} is set only when the platform generated it, so the admin
 * sees it once; it is null when the admin entered the password.
 */
public record CreatedUserDto(UserDto user, String oneTimePassword) {}
