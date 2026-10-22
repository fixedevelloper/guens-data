package com.guens.fieldcollect.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guens.fieldcollect.common.exception.ErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginRateLimitFilter filter =
            new LoginRateLimitFilter(3, new ErrorResponseWriter(new ObjectMapper()), now::get);

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void blocksAfterLimitPerIpThenRecoversWithNextWindow() throws Exception {
        for (int i = 0; i < 3; i++) assertEquals(200, call("POST", "/api/v1/auth/login", "1.1.1.1").getStatus());
        MockHttpServletResponse blocked = call("POST", "/api/v1/auth/login", "1.1.1.1");
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("TOO_MANY_REQUESTS"));

        // une autre IP n'est pas affectée
        assertEquals(200, call("POST", "/api/v1/auth/login", "2.2.2.2").getStatus());

        now.addAndGet(60_001);
        assertEquals(200, call("POST", "/api/v1/auth/login", "1.1.1.1").getStatus());
    }

    @Test
    void onlyLoginAndRefreshPostsAreLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, call("GET", "/api/v1/auth/me", "3.3.3.3").getStatus());
            assertEquals(200, call("POST", "/api/v1/mobile/persons", "3.3.3.3").getStatus());
        }
    }
}
