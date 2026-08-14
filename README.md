# Suspicious Lions Backend

Spring Boot backend server for the Suspicious Lions team.

## Requirements

- JDK 17 or later

## Run

```bash
./gradlew bootRun
```

To allow frontend origins other than the local defaults, set `CORS_ALLOWED_ORIGINS` as a comma-separated list.

```bash
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173 ./gradlew bootRun
```

The default allowed origins are `http://localhost:3000` and `http://localhost:5173`.

## Test

```bash
./gradlew test
```

## URLs

After starting the server, the following URLs are available:

- Health check: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
