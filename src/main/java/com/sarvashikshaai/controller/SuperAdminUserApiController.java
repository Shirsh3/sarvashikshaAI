package com.sarvashikshaai.controller;

import com.sarvashikshaai.model.entity.AppUserEntity;
import com.sarvashikshaai.service.SuperAdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/users")
@RequiredArgsConstructor
public class SuperAdminUserApiController {

    private final SuperAdminUserService superAdminUserService;

    @PostMapping(value = "/teacher/reset", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> resetTeacher(@RequestBody ResetTeacherRequest req) {
        AppUserEntity updated = superAdminUserService.resetTeacherCredentials(req.username(), req.password());
        return Map.of("ok", true, "username", updated.getUsername());
    }

    public record ResetTeacherRequest(String username, String password) {}
}
