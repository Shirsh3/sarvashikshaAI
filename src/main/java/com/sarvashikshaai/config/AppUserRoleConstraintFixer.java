package com.sarvashikshaai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL compatibility:
 * <p>
 * When the app started earlier (before SUPER_ADMIN existed), Hibernate created a CHECK
 * constraint on {@code app_users.role} that doesn't include {@code SUPER_ADMIN}.
 * This component updates that constraint so the dev bootstrap seeder can insert the
 * SUPER_ADMIN user.
 * <p>
 * It is intentionally defensive (wrapped in try/catch) so H2/local startup won't fail.
 */
@Component("appUserRoleConstraintFixer")
@RequiredArgsConstructor
public class AppUserRoleConstraintFixer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixIfNeeded() {
        try {
            // Drop known constraint if present, then re-create it with SUPER_ADMIN allowed.
            jdbcTemplate.execute("ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check");
            jdbcTemplate.execute(
                    "ALTER TABLE app_users ADD CONSTRAINT app_users_role_check " +
                            "CHECK (role IN ('TEACHER','ADMIN','SUPER_ADMIN'))"
            );
        } catch (Exception ignored) {
            // H2 or existing schema may not match; don't block app startup.
        }
    }
}

