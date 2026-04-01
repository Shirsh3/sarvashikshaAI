package com.sarvashikshaai.controller;

import com.sarvashikshaai.service.SuperAdminCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SuperAdminCleanupApiController {

    private final SuperAdminCleanupService cleanupService;

    /**
     * Preview mode (recommended): shows how many rows would be deleted.
     */
    @GetMapping(value = {"/admin/cleanup/old-attendance/preview", "/api/superadmin/cleanup/old-attendance/preview"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> previewOldAttendance(@RequestParam("days") int days) {
        long count = cleanupService.previewOldAttendance(days);
        return Map.of("ok", true, "action", "old-attendance", "days", days, "count", count);
    }

    @DeleteMapping(value = {"/admin/cleanup/old-attendance", "/api/superadmin/cleanup/old-attendance"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> deleteOldAttendance(@RequestParam("days") int days,
                                                   @RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
                                                   Authentication authentication) {
        if (!confirm) {
            return Map.of("ok", false, "error", "confirm_required");
        }
        String actor = authentication != null ? authentication.getName() : null;
        long deleted = cleanupService.deleteOldAttendance(actor, days);
        return Map.of("ok", true, "action", "old-attendance", "days", days, "deleted", deleted);
    }

    @GetMapping(value = {"/admin/cleanup/temp-data/preview", "/api/superadmin/cleanup/temp-data/preview"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> previewTempData() {
        long count = cleanupService.previewTempData();
        return Map.of("ok", true, "action", "temp-data", "count", count);
    }

    @DeleteMapping(value = {"/admin/cleanup/temp-data", "/api/superadmin/cleanup/temp-data"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> deleteTempData(@RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
                                              Authentication authentication) {
        if (!confirm) {
            return Map.of("ok", false, "error", "confirm_required");
        }
        String actor = authentication != null ? authentication.getName() : null;
        long deleted = cleanupService.deleteTempData(actor);
        return Map.of("ok", true, "action", "temp-data", "deleted", deleted);
    }

    @GetMapping(value = {"/admin/cleanup/inactive-students/preview", "/api/superadmin/cleanup/inactive-students/preview"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> previewInactiveStudents(@RequestParam("days") int days) {
        long count = cleanupService.previewInactiveStudents(days);
        return Map.of("ok", true, "action", "inactive-students", "days", days, "count", count);
    }

    @DeleteMapping(value = {"/admin/cleanup/inactive-students", "/api/superadmin/cleanup/inactive-students"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> deleteInactiveStudents(@RequestParam("days") int days,
                                                      @RequestParam(name = "confirm", defaultValue = "false") boolean confirm,
                                                      Authentication authentication) {
        if (!confirm) {
            return Map.of("ok", false, "error", "confirm_required");
        }
        String actor = authentication != null ? authentication.getName() : null;
        long deleted = cleanupService.deleteInactiveStudents(actor, days);
        return Map.of("ok", true, "action", "inactive-students", "days", days, "deleted", deleted);
    }
}

