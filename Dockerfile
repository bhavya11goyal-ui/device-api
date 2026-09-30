# Build stage - uses the committed Maven Wrapper so the image is built
# with the exact same Maven/JDK combination as local dev. Tests are
# skipped here on purpose: the repository/full-integration tests need
# Testcontainers, which needs a Docker socket this build stage doesn't
# have - test execution stays a CI/local `./mvnw verify` responsibility,
# not something the image build does.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Runtime stage - Alpine for a small footprint; a plain JRE (not JDK) is
# enough since nothing here compiles code.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/target/devices-api-*.jar app.jar

EXPOSE 8080

# Alpine's default BusyBox wget is enough here - no need to install curl
# just for this.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
