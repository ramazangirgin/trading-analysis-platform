package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreateUserRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.CreatedUserDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.OneTimePasswordDto;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ResetPasswordRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UpdateUserRequest;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.UserDto;

public interface UsersApiDelegate {

    List<UserDto> listUsers();

    UserDto getUser(String id);

    ResponseEntity<CreatedUserDto> createUser(CreateUserRequest request);

    UserDto updateUser(String id, UpdateUserRequest request, HttpServletRequest http);

    OneTimePasswordDto resetPassword(String id, ResetPasswordRequest request);

    ResponseEntity<Void> unlock(String id);
}
