package com.guens.fieldcollect.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guens.fieldcollect.common.crypto.CryptoService;
import com.guens.fieldcollect.person.Person;
import com.guens.fieldcollect.person.PersonRepository;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PersonRepository persons;
    @Autowired CryptoService crypto;

    String adminToken, agentToken, device;
    long countryId, cityId, appId, otherCountryId, otherCityId;

    @BeforeEach
    void setUp() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 6);
        adminToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin#12345\"}")));
        countryId = id(admin(post("/api/v1/admin/countries"), "{\"isoCode\":\"%s\",\"name\":\"C-%s\"}".formatted(iso(), s)));
        cityId = id(admin(post("/api/v1/admin/cities"), "{\"countryId\":%d,\"name\":\"V-%s\"}".formatted(countryId, s)));
        otherCountryId = id(admin(post("/api/v1/admin/countries"), "{\"isoCode\":\"%s\",\"name\":\"O-%s\"}".formatted(iso(), s)));
        otherCityId = id(admin(post("/api/v1/admin/cities"),
                "{\"countryId\":%d,\"name\":\"OV-%s\"}".formatted(otherCountryId, s)));
        appId = id(admin(post("/api/v1/admin/applications"), "{\"code\":\"M-%s\",\"name\":\"Mobile app\"}".formatted(s)));
        admin(put("/api/v1/admin/applications/" + appId + "/cities"),
                "{\"cityIds\":[%d,%d]}".formatted(cityId, otherCityId));
        long agent = id(admin(post("/api/v1/admin/users"),
                "{\"username\":\"m-%s\",\"password\":\"password123\",\"fullName\":\"M\"}".formatted(s)));
        // affecté uniquement à la 1re ville
        admin(put("/api/v1/admin/users/" + agent + "/assignments"),
                "{\"assignments\":[{\"applicationId\":%d,\"cityId\":%d}]}".formatted(appId, cityId));
        device = "DEV-" + s;
        agentToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"m-%s\",\"password\":\"password123\",\"deviceId\":\"%s\"}".formatted(s, device))));
    }

    private static final java.util.concurrent.atomic.AtomicInteger ISO = new java.util.concurrent.atomic.AtomicInteger();

    private static String iso() {
        int n = ISO.getAndIncrement();
        return "" + (char) ('A' + n / 26 % 26) + (char) ('A' + n % 26);
    }

    private ResultActions admin(MockHttpServletRequestBuilder b, String json) throws Exception {
        b.header("Authorization", "Bearer " + adminToken);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b);
    }

    private ResultActions agent(MockHttpServletRequestBuilder b, String json) throws Exception {
        b.header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device);
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

    private String person(String uuid, long country, long city, String mode, String docNumber) {
        String doc = docNumber == null ? "" : ",\"documentType\":\"ID_CARD\",\"documentNumber\":\"" + docNumber + "\"";
        return ("{\"clientUuid\":\"%s\",\"applicationId\":%d,\"countryId\":%d,\"cityId\":%d,\"captureMode\":\"%s\","
                + "\"lastName\":\"Doe\",\"firstName\":\"Jane\",\"birthDate\":\"1990-04-02\",\"phone\":\"690000000\",\"email\":\"jane@example.com\",\"signature\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==\","
                + "\"capturedAt\":\"2026-10-15T09:30:00\",\"extraData\":{\"k\":\"v\"}%s}")
                .formatted(uuid, appId, country, city, mode, doc);
    }

    @Test
    void phoneEmailAndSignatureAreRequired() throws Exception {
        String ok = person(UUID.randomUUID().toString(), countryId, cityId, "MANUAL", null);
        agent(post("/api/v1/mobile/persons"), ok.replace("\"phone\":\"690000000\",", ""))
                .andExpect(status().isBadRequest());
        agent(post("/api/v1/mobile/persons"), ok.replace("\"email\":\"jane@example.com\",", ""))
                .andExpect(status().isBadRequest());
        agent(post("/api/v1/mobile/persons"), ok.replace("jane@example.com", "pas-un-email"))
                .andExpect(status().isBadRequest());
        agent(post("/api/v1/mobile/persons"), ok.replaceAll("\"signature\":\"[^\"]*\",", ""))
                .andExpect(status().isBadRequest());
        agent(post("/api/v1/mobile/persons"), ok.replaceAll("\"signature\":\"[^\"]*\"", "\"signature\":\"bm90LWEtcG5n\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    void birthDateIsOptional() throws Exception {
        String body = person(UUID.randomUUID().toString(), countryId, cityId, "MANUAL", null)
                .replace("\"birthDate\":\"1990-04-02\",", "");
        agent(post("/api/v1/mobile/persons"), body).andExpect(status().isCreated());
    }

    @Test
    void agentSeesOnlyOwnCounters() throws Exception {
        agent(get("/api/v1/mobile/stats"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        agent(post("/api/v1/mobile/persons"), person(UUID.randomUUID().toString(), countryId, cityId, "MANUAL", null))
                .andExpect(status().isCreated());
        agent(get("/api/v1/mobile/stats"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.byCountry[0].countryId").value(countryId))
                .andExpect(jsonPath("$.byCity[0].cityId").value(cityId))
                .andExpect(jsonPath("$.byApplication[0].applicationId").value(appId))
                .andExpect(jsonPath("$.byApplication[0].count").value(1));
        agent(get("/api/v1/mobile/stats/daily?days=7"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].count").value(1)).andExpect(jsonPath("$[0].count").value(0));
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        agent(get("/api/v1/mobile/stats/day?date=" + today), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.byCity[0].count").value(1));
        agent(get("/api/v1/mobile/stats/day?date=2020-01-01"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.byCountry.length()").value(0));
    }

    @Test
    void referenceListsAreLimitedToAssignments() throws Exception {
        agent(get("/api/v1/mobile/countries"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(countryId));
        agent(get("/api/v1/mobile/countries/" + countryId + "/cities"), null)
                .andExpect(jsonPath("$[0].id").value(cityId));
        agent(get("/api/v1/mobile/countries/" + otherCountryId + "/cities"), null)
                .andExpect(jsonPath("$.length()").value(0));
        agent(get("/api/v1/mobile/cities/" + cityId + "/applications"), null)
                .andExpect(jsonPath("$[0].id").value(appId));
        agent(get("/api/v1/mobile/cities/" + otherCityId + "/applications"), null)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void submitIsIdempotentAndStoresDocumentNumberEncrypted() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String num = "AB-123 456";
        String body = person(uuid, countryId, cityId, "SCAN", num);

        long first = id(agent(post("/api/v1/mobile/persons"), body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED")));
        long again = id(agent(post("/api/v1/mobile/persons"), body).andExpect(status().isOk()));
        assertEquals(first, again);

        Person p = persons.findByClientUuid(uuid).orElseThrow();
        assertNotEquals("AB123456", new String(p.getDocumentNumberEnc()));
        assertEquals("AB123456", crypto.decryptString(p.getDocumentNumberEnc()));
        assertEquals(64, p.getDocumentNumberHash().length());
        assertEquals(device, p.getDeviceId());

        // même pièce (formatée autrement) sous un autre clientUuid -> refusée
        agent(post("/api/v1/mobile/persons"), person(UUID.randomUUID().toString(), countryId, cityId, "MANUAL", "ab123456"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERSON_ALREADY_REGISTERED"));
    }

    @Test
    void scopeAndConsistencyRules() throws Exception {
        // ville non affectée
        agent(post("/api/v1/mobile/persons"),
                person(UUID.randomUUID().toString(), otherCountryId, otherCityId, "MANUAL", null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ASSIGNED"));
        // ville affectée mais pays incohérent
        agent(post("/api/v1/mobile/persons"),
                person(UUID.randomUUID().toString(), otherCountryId, cityId, "MANUAL", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CITY_COUNTRY_MISMATCH"));
        // scan sans pièce
        agent(post("/api/v1/mobile/persons"),
                person(UUID.randomUUID().toString(), countryId, cityId, "SCAN", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DOCUMENT_REQUIRED"));
        // saisie manuelle sans pièce : acceptée
        agent(post("/api/v1/mobile/persons"),
                person(UUID.randomUUID().toString(), countryId, cityId, "MANUAL", null))
                .andExpect(status().isCreated());
    }

    @Test
    void documentUploadRules() throws Exception {
        String uuid = UUID.randomUUID().toString();
        agent(post("/api/v1/mobile/persons"), person(uuid, countryId, cityId, "SCAN", "ZZ" + uuid.substring(0, 6)))
                .andExpect(status().isCreated());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        byte[] fake = "not an image at all".getBytes();

        mvc.perform(multipart("/api/v1/mobile/persons/" + uuid + "/document")
                        .file(new MockMultipartFile("file", "x.png", "image/png", fake))
                        .header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        mvc.perform(multipart("/api/v1/mobile/persons/" + uuid + "/document")
                        .file(new MockMultipartFile("file", "x.png", "image/png", png))
                        .header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/mobile/persons/" + uuid + "/document")
                        .file(new MockMultipartFile("file", "x.png", "image/png", png))
                        .header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device))
                .andExpect(status().isConflict());

        // l'image est stockée chiffrée, et se relit à l'identique
        Person p = persons.findByClientUuid(uuid).orElseThrow();
        assertNotNull(p.getDocumentImagePath());
        byte[] onDisk = java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "field-collect-test-docs",
                        p.getDocumentImagePath()));
        assertFalse(onDisk.length >= 4 && onDisk[1] == 'P' && onDisk[2] == 'N');
        assertArrayEquals(png, crypto.decrypt(onDisk));

        // un document inconnu -> 404
        mvc.perform(multipart("/api/v1/mobile/persons/" + UUID.randomUUID() + "/document")
                        .file(new MockMultipartFile("file", "x.png", "image/png", png))
                        .header("Authorization", "Bearer " + agentToken).header("X-Device-Id", device))
                .andExpect(status().isNotFound());
    }

    @Test
    void agentCannotReadBackRecords() throws Exception {
        String uuid = UUID.randomUUID().toString();
        long pid = id(agent(post("/api/v1/mobile/persons"), person(uuid, countryId, cityId, "MANUAL", null)));
        for (String path : new String[]{"/api/v1/mobile/persons", "/api/v1/mobile/persons/" + uuid,
                "/api/v1/mobile/persons/" + pid, "/api/v1/admin/persons"}) {
            int code = agent(get(path), null).andReturn().getResponse().getStatus();
            assertTrue(code == 404 || code == 405 || code == 403, path + " -> " + code);
        }
        agent(delete("/api/v1/mobile/persons/" + pid), null)
                .andExpect(r -> assertTrue(r.getResponse().getStatus() >= 400));
    }
}
