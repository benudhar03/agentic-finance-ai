# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Leverage Docker layer caching: copy wrapper + pom first, download deps separately
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy the actual source and build
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Run as a non-root user
RUN addgroup --system spring && adduser --system --ingroup spring spring

# --chown here (not just `USER` before COPY) is what actually makes the jar
# owned by the spring user — USER only affects the process's runtime identity,
# not the ownership of files copied afterward.
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring:spring

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]