# syntax=docker/dockerfile:1.7
# Clinic Server image (local trial; production runs as Windows services): React app + Spring Boot backend in one jar. Build context = repository root.

FROM node:22-alpine AS web
WORKDIR /src
RUN corepack enable
# pnpm workspace: install only what the clinic app (and the shared UI package) needs.
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY libs/web-ui/package.json libs/web-ui/
COPY clinic-server/web/package.json clinic-server/web/
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile --filter clinic-web...
COPY libs/web-ui libs/web-ui
COPY clinic-server/web clinic-server/web
RUN pnpm --filter clinic-web build

FROM eclipse-temurin:21-jdk-noble AS backend
WORKDIR /src
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY build-logic build-logic
COPY libs libs
COPY clinic-server/backend clinic-server/backend
COPY --from=web /src/clinic-server/web/dist clinic-server/web/dist
# Tolerate Windows checkouts: strip CR line endings and restore the executable bit.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew :clinic-server:backend:bootJar --no-daemon --max-workers=2 -x test

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home /app app \
 && mkdir -p /app/data && chown -R app:app /app
WORKDIR /app
COPY --from=backend --chown=app:app /src/clinic-server/backend/build/libs/clinic-server.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TZ=UTC
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
