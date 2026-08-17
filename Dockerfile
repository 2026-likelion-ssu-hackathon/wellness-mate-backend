FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test
RUN set -eu; \
    JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print)"; \
    test -n "$JAR_FILE"; \
    test "$(printf '%s\n' "$JAR_FILE" | wc -l | tr -d ' ')" -eq 1; \
    cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
