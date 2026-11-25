# syntax=docker/dockerfile:1.7
# Vendor Platform image: React console + Spring Boot backend in one jar. Build context = repository root.

FROM node:22-alpine AS web
WORKDIR /src
RUN corepack enable
# pnpm workspace: install only what the vendor console (and the shared UI package) needs.
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY libs/web-ui/package.json libs/web-ui/
COPY vendor-platform/web/package.json vendor-platform/web/
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile --filter vendor-console...
COPY libs/web-ui libs/web-ui
COPY vendor-platform/web vendor-platform/web
RUN pnpm --filter vendor-console build

FROM eclipse-temurin:21-jdk-noble AS backend
WORKDIR /src
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY libs libs
COPY vendor-platform/backend vendor-platform/backend
COPY --from=web /src/vendor-platform/web/dist vendor-platform/web/dist
# Tolerate Windows checkouts: strip CR line endings and restore the executable bit.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew :vendor-platform:backend:bootJar --no-daemon --max-workers=2 -x test

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home /app app \
 && mkdir -p /app/keys && chown -R app:app /app
WORKDIR /app
COPY --from=backend --chown=app:app /src/vendor-platform/backend/build/libs/vendor-platform.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TZ=UTC
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
