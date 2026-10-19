package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import tools.jackson.databind.json.JsonMapper;

class ExpiredSessionStrategyTest {

    private final ExpiredSessionStrategy strategy =
            new ExpiredSessionStrategy(new ApiErrorWriter(JsonMapper.builder().build()));

    @Test
    void anApiRequestGets401Unauthorized() throws Exception {
        MockHttpServletResponse response = expire(request("/api/analyses", null));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"unauthorized\"").contains("Session ended");
    }

    @Test
    void aRequestForTheUiIsSentToTheSameUrlAgain() throws Exception {
        MockHttpServletResponse response = expire(request("/assets/RunsPage-abc.js", null));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/assets/RunsPage-abc.js");
    }

    @Test
    void theRedirectKeepsTheQuery() throws Exception {
        MockHttpServletResponse response = expire(request("/analyses", "status=RUNNING"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/analyses?status=RUNNING");
    }

    private static MockHttpServletRequest request(String path, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setQueryString(query);
        return request;
    }

    private MockHttpServletResponse expire(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SessionInformation session = new SessionInformation("user", "session", new Date());
        session.expireNow();
        strategy.onExpiredSessionDetected(new SessionInformationExpiredEvent(session, request, response));
        return response;
    }
}
