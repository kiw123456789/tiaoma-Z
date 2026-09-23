package com.tiaoma.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** เทสต์ว่าการจำกัดจำนวนครั้งยิง API ทำงานจริงและไม่ไปกระทบ endpoint อื่น */
class RateLimitFilterTest {

    /** login: อนุญาต 3 ครั้งใน 15 นาที, forgot/register: 2 ครั้งใน 60 นาที */
    private RateLimitFilter filter() {
        return new RateLimitFilter(3, 15, 2, 60);
    }

    private MockHttpServletRequest post(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("ยิง login เกินโควตาแล้วต้องได้ 429 พร้อม header Retry-After")
    void login_overLimit_returns429() throws Exception {
        RateLimitFilter f = filter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(post("/api/auth/login", "1.1.1.1"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        f.doFilter(post("/api/auth/login", "1.1.1.1"), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("บ่อยเกินไป");
        // ครั้งที่โดนบล็อกต้องไม่ถูกส่งต่อไปยัง controller
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    @DisplayName("คนละ IP กัน นับแยกกัน ไม่บล็อกข้ามกัน")
    void differentIps_countedSeparately() throws Exception {
        RateLimitFilter f = filter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            f.doFilter(post("/api/auth/login", "2.2.2.2"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse other = new MockHttpServletResponse();
        f.doFilter(post("/api/auth/login", "3.3.3.3"), other, chain);
        assertThat(other.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("forgot-password มีโควตาแยกจาก login")
    void forgotPassword_hasOwnQuota() throws Exception {
        RateLimitFilter f = filter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(post("/api/auth/forgot-password", "4.4.4.4"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        f.doFilter(post("/api/auth/forgot-password", "4.4.4.4"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        // login ของ IP เดียวกันยังใช้ได้ เพราะนับแยก key
        MockHttpServletResponse login = new MockHttpServletResponse();
        f.doFilter(post("/api/auth/login", "4.4.4.4"), login, chain);
        assertThat(login.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("endpoint อื่นและ GET ไม่ถูกจำกัด")
    void otherEndpoints_notLimited() throws Exception {
        RateLimitFilter f = filter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(post("/api/places", "5.5.5.5"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");
        get.setRequestURI("/api/auth/login");
        get.setRemoteAddr("5.5.5.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(get, res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("เคารพ X-Forwarded-For เมื่อรันหลัง reverse proxy")
    void respectsXForwardedFor() throws Exception {
        RateLimitFilter f = filter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = post("/api/auth/login", "10.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
            f.doFilter(req, new MockHttpServletResponse(), chain);
        }

        // IP จริงเดิม (จาก X-Forwarded-For) ต้องโดนบล็อก
        MockHttpServletRequest same = post("/api/auth/login", "10.0.0.1");
        same.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        f.doFilter(same, blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);

        // ส่วน IP อื่นที่ผ่าน proxy เดียวกันยังใช้ได้
        MockHttpServletRequest another = post("/api/auth/login", "10.0.0.1");
        another.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        f.doFilter(another, ok, chain);
        assertThat(ok.getStatus()).isEqualTo(200);
    }
}
