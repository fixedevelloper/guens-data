package com.guens.fieldcollect.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .findFirst().orElse("Requête invalide");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("VALIDATION_ERROR", msg));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT",
                "Opération impossible : doublon ou données liées."));
    }

    /**
     * Filet de sécurité : sans lui, une exception non gérée part vers /error, protégé par la sécurité, et le client reçoit
     * un 401 trompeur au lieu d'un 500. Les exceptions de sécurité restent traitées par Spring Security.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) throw e;
        if (e instanceof ErrorResponse er) { // erreurs HTTP de Spring MVC (404, 405, paramètre manquant…)
            return ResponseEntity.status(er.getStatusCode()).body(new ApiError("HTTP_" + er.getStatusCode().value(),
                    "Requête invalide."));
        }
        log.error("Erreur interne non gérée", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR",
                "Erreur interne du serveur."));
    }
}
