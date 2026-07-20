# =====================================================================
# STAGE 1 — BUILD
# Uses a full JDK + Maven image just to compile and package the app.
# We throw this stage away afterward — it never ends up in the final image.
# =====================================================================
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy the Maven wrapper and pom.xml first (so Docker caches the
# dependency download layer separately from your source code —
# dependencies only re-download when pom.xml actually changes).
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy your actual source code and build the jar
COPY src src
RUN ./mvnw clean package -DskipTests -B

# =====================================================================
# STAGE 2 — RUN
# Uses a much smaller JRE-only image (no compiler tools needed at runtime).
# Final image size: ~180MB instead of ~600MB with a full JDK image.
# =====================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy only the compiled jar from the build stage
COPY --from=build /app/target/complaint-management-system.jar app.jar

# The port your Spring Boot app listens on
EXPOSE 8080

# Start the app
ENTRYPOINT ["java", "-jar", "app.jar"]
