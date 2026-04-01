package com.sarvashikshaai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarvashikshaai.model.dto.MenuItemDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "auth.bootstrap.enabled=true",
                "jwt.secret=test-jwt-secret-change-me-1234567890",
                "jwt.issuer=test-issuer",
                "jwt.expiration-seconds=3600",
                "jwt.cookie-name=SVAI_AUTH_TOKEN"
        }
)
@AutoConfigureMockMvc
class AuthLoginMenuMockMvcTest {

    private static final String COOKIE_NAME = "SVAI_AUTH_TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anonymousMenu_ReturnsEmptyJson() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void teacherLogin_ReturnsMenuForTeacherOnly() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher\",\"password\":\"teacher123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andReturn();

        Cookie jwtCookie = cookieFromResponse(loginResult.getResponse());
        assertThat(jwtCookie).isNotNull();
        assertThat(jwtCookie.getName()).isEqualTo(COOKIE_NAME);
        assertThat(jwtCookie.getValue()).isNotBlank();

        MvcResult menuResult = mockMvc.perform(get("/api/menu").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andReturn();

        String body = menuResult.getResponse().getContentAsString();
        assertThat(body).contains("\"href\":\"/teacher/dashboard\"");
        assertThat(body).contains("\"href\":\"/teacher/setup\"");
        assertThat(body).doesNotContain("\"href\":\"/admin/analytics\"");
    }

    @Test
    void adminLogin_ReturnsAdminMenu() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        Cookie jwtCookie = cookieFromResponse(loginResult.getResponse());
        assertThat(jwtCookie).isNotNull();

        MvcResult menuResult = mockMvc.perform(get("/api/menu").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andReturn();

        String body = menuResult.getResponse().getContentAsString();
        assertThat(body).contains("\"href\":\"/admin/analytics\"");
        assertThat(body).contains("\"href\":\"/teacher/dashboard\"");
    }

    @Test
    void teacherCannotAccessAdminPages() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher\",\"password\":\"teacher123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andReturn();

        Cookie jwtCookie = cookieFromResponse(loginResult.getResponse());
        assertThat(jwtCookie).isNotNull();

        mockMvc.perform(get("/admin").cookie(jwtCookie))
                .andExpect(status().isForbidden());
    }

    private Cookie cookieFromResponse(MockHttpServletResponse response) {
        Cookie[] cookies = response.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> COOKIE_NAME.equals(c.getName()))
                    .findFirst()
                    .orElse(null);
        }

        String setCookie = response.getHeader("Set-Cookie");
        if (setCookie == null) return null;

        // Basic parse: SVAI_AUTH_TOKEN=VALUE;
        int idx = setCookie.indexOf(COOKIE_NAME + "=");
        if (idx < 0) return null;
        int start = idx + (COOKIE_NAME + "=").length();
        int end = setCookie.indexOf(';', start);
        if (end < 0) end = setCookie.length();
        String value = setCookie.substring(start, end);

        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        return cookie;
    }
}

