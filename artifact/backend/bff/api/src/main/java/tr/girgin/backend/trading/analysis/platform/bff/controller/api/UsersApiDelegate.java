package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import org.springframework.http.ResponseEntity;

public interface UsersApiDelegate {

    ResponseEntity<Void> unlock(String id);
}
