package com.guens.fieldcollect.user;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_assignments")
@Getter @NoArgsConstructor @AllArgsConstructor
public class UserAssignment {
    @EmbeddedId
    private UserAssignmentId id;
}
