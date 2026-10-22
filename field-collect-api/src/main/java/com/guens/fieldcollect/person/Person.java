package com.guens.fieldcollect.person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "persons")
@Getter @Setter @NoArgsConstructor
public class Person {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_uuid", nullable = false, unique = true, length = 36)
    private String clientUuid;
    @Column(name = "application_id", nullable = false) private Long applicationId;
    @Column(name = "country_id", nullable = false) private Long countryId;
    @Column(name = "city_id", nullable = false) private Long cityId;
    @Column(name = "agent_id", nullable = false) private Long agentId;
    @Column(name = "device_id", nullable = false) private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_mode", nullable = false)
    private CaptureMode captureMode;

    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Enumerated(EnumType.STRING) private Gender gender;
    @Column(name = "birth_date") private LocalDate birthDate;
    @Column(name = "birth_place") private String birthPlace;
    private String nationality;
    private String phone;
    private String email;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocumentType documentType;
    @Column(name = "document_number_enc", length = 255) private byte[] documentNumberEnc;
    @Column(name = "document_number_hash", length = 64) private String documentNumberHash;
    @Column(name = "document_issue_date") private LocalDate documentIssueDate;
    @Column(name = "document_expiry_date") private LocalDate documentExpiryDate;
    @Column(name = "document_image_path") private String documentImagePath;
    @Column(name = "signature_path") private String signaturePath;
    @Column(name = "ocr_raw_text", columnDefinition = "mediumtext") private String ocrRawText;
    @Column(name = "ocr_confidence", precision = 5, scale = 2) private BigDecimal ocrConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data")
    private String extraData;

    @Column(name = "captured_at", nullable = false) private LocalDateTime capturedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
