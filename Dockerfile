# Build stage (Maven + JDK 17)
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Railway sets PORT; Spring Boot reads it via server.port=${PORT:8080}
ENV PORT=8080
EXPOSE 8080

COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
