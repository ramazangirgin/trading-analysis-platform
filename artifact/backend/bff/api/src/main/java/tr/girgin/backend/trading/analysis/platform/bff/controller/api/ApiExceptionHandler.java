package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ApiErrorDto;

@RestControllerAdvice
public class ApiExceptionHandler {

    static final String INVALID_REQUEST = "invalid_request";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorDto> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.status())
                .body(new ApiErrorDto(ex.errorCode(), ex.getMessage(), ex.params()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidBody(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .findFirst()
                .orElse(ex.getBindingResult().getObjectName());
        return badRequest("Invalid " + field, Map.of("field", field));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorDto> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return badRequest("Malformed request body", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDto> handleBadParameter(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid " + ex.getName(), Map.of("field", ex.getName()));
    }

    private static ResponseEntity<ApiErrorDto> badRequest(String message, Map<String, Object> params) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorDto(INVALID_REQUEST, message, params));
    }
}
