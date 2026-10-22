package com.guens.fieldcollect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guens.fieldcollect.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPersonsStatsTest {

    private static final AtomicInteger ISO = new AtomicInteger(300);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuditLogRepository auditLogs;

    String adminToken, agentToken, device, suffix;
    long countryId, cityA, cityB, appId;

    @BeforeEach
    void seed() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 6);
        adminToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin#12345\"}")));
        int n = ISO.getAndIncrement();
        String iso = "" + (char) ('A' + n / 26 % 26) + (char) ('A' + n % 26);
        countryId = id(admin(post("/api/v1/admin/countries"), "{\"isoCode\":\"%s\",\"name\":\"Pays-%s\"}".formatted(iso, suffix)));
        cityA = id(admin(post("/api/v1/admin/cities"), "{\"countryId\":%d,\"name\":\"A-%s\"}".formatted(countryId, suffix)));
        cityB = id(admin(post("/api/v1/admin/cities"), "{\"countryId\":%d,\"name\":\"B-%s\"}".formatted(countryId, suffix)));
        appId = id(admin(post("/api/v1/admin/applications"), "{\"code\":\"S-%s\",\"name\":\"Stats %s\"}".formatted(suffix, suffix)));
        admin(put("/api/v1/admin/applications/" + appId + "/cities"), "{\"cityIds\":[%d,%d]}".formatted(cityA, cityB));
        long agent = id(admin(post("/api/v1/admin/users"),
                "{\"username\":\"s-%s\",\"password\":\"password123\",\"fullName\":\"Agent %s\"}".formatted(suffix, suffix)));
        admin(put("/api/v1/admin/users/" + agent + "/assignments"),
                "{\"assignments\":[{\"applicationId\":%d,\"cityId\":%d},{\"applicationId\":%d,\"cityId\":%d}]}"
                        .formatted(appId, cityA, appId, cityB));
        device = "DEV-" + suffix;
        agentToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"s-%s\",\"password\":\"password123\",\"deviceId\":\"%s\"}".formatted(suffix, device))));
    }

    // ---------- helpers
    private ResultActions admin(MockHttpServletRequestBuilder b, String json) throws Exception {
        b.header("Authorization", "Bearer " + adminToken);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b);
    }

    private String token(ResultActions r) throws Exception {
        return mapper.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long id(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long submit(long city, String mode, String lastName, String docNumber) throws Exception {
        String doc = docNumber == null ? "" : ",\"documentType\":\"ID_CARD\",\"documentNumber\":\"" + docNumber + "\"";
        String body = ("{\"clientUuid\":\"%s\",\"applicationId\":%d,\"countryId\":%d,\"cityId\":%d,\"captureMode\":\"%s\","
                + "\"lastName\":\"%s\",\"firstName\":\"Jane\",\"birthDate\":\"1990-04-02\",\"phone\":\"690000000\",\"email\":\"jane@example.com\",\"signature\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==\","
                + "\"capturedAt\":\"2026-10-15T09:30:00\",\"extraData\":{\"k\":\"v\"}%s}")
                .formatted(UUID.randomUUID(), appId, countryId, city, mode, lastName, doc);
        ResultActions r = mvc.perform(post("/api/v1/mobile/persons").header("Authorization", "Bearer " + agentToken)
                .header("X-Device-Id", device).contentType(MediaType.APPLICATION_JSON).content(body));
        r.andExpect(status().isCreated());
        return mapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** 4 personnes : A=3 (2 SCAN dont 1 avec image, 1 MANUAL « =cmd »), B=1 (MANUAL). */
    private long[] seedPersons() throws Exception {
        long p1 = submit(cityA, "SCAN", "Doe", "AB-123456");
        long p2 = submit(cityA, "SCAN", "Smith", "CD-987654");
        long p3 = submit(cityA, "MANUAL", "=cmd", null);
        long p4 = submit(cityB, "MANUAL", "Brown", null);
        String uuid = mapper.readTree(admin(get("/api/v1/admin/persons/" + p1), null).andReturn().getResponse()
                .getContentAsString()).get("clientUuid").asText();
        mvc.perform(multipart("/api/v1/mobile/persons/" + uuid + "/document")
                .file(new MockMultipartFile("file", "x.png", "image/png", PNG))
                .header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device))
                .andExpect(status().isOk());
        return new long[]{p1, p2, p3, p4};
    }

    // ---------- tests
    @Test
    void searchDetailDocumentAndAudit() throws Exception {
        long[] p = seedPersons();

        JsonNode all = json(admin(get("/api/v1/admin/persons?applicationId=" + appId), null));
        assertEquals(4, all.get("totalElements").asInt());
        JsonNode first = json(admin(get("/api/v1/admin/persons?applicationId=" + appId + "&cityId=" + cityA
                + "&q=doe"), null));
        assertEquals(1, first.get("totalElements").asInt());
        JsonNode row = first.get("content").get(0);
        assertEquals("****3456", row.get("documentNumberMasked").asText());
        assertEquals("Stats " + suffix, row.get("applicationName").asText());
        assertTrue(row.get("hasDocumentImage").asBoolean());

        assertEquals(3, json(admin(get("/api/v1/admin/persons?applicationId=" + appId + "&cityId=" + cityA), null))
                .get("totalElements").asInt());
        assertEquals(2, json(admin(get("/api/v1/admin/persons?applicationId=" + appId + "&captureMode=SCAN"), null))
                .get("totalElements").asInt());
        assertEquals(0, json(admin(get("/api/v1/admin/persons?applicationId=" + appId + "&from="
                + LocalDate.now(ZoneOffset.UTC).plusDays(2)), null)).get("totalElements").asInt());

        JsonNode detail = json(admin(get("/api/v1/admin/persons/" + p[0]), null));
        assertEquals("AB123456", detail.get("documentNumber").asText());
        assertEquals("v", detail.get("extraData").get("k").asText());
        assertEquals("Agent " + suffix, detail.get("agentName").asText());

        byte[] img = admin(get("/api/v1/admin/persons/" + p[0] + "/document"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PNG, img);
        admin(get("/api/v1/admin/persons/" + p[1] + "/document"), null).andExpect(status().isNotFound());

        assertTrue(auditLogs.findAll().stream().anyMatch(a -> "PERSON_VIEWED".equals(a.getAction())
                && a.getEntityId() == p[0]));
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> "PERSON_DOCUMENT_VIEWED".equals(a.getAction())
                && a.getEntityId() == p[0]));
    }

    @Test
    void csvExportMasksNumbersAndNeutralisesFormulas() throws Exception {
        seedPersons();
        String csv = admin(get("/api/v1/admin/persons/export?applicationId=" + appId), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String[] lines = csv.trim().split("\r\n");
        assertEquals(5, lines.length); // en-tête + 4
        assertTrue(lines[0].contains("document_number_masked"));
        assertTrue(csv.contains("****3456"));
        assertFalse(csv.contains("AB123456"));
        assertTrue(csv.contains(",'=cmd,"), "injection de formule non neutralisée");
        assertEquals("text/csv;charset=UTF-8", admin(get("/api/v1/admin/persons/export?applicationId=" + appId), null)
                .andReturn().getResponse().getContentType().replace(" ", ""));
    }

    @Test
    void statistics() throws Exception {
        seedPersons();
        String f = "applicationId=" + appId;

        JsonNode ov = json(admin(get("/api/v1/admin/stats/overview?" + f), null));
        assertEquals(4, ov.get("total").asInt());
        assertEquals(4, ov.get("today").asInt());
        assertEquals(1, ov.get("activeAgents").asInt());
        assertEquals(2, ov.get("byCaptureMode").get("SCAN").asInt());
        assertEquals(2, ov.get("byCaptureMode").get("MANUAL").asInt());

        JsonNode byCountry = json(admin(get("/api/v1/admin/stats/by-country?" + f), null));
        assertEquals(1, byCountry.size());
        assertEquals("Pays-" + suffix, byCountry.get(0).get("country").asText());
        assertEquals(4, byCountry.get(0).get("count").asInt());

        JsonNode byCity = json(admin(get("/api/v1/admin/stats/by-city?countryId=" + countryId), null));
        assertEquals(2, byCity.size());
        assertEquals(3, byCity.get(0).get("count").asInt()); // trié par volume décroissant
        assertEquals("A-" + suffix, byCity.get(0).get("city").asText());
        assertEquals(1, byCity.get(1).get("count").asInt());

        JsonNode byApp = json(admin(get("/api/v1/admin/stats/by-application?countryId=" + countryId), null));
        assertEquals(4, byApp.get(0).get("count").asInt());
        assertEquals("Stats " + suffix, byApp.get(0).get("application").asText());

        JsonNode breakdown = json(admin(get("/api/v1/admin/stats/breakdown"), null));
        assertTrue(breakdown.findValues("application").stream().anyMatch(v -> v.asText().equals("Stats " + suffix)));

        JsonNode timeline = json(admin(get("/api/v1/admin/stats/timeline?" + f), null));
        assertEquals(1, timeline.size());
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), timeline.get(0).get("day").asText());
        assertEquals(4, timeline.get(0).get("count").asInt());
    }

    @Test
    void agentCannotAccessPersonsOrStats() throws Exception {
        for (String path : new String[]{"/api/v1/admin/persons", "/api/v1/admin/stats/overview",
                "/api/v1/admin/persons/export"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/persons/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
