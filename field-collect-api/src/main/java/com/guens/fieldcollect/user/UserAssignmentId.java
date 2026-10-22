package com.guens.fieldcollect.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class UserAssignmentId implements Serializable {
    @Column(name = "user_id") private Long userId;
    @Column(name = "application_id") private Long applicationId;
    @Column(name = "city_id") private Long cityId;
}
