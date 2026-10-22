package com.guens.fieldcollect.person;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class PersonDtos {
    private PersonDtos() {}

    public record PersonRequest(
            @NotNull UUID clientUuid,
            @NotNull Long applicationId,
            @NotNull Long countryId,
            @NotNull Long cityId,
            @NotNull CaptureMode captureMode,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 100) String firstName,
            Gender gender,
            @PastOrPresent LocalDate birthDate,
            @Size(max = 150) String birthPlace,
            @Size(max = 100) String nationality,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Email @Size(max = 150) String email,
            @Size(max = 255) String address,
            DocumentType documentType,
            @Size(max = 64) String documentNumber,
            LocalDate documentIssueDate,
            LocalDate documentExpiryDate,
            @Size(max = 20000) String ocrRawText,
            @DecimalMin("0") @DecimalMax("100") BigDecimal ocrConfidence,
            JsonNode extraData,
            @NotNull LocalDateTime capturedAt,
            /** Signature numérique : image PNG encodée en base64. */
            @NotBlank @Size(max = 400_000) String signature) {}

    /** Seule réponse renvoyée à l'agent : aucune donnée de la personne n'est restituée. */
    public record SubmitResponse(Long id, String clientUuid, String status) {}

    public record DocumentResponse(String status) {}

    public record SubmitResult(SubmitResponse response, boolean created) {}
}
