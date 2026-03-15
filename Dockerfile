# Build stage (Maven + JDK 17)
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Run stage — ready for Railway (prod profile, PORT from env)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Prod profile: all secrets from Railway Variables (OPENAI_API_KEY, etc.)
ENV SPRING_PROFILES_ACTIVE=prod
# Railway injects PORT at runtime; fallback 8080 for local runs
ENV PORT=8080
EXPOSE 8080

COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -Dserver.port=${PORT} -jar app.jar"]
