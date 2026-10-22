package com.guens.fieldcollect.person;

import com.guens.fieldcollect.common.dto.PageResponse;
import com.guens.fieldcollect.person.PersonAdminDtos.*;
import com.guens.fieldcollect.security.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/persons")
public class PersonAdminController {

    private final PersonAdminService service;

    public PersonAdminController(PersonAdminService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PersonSummary> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long countryId,
                                              @RequestParam(required = false) Long cityId,
                                              @RequestParam(required = false) Long applicationId,
                                              @RequestParam(required = false) Long agentId,
                                              @RequestParam(required = false) CaptureMode captureMode,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.search(new PersonFilter(q, countryId, cityId, applicationId, agentId, captureMode, from, to),
                page, size);
    }

    @GetMapping("/{id}")
    public PersonDetail detail(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return service.detail(id, me.id());
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        DocumentImage img = service.documentImage(id, me.id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.bytes());
    }

    @GetMapping("/{id}/signature")
    public ResponseEntity<byte[]> signature(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        DocumentImage img = service.signatureImage(id, me.id());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.bytes());
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long countryId,
                       @RequestParam(required = false) Long cityId,
                       @RequestParam(required = false) Long applicationId,
                       @RequestParam(required = false) Long agentId,
                       @RequestParam(required = false) CaptureMode captureMode,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @AuthenticationPrincipal AuthUser me, HttpServletResponse res) throws IOException {
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"persons-" + LocalDate.now() + ".csv\"");
        res.setHeader("Cache-Control", "no-store");
        service.exportCsv(new PersonFilter(q, countryId, cityId, applicationId, agentId, captureMode, from, to),
                res.getWriter(), me.id());
    }
}
