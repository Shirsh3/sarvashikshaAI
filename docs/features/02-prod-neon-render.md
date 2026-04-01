## Local testing with `prod` profile (Neon/Render parity)

### Why
- `prod` uses `spring.jpa.hibernate.ddl-auto=validate` so schema must already exist.
- Schema is created/managed by Flyway migrations (`classpath:db/migration`) via the manual runner.

### Run locally as `prod`

PowerShell example (from repo root):

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://<host>/<db>?sslmode=require&channel_binding=require"
$env:SPRING_DATASOURCE_USERNAME="<user>"
$env:SPRING_DATASOURCE_PASSWORD="<password>"
mvn spring-boot:run
```

### What happens on first run
- Flyway runs and creates `flyway_schema_history` plus the tables from `V1__*.sql`, `V2__*.sql`, `V3__*.sql` etc.
- JPA validates mappings against the schema (no auto table creation in prod).

### If the DB is empty but app fails to start
- Ensure Flyway is enabled: `flyway.manual.enabled=true`
- Ensure JDBC URL is correct and reachable (Neon requires SSL).

