package com.guens.fieldcollect.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Écrit une ApiError JSON depuis les filtres de sécurité (hors portée du @RestControllerAdvice). */
@Component
public class ErrorResponseWriter {
    private final ObjectMapper mapper;

    public ErrorResponseWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), new ApiError(code, message));
    }
}
