package com.guens.fieldcollect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCrudTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    String adminToken;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = tokenOf(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin#12345\"}")).andExpect(status().isOk()));
    }

    private String tokenOf(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long idOf(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions admin(MockHttpServletRequestBuilder b, String json) throws Exception {
        b.header("Authorization", "Bearer " + adminToken);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    @Test
    void fullAdminScenario() throws Exception {
        String s = suffix();
        long country = idOf(admin(post("/api/v1/admin/countries"),
                "{\"isoCode\":\"zz\",\"name\":\"Pays-%s\"}".formatted(s)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isoCode").value("ZZ")));
        long city = idOf(admin(post("/api/v1/admin/cities"),
                "{\"countryId\":%d,\"name\":\"Ville-%s\"}".formatted(country, s)).andExpect(status().isCreated()));
        long app = idOf(admin(post("/api/v1/admin/applications"),
                "{\"code\":\"app-%s\",\"name\":\"Recensement\"}".formatted(s)).andExpect(status().isCreated()));

        admin(put("/api/v1/admin/applications/" + app + "/cities"), "{\"cityIds\":[%d]}".formatted(city))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cityIds[0]").value(city));

        // création d'un agent + affectation
        String username = "agent-" + s;
        long agent = idOf(admin(post("/api/v1/admin/users"),
                "{\"username\":\"%s\",\"password\":\"password123\",\"fullName\":\"Agent %s\"}".formatted(username, s))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("AGENT")));
        admin(put("/api/v1/admin/users/" + agent + "/assignments"),
                "{\"assignments\":[{\"applicationId\":%d,\"cityId\":%d}]}".formatted(app, city))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignments[0].cityName").value("Ville-" + s));
        // ré-envoi identique (diff) puis vidage
        admin(put("/api/v1/admin/users/" + agent + "/assignments"),
                "{\"assignments\":[{\"applicationId\":%d,\"cityId\":%d}]}".formatted(app, city))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignments.length()").value(1));

        // l'agent se lie à un appareil, puis l'admin le libère
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"password123\",\"deviceId\":\"D-%s\"}".formatted(username, s)))
                .andExpect(status().isOk());
        admin(get("/api/v1/admin/users/" + agent), null).andExpect(jsonPath("$.deviceId").value("D-" + s));
        admin(post("/api/v1/admin/users/" + agent + "/reset-device"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.deviceId").doesNotExist());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"password123\",\"deviceId\":\"D2-%s\"}".formatted(username, s)))
                .andExpect(status().isOk());

        // désactivation -> login refusé
        admin(patch("/api/v1/admin/users/" + agent + "/status"), "{\"active\":false}").andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"password123\",\"deviceId\":\"D2-%s\"}".formatted(username, s)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        // recherche paginée
        admin(get("/api/v1/admin/users?q=" + username + "&role=AGENT&active=false"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        // intégrité : le pays contient une ville -> 409
        admin(delete("/api/v1/admin/countries/" + country), null).andExpect(status().isConflict());
        admin(delete("/api/v1/admin/applications/" + app), null).andExpect(status().isNoContent());
        admin(delete("/api/v1/admin/cities/" + city), null).andExpect(status().isNoContent());
        admin(delete("/api/v1/admin/countries/" + country), null).andExpect(status().isNoContent());
    }

    @Test
    void assignmentRequiresApplicationDeployedInCity() throws Exception {
        String s = suffix();
        long country = idOf(admin(post("/api/v1/admin/countries"),
                "{\"isoCode\":\"yy\",\"name\":\"P-%s\"}".formatted(s)));
        long city = idOf(admin(post("/api/v1/admin/cities"), "{\"countryId\":%d,\"name\":\"V-%s\"}".formatted(country, s)));
        long app = idOf(admin(post("/api/v1/admin/applications"), "{\"code\":\"X-%s\",\"name\":\"X\"}".formatted(s)));
        long agent = idOf(admin(post("/api/v1/admin/users"),
                "{\"username\":\"ag-%s\",\"password\":\"password123\",\"fullName\":\"A\"}".formatted(s)));
        admin(put("/api/v1/admin/users/" + agent + "/assignments"),
                "{\"assignments\":[{\"applicationId\":%d,\"cityId\":%d}]}".formatted(app, city))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPLICATION_NOT_IN_CITY"));
    }

    @Test
    void duplicatesAndValidation() throws Exception {
        String s = suffix();
        String body = "{\"isoCode\":\"xx\",\"name\":\"Dup-%s\"}".formatted(s);
        admin(post("/api/v1/admin/countries"), body).andExpect(status().isCreated());
        admin(post("/api/v1/admin/countries"), body).andExpect(status().isConflict());
        admin(post("/api/v1/admin/users"), "{\"username\":\"a\",\"password\":\"x\",\"fullName\":\"\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adminCannotDeactivateSelfAndAgentCannotUseAdminRoutes() throws Exception {
        String me = mapper.readTree(admin(get("/api/v1/auth/me"), null).andReturn().getResponse()
                .getContentAsString()).get("id").asText();
        admin(patch("/api/v1/admin/users/" + me + "/status"), "{\"active\":false}").andExpect(status().isBadRequest());

        String s = suffix();
        admin(post("/api/v1/admin/users"),
                "{\"username\":\"r-%s\",\"password\":\"password123\",\"fullName\":\"R\"}".formatted(s));
        String agentToken = tokenOf(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"r-%s\",\"password\":\"password123\",\"deviceId\":\"RD-%s\"}".formatted(s, s)))
                .andExpect(status().isOk()));
        mvc.perform(get("/api/v1/admin/countries").header("Authorization", "Bearer " + agentToken)
                .header("X-Device-Id", "RD-" + s)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/countries")).andExpect(status().isUnauthorized());
    }

    @Test
    void accountLocksAfterRepeatedFailuresAndAdminCanUnlock() throws Exception {
        String s = suffix();
        String user = "lock-" + s;
        long id = idOf(admin(post("/api/v1/admin/users"),
                "{\"username\":\"%s\",\"password\":\"password123\",\"fullName\":\"L\"}".formatted(user))
                .andExpect(status().isCreated()));
        String bad = "{\"username\":\"%s\",\"password\":\"wrong\",\"deviceId\":\"L-%s\"}".formatted(user, s);
        String good = "{\"username\":\"%s\",\"password\":\"password123\",\"deviceId\":\"L-%s\"}".formatted(user, s);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        // même avec le bon mot de passe : verrouillé
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        admin(get("/api/v1/admin/users/" + id), null).andExpect(jsonPath("$.lockedUntil").isNotEmpty());

        admin(post("/api/v1/admin/users/" + id + "/unlock"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.lockedUntil").doesNotExist());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        String s = suffix();
        String user = "reset-" + s;
        admin(post("/api/v1/admin/users"),
                "{\"username\":\"%s\",\"password\":\"password123\",\"fullName\":\"R\"}".formatted(user));
        String bad = "{\"username\":\"%s\",\"password\":\"wrong\",\"deviceId\":\"R-%s\"}".formatted(user, s);
        String good = "{\"username\":\"%s\",\"password\":\"password123\",\"deviceId\":\"R-%s\"}".formatted(user, s);
        for (int round = 0; round < 3; round++) { // 3 x (4 échecs + 1 succès) ne doit jamais verrouiller
            for (int i = 0; i < 4; i++) {
                mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(good))
                    .andExpect(status().isOk());
        }
    }
}
