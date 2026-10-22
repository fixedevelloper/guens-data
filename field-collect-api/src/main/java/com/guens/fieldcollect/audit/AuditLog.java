package com.guens.fieldcollect.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_id") private Long actorId;
    @Column(nullable = false, length = 60) private String action;
    @Column(name = "entity_type", length = 60) private String entityType;
    @Column(name = "entity_id") private Long entityId;
    @JdbcTypeCode(SqlTypes.JSON) private String details;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
}
