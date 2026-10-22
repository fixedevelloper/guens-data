package com.guens.fieldcollect.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldApplicationRepository extends JpaRepository<FieldApplication, Long> {
    List<FieldApplication> findAllByOrderByNameAsc();
    boolean existsByCode(String code);
}
