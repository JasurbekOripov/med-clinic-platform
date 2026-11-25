# Architecture overview

Medical center management platform for Uzbekistan. Time zone `Asia/Tashkent` (UTC+5, no DST), currency UZS,
languages Uzbek Latin, Uzbek Cyrillic, Russian (+ English for vendor staff).

## 1. Components

```
                   ┌──────────────── Vendor Platform (Linux VPS, Docker) ───────────────┐
                   │  vendor-platform (Spring Boot + React console)  ── PostgreSQL       │
                   │  tenancy · licensing · devices · monitoring · identity · audit     │
                   └───────────────▲────────────────────────────────────────────────────┘
                                   │ HTTPS, outgoing only (pairing, heartbeat, license status,
                                   │ later: metrics outbox, encrypted backups, releases)
 ┌─────────── Clinic LAN (one per branch) ─────────────────────────────────────────────┐
 │  Clinic Server (Windows PC): clinic-server (Spring Boot + React) ── PostgreSQL      │
 │      ▲ HTTPS (LAN)                     ▲ paired device token                         │
 │  Browsers on staff PCs           Print Agent (Compose Desktop tray app per printer PC)│
 └─────────────────────────────────────────────────────────────────────────────────────┘
```

| Component | Runs on | Status |
|---|---|---|
| `vendor-platform` | Vendor VPS (Linux, Docker Compose) | **V1 in progress** — see [vendor-platform.md](vendor-platform.md) |
| `clinic-server` | Clinic PC (Windows 10/11, Windows services) | decisions agreed — see [clinic-server.md](clinic-server.md) |
| `print-agent` | Any clinic PC with a printer | later |
| `libs/common` | Shared JVM library: ids, phone, time, errors | V1 |
| `libs/license` | Shared JVM library: license format, Ed25519 verify | V1 |
| `libs/vendor-protocol` | Shared DTOs of the Clinic Server ↔ Vendor protocol | V1 |

Hard rules:
- **Patient personal data never leaves the Clinic Server.** The Vendor Platform stores tenants, licenses,
  devices, technical health and (later) aggregated metrics and opaque encrypted backups only.
- Clinic Server ↔ Vendor traffic is **initiated by the Clinic Server only** (no inbound ports in clinics).
- Clinics must work fully **offline**; every vendor dependency (license, heartbeat) degrades gracefully.

## 2. Repository layout

```
build-logic/            Gradle convention plugins (kotlin, spring)
libs/common             pure Kotlin, no Spring
libs/license            pure Kotlin + JDK crypto, no Spring  (used by vendor + clinic server)
libs/vendor-protocol    pure Kotlin DTOs                     (used by vendor + clinic server)
vendor-platform/backend Spring Boot application (modular monolith)
vendor-platform/web     React + TypeScript + Vite console (bundled into the backend jar)
deploy/vendor           Docker Compose for local run / VPS
docs/                   architecture + per-component design
```

## 3. Module boundaries (applies to every backend)

- **Modular monolith**, one Spring Boot app per deployable. Each business module is a top-level package
  (`…vendor.tenancy`, `…vendor.licensing`, …). Boundaries are verified by a Spring Modulith test.
- Inside a module: `api` (public facade + DTOs other modules may use) · `domain` (pure Kotlin model, rules,
  ports) · `application` (use cases, transactions) · `infrastructure` (SQL repositories, adapters) ·
  `web` (REST controllers). Only `api` (and published events) may be referenced by other modules.
- Persistence via Spring `JdbcClient` with explicit SQL: domain classes have no framework annotations,
  queries are visible and tunable (important for reports and millions of rows later).
- Cross-module side effects go through the `api` facade (synchronous, same transaction) or through
  application events (asynchronous; later moved to a transactional outbox).
- Extension points are interfaces with one implementation today (e.g. `AlertNotifier`, `LicenseSigner`
  key source, future `AiGateway`, `FiscalReceiptPort`).

## 4. Shared conventions

