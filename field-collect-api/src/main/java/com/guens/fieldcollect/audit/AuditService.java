package com.guens.fieldcollect.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** S'exécute dans la transaction de l'appelant : l'audit est annulé si l'action échoue. */
    public void log(Long actorId, String action, String entityType, Long entityId) {
        AuditLog l = new AuditLog();
        l.setActorId(actorId);
        l.setAction(action);
        l.setEntityType(entityType);
        l.setEntityId(entityId);
        repo.save(l);
    }
}
