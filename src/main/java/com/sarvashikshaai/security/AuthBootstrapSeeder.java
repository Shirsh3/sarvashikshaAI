package com.sarvashikshaai.security;

import com.sarvashikshaai.model.UserRole;
import com.sarvashikshaai.model.entity.AppUserEntity;
import com.sarvashikshaai.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates default teacher/admin accounts in dev builds (no signup flow exists yet).
 * <p>
 * Enable/disable via: {@code auth.bootstrap.enabled}.
 */
@Component
@ConditionalOnProperty(prefix = "auth.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@DependsOn("appUserRoleConstraintFixer")
public class AuthBootstrapSeeder {

    private final AppUserRepository repo;

    @Value("${auth.bootstrap.teacher.username}")
    private String teacherUsername;

    @Value("${auth.bootstrap.teacher.password}")
    private String teacherPassword;

    @Value("${auth.bootstrap.admin.username}")
    private String adminUsername;

    @Value("${auth.bootstrap.admin.password}")
    private String adminPassword;

    @Value("${auth.bootstrap.superadmin.username}")
    private String superAdminUsername;

    @Value("${auth.bootstrap.superadmin.password}")
    private String superAdminPassword;

    @jakarta.annotation.PostConstruct
    void seedOnStartup() {
        seedIfMissing();
    }

    public void seedIfMissing() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        createIfMissing(teacherUsername, teacherPassword, UserRole.TEACHER);
        createIfMissing(adminUsername, adminPassword, UserRole.ADMIN);
        createIfMissing(superAdminUsername, superAdminPassword, UserRole.SUPER_ADMIN);
    }

    private void createIfMissing(String username, String rawPassword, UserRole role) {
        if (username == null || username.isBlank()) return;

        repo.findByUsername(username).orElseGet(() -> {
            AppUserEntity entity = new AppUserEntity();
            entity.setUsername(username.trim());
            entity.setRole(role);
            entity.setActive(true);
            entity.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
            return repo.save(entity);
        });
    }
}

