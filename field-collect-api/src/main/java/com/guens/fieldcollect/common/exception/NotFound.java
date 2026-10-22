package com.guens.fieldcollect.common.exception;

import org.springframework.http.HttpStatus;

public final class NotFound {
    private NotFound() {}

    public static ApiException of(String what, Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " introuvable (id=" + id + ").");
    }
}
