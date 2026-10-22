package com.guens.fieldcollect.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAssignmentRepository extends JpaRepository<UserAssignment, UserAssignmentId> {
    List<UserAssignment> findByIdUserId(Long userId);
}
