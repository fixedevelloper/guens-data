package com.guens.fieldcollect.person;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PersonAdminDtos {
    private PersonAdminDtos() {}

    public record PersonSummary(Long id, String lastName, String firstName, LocalDate birthDate,
                                DocumentType documentType, String documentNumberMasked,
                                Long applicationId, String applicationName,
                                Long countryId, String countryName, Long cityId, String cityName,
                                Long agentId, String agentName, CaptureMode captureMode,
                                boolean hasDocumentImage, Instant createdAt) {}

    public record PersonDetail(Long id, String clientUuid,
                               Long applicationId, String applicationName,
                               Long countryId, String countryName, Long cityId, String cityName,
                               Long agentId, String agentName, String deviceId, CaptureMode captureMode,
                               String lastName, String firstName, Gender gender, LocalDate birthDate,
                               String birthPlace, String nationality, String phone, String email, String address,
                               DocumentType documentType, String documentNumber,
                               LocalDate documentIssueDate, LocalDate documentExpiryDate, boolean hasDocumentImage, boolean hasSignature,
                               BigDecimal ocrConfidence, String ocrRawText, JsonNode extraData,
                               LocalDateTime capturedAt, Instant createdAt) {}

    public record DocumentImage(byte[] bytes, String contentType) {}
}
