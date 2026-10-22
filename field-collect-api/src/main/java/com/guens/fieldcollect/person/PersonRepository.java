package com.guens.fieldcollect.person;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {
    Optional<Person> findByClientUuid(String clientUuid);

    boolean existsByApplicationIdAndDocumentTypeAndDocumentNumberHash(
            Long applicationId, DocumentType type, String hash);
}
