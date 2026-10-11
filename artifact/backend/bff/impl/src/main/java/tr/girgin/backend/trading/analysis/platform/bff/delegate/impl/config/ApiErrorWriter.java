package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ApiErrorDto;

/** Writes an {@link ApiErrorDto} for the security filters, which answer before a controller advice can. */
final class ApiErrorWriter {

    private final JsonMapper jsonMapper;

    ApiErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    void write(HttpServletResponse response, HttpStatus status, String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(new ApiErrorDto(errorCode, message, Map.of())));
    }
}
