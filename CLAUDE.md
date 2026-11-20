# CLAUDE.md — project conventions

Read `docs/ARCHITECTURE.md` (system rules) and the component doc (e.g. `docs/vendor-platform.md`) before changing code.

## Non-negotiables
- Patient personal data never leaves the Clinic Server. The Vendor Platform must never store it.
- Never invent facts about laws, device protocols or third-party APIs: mark `ASSUMPTION` and ask.
- Modular monolith: modules talk only through their `api` package or published events (Spring Modulith test enforces it).
- UUIDv7 ids (`Ids.newId()`), `timestamptz` in UTC, money as `bigint` UZS, soft delete (`deleted_at`), optimistic locking (`version`).
- Every endpoint checks a permission (`@PreAuthorize("hasAuthority('…')")`), never a role name.
- Every state change of a business entity writes an audit row in the same transaction.
- Errors are problem+json with a stable `code`; UI translates codes (uz-Latn, uz-Cyrl, ru, en). No user-facing text in the backend.
- No personal data or secrets in logs.

## Code style (Kotlin)
- Domain: plain Kotlin, no Spring/SQL annotations; invariants enforced in constructors/factory methods and behaviour methods.
- Persistence: `JdbcClient` with explicit SQL in `infrastructure`; Flyway migrations `V<n>__<module>_<what>.sql`, never edited after merge.
- Application services own transactions (`@Transactional`) and inject `java.time.Clock`.
- Controllers are thin: validate request DTO → call service → map to response DTO.
- Tests: domain unit tests (JUnit 5 + AssertJ), API integration tests extend `IntegrationTest` (Testcontainers PostgreSQL).

## Commands
- Backend + libs tests (Docker needed for Testcontainers): `./gradlew test`
- Web: `cd vendor-platform/web && pnpm install && pnpm dev` (proxies /api to :8080) / `pnpm test` / `pnpm typecheck` / `pnpm build`
- API contract changed? `./gradlew :vendor-platform:backend:test --tests '*OpenApiExportTest'` then `cd vendor-platform/web && pnpm gen:api`
- Local run: `docker compose -f deploy/vendor/docker-compose.yml up --build`

## Web console conventions
- All user-facing text via i18next keys in all 4 locales (`src/i18n/locales/*.json`); the parity test fails on missing keys.
  Uzbek Latin uses `ʻ` (U+02BB) in oʻ/gʻ and `ʼ` (U+02BC) for the tutuq belgisi.
- Server data only through hooks in `src/api/hooks.ts`; errors shown through `errorMessage`/`fieldErrors` (never raw text).
- Instants are displayed in Asia/Tashkent via `src/lib/format.ts`, regardless of the browser time zone.
- Hide actions the user lacks permission for (`usePermissions().can`), but never rely on it: the server enforces.

## Git
- Small, meaningful commits. Never commit secrets (signing keys, `.env`).
