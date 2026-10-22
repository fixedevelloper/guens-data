package com.guens.fieldcollect.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guens.fieldcollect.user.Role;
import com.guens.fieldcollect.user.User;
import com.guens.fieldcollect.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthDeviceBindingTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginAttemptRepository attempts;

    @BeforeEach
    void setUp() {
        refreshTokens.deleteAll();
        attempts.deleteAll();
        users.findAll().stream().filter(u -> u.getRole() == Role.AGENT).forEach(users::delete);
        users.flush();
        agent("agent1");
        agent("agent2");
    }

    private void agent(String username) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode("secret"));
        u.setFullName(username);
        u.setRole(Role.AGENT);
        users.save(u);
    }

    private ResultActions login(String user, String pwd, String device) throws Exception {
        String deviceJson = device == null ? "null" : "\"" + device + "\"";
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\",\"deviceId\":%s}".formatted(user, pwd, deviceJson)));
    }

    @Test
    void firstLoginBindsDeviceAndSameDeviceCanLoginAgain() throws Exception {
        login("agent1", "secret", "DEV-A").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        login("agent1", "secret", "DEV-A").andExpect(status().isOk());
    }

    @Test
    void otherDeviceIsRejected() throws Exception {
        login("agent1", "secret", "DEV-A").andExpect(status().isOk());
        login("agent1", "secret", "DEV-B").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_MISMATCH"));
    }

    @Test
    void deviceAlreadyBoundToAnotherUserIsRejected() throws Exception {
        login("agent1", "secret", "DEV-A").andExpect(status().isOk());
        login("agent2", "secret", "DEV-A").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_ALREADY_USED"));
    }

    @Test
    void agentWithoutDeviceIdIsRejected() throws Exception {
        login("agent1", "secret", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));
    }

    @Test
    void badPasswordIsUnauthorized() throws Exception {
        login("agent1", "nope", "DEV-A").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIsUnusableFromAnotherDevice() throws Exception {
        String body = login("agent1", "secret", "DEV-A").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "DEV-A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("agent1"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", "DEV-B"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndChecksDevice() throws Exception {
        String body = login("agent1", "secret", "DEV-A").andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body);
        String refresh = json.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"deviceId\":\"DEV-B\"}".formatted(refresh)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"deviceId\":\"DEV-A\"}".formatted(refresh)))
                .andExpect(status().isOk());
        // l'ancien refresh token est révoqué (rotation)
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\",\"deviceId\":\"DEV-A\"}".formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLogsInWithoutDeviceAndOnlyReachesAdminRoutes() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin#12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(body).get("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
