package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.UserRole;
import com.sarvashikshaai.model.entity.AppUserEntity;
import com.sarvashikshaai.service.SuperAdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/users")
@RequiredArgsConstructor
public class SuperAdminUserApiController {

    private final SuperAdminUserService superAdminUserService;

    /**
     * Reset login for Teacher, Admin, or Super Admin (one active account per role).
     */
    @PostMapping(value = "/reset", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> reset(@RequestBody ResetRequest req) {
        UserRole role;
        try {
            role = UserRole.valueOf(req.role() == null ? "" : req.role().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid_role");
        }
        AppUserEntity updated = superAdminUserService.resetCredentials(role, req.username(), req.password());
        return Map.of(
                "ok", true,
                "username", updated.getUsername(),
                "role", updated.getRole().name());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "bad_request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> stateConflict(IllegalStateException ex) {
        return ResponseEntity.status(409).body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "conflict"));
    }

    public record ResetRequest(String role, String username, String password) {}
}
