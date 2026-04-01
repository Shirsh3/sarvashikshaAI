package com.sarvashikshaai.config;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Runs Flyway migrations explicitly at application startup.
 * <p>
 * We keep this manual runner because Flyway/Spring Boot versions can sometimes be incompatible
 * for very new PostgreSQL releases.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "flyway.manual", name = "enabled", havingValue = "true", matchIfMissing = false)
public class FlywayManualMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final Environment environment;

    @Value("${flyway.manual.fail-on-unsupported:true}")
    private boolean failOnUnsupported;

    @Value("${flyway.manual.repair-on-validation-error:false}")
    private boolean repairOnValidationError;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
        } catch (FlywayValidateException ve) {
            if (!repairOnValidationError) {
                throw ve;
            }
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.repair();
            flyway.migrate();
        } catch (Exception e) {
            // Flyway can fail for brand-new PostgreSQL versions that its engine doesn't know yet.
            // In that case we keep the app running in local/dev. In production we fail fast.
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Unsupported Database")) {
                boolean isProd = Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
                if (!isProd && !failOnUnsupported) {
                    return;
                }
            }
            throw e;
        }
    }
}