| Topic | Convention |
|---|---|
| Ids | UUIDv7 generated in the application (`Ids.newId()`), time-ordered → index-friendly, globally unique across branches. |
| Tenancy | Clinic app: every business table has `clinic_id` and `branch_id`. Vendor: tenant tables are `clinic` / `branch`; dependent rows reference them. |
| Human codes | Short readable codes (`CL-0001`, branch `CL-0001-01`) for phone support; never used as keys. |
| Time | Stored as `timestamptz` (UTC). API uses ISO-8601 instants. UI renders in `Asia/Tashkent`. Business "day" = Tashkent calendar day. All code takes `java.time.Clock` (testable). |
| Money | `bigint` UZS (no fractions). Never floating point. |
| Soft delete | `deleted_at timestamptz null`; default queries exclude deleted rows; unique indexes are partial (`where deleted_at is null`). Business status (e.g. `ARCHIVED`) is separate from deletion. |
| Concurrency | Every mutable row has `version int`; updates use `where id = ? and version = ?` → `409 CONFLICT` on mismatch. Clients send the `version` they edited. |
| Idempotency | Device/payment/print endpoints accept a client-generated UUID and deduplicate by unique index. |
| Audit | Append-only `audit_log` (DB trigger blocks update/delete), written in the same transaction as the change: actor, action, entity, before/after JSON (secrets stripped), IP, request id. |
| Errors | RFC 9457 `application/problem+json` with stable `code` (`clinic.not_found`) + `params`; UI translates codes. Never leak stack traces. |
| i18n | Backend is language-neutral (codes only). UI catalogs: `uz-Latn` (default), `uz-Cyrl`, `ru`, `en`. Printed documents (clinic app) choose language per document. |
| Phones | Uzbek numbers normalised to E.164 `+998XXXXXXXXX`. |
| API | REST under `/api/v1/**` (staff, session cookie + CSRF) and `/api/device/v1/**` (devices, bearer token). OpenAPI generated by springdoc and committed to `docs/openapi/`. Lists are paginated (`page`, `size` ≤ 100). |
| Security | Server-side sessions (HttpOnly, SameSite=Lax cookie), idle + absolute timeout, login throttling, BCrypt passwords, permission check on every endpoint (`@PreAuthorize`), secrets only as hashes. |
| Observability | Actuator health/readiness, JSON structured logs with `requestId`, no personal data in logs. |
| Tests | Unit tests for domain, Testcontainers PostgreSQL integration tests for API + SQL, Modulith boundary test. |

## 5. Stack

Kotlin 2.3 (JVM 21) · Spring Boot 4.1 (Spring Framework 7, Security 7, Jackson 3) · PostgreSQL 17 · Flyway ·
Spring Modulith · springdoc-openapi · JUnit 5 + AssertJ + MockK + Testcontainers ·
React 19 + TypeScript + Vite + TanStack Query + shadcn/ui (Tailwind) + i18next.
Spring Boot 4 was chosen over 3.x because 3.x left open-source support in mid-2026.

## 6. Decisions recorded for later parts (not designed yet)

- Clinic Server: **Windows only**; installer, Windows services, Windows backup tooling (WAL archiving +
  AES-256 encryption) to be designed at that milestone. Hardware: 4+ cores, 16 GB RAM, 2 SSDs, UPS.
- Owner remote access: WireGuard via a separate relay VPS (traffic stays TLS end-to-end).
- LAN HTTPS: private CA per clinic, installed on staff PCs by the Print Agent installer.
- No patient merge. Doctors never see audit logs (admin only).
- Multi-branch: each branch runs its own Clinic Server; UUIDv7 ids + `clinic_id/branch_id` everywhere make
  later branch ↔ HQ sync possible without schema redesign.
- AI: future `ai-gateway` module; suggestions only, human confirms; no patient data to external services
  without anonymisation and legal approval.
- Open legal questions (need a lawyer/accountant): fiscal receipt obligations, patient consent wording,
  medical record retention periods, validity of image signatures.
