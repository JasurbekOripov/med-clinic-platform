# Vendor Platform — V1 design

The vendor's own control plane: tenants (clinics, branches), licenses, Clinic Server pairing and health
monitoring. Runs on the vendor VPS (Linux + Docker Compose). **Never stores patient data.**

V1 scope: staff auth · clinics · branches · licenses · pairing · heartbeat + server dashboard · audit · 4 UI languages.
Out of scope (extension points only): owner metrics, Telegram, releases/updates, backup storage, VPN.

## 1. Backend modules

Package root `uz.medclinic.vendor`.

| Module | Responsibility | Public API used by others |
|---|---|---|
| `shared` | ids, clock, errors (problem+json), paging, web/security plumbing | everything (open module) |
| `identity` | vendor staff, login, sessions, roles → permissions | `CurrentActor` |
| `audit` | append-only audit log + query | `AuditRecorder` |
| `tenancy` | clinics, branches | `TenancyQueries` (clinic/branch summaries, status checks) |
| `licensing` | issue / renew / revoke / download licenses, signing keys | `LicenseQueries` (current license for clinic) |
| `devices` | pairing codes, device credentials, device authentication | `DeviceQueries` |
| `monitoring` | heartbeats, server status, alerts | — (publishes `AlertOpened` / `AlertResolved` events) |

Future modules plug in next to these: `metrics` (aggregated outbox ingest), `releases`, `backups`, `notifications`.

## 2. Domain model

```
vendor_staff ─┐ (actor of every audit row)
              │
clinic 1───* branch 1───* pairing_code
  │                 └───* device 1───1 device_health
  │                           └───* heartbeat
  │                           └───* monitoring_alert
  └───* license (renewal chain: superseded_by → license)
audit_log (append-only)        spring_session (+ attributes)
```

| Entity | Key fields | Rules |
|---|---|---|
| **VendorStaff** | login (unique, case-insensitive), full_name, password_hash (BCrypt), role `OWNER`/`SUPPORT`, active, must_change_password, last_login_at, version | Never deleted, only deactivated. The last active OWNER cannot be deactivated or demoted. |
| **Clinic** | code `CL-0001`, name, legal_name, owner_name, phone (E.164), address, region, notes, status, next_branch_no, version, deleted_at | Status `ACTIVE ⇄ SUSPENDED`, `ACTIVE/SUSPENDED → ARCHIVED`, `ARCHIVED → ACTIVE` (restore). Soft delete only from `ARCHIVED`. |
| **Branch** | clinic_id, code `CL-0001-01`, name, address, phone, status, version, deleted_at | Same status machine. Creating/editing needs a non-archived clinic. Soft delete only from `ARCHIVED`. |
| **License** | clinic_id, serial (global, human), plan, modules[], max_users, max_branches, starts_on/ends_on (Tashkent days), valid_from, valid_until, expiry_grace_days, issued_at/by, key_id, payload (signed bytes), signature, renewed_from, superseded_at/by, revoked_at/by/reason | Immutable once issued (payload is what was signed). At most one *current* license per clinic (`revoked_at` and `superseded_at` null; partial unique index). |
| **PairingCode** | branch_id, clinic_id, code_hash (SHA-256), expires_at (default 24 h), created_by, claimed_at, claimed_device_id, cancelled_at | Shown once; single use; requires active clinic and branch. |
| **Device** | clinic_id, branch_id, token_hash (SHA-256), status `ACTIVE`/`REVOKED`, hostname, os, app_version, paired_at, last_seen_at, last_ip | At most one ACTIVE device per branch; a new successful pairing revokes the previous one (server replaced / restored). |
| **DeviceHealth** | latest heartbeat snapshot: app_version, uptime, disks, last backup, error counts, clock skew | Upserted on each heartbeat. |
| **Heartbeat** | id = client `heartbeatId` (idempotency), device_id, received_at, sent_at, payload jsonb | History, purged after 30 days. |
| **MonitoringAlert** | device_id, clinic_id, branch_id, type, details, opened_at, resolved_at | At most one open alert per (device, type). |
| **AuditLog** | occurred_at, actor_type `STAFF`/`DEVICE`/`SYSTEM`, actor_id, actor_name, action, entity_type, entity_id, clinic_id, before, after, ip, request_id | DB trigger forbids UPDATE/DELETE. |

Regions: the 14 first-level divisions (Karakalpakstan, Andijan, Bukhara, Fergana, Jizzakh, Kashkadarya,
Khorezm, Namangan, Navoi, Samarkand, Surkhandarya, Syrdarya, Tashkent region, Tashkent city).

**License effective status** (derived, not stored): `REVOKED` if revoked · `SUPERSEDED` if renewed ·
`PENDING` before `valid_from` · `ACTIVE` until `valid_until` · `GRACE` for `expiry_grace_days` after ·
`EXPIRED` afterwards.

## 3. Roles and permissions

| Permission | OWNER | SUPPORT |
|---|:-:|:-:|
| `CLINIC_VIEW` (clinics, branches) | ✔ | ✔ |
| `CLINIC_EDIT` (create/update clinics, branches) | ✔ | ✔ |
| `CLINIC_STATUS` (suspend, archive, restore, delete) | ✔ | |
| `LICENSE_VIEW` (incl. download) | ✔ | ✔ |
| `LICENSE_MANAGE` (issue, renew, revoke) | ✔ | |
| `DEVICE_PAIR` (create/cancel pairing codes) | ✔ | ✔ |
| `DEVICE_REVOKE` | ✔ | |
| `MONITORING_VIEW` | ✔ | ✔ |
| `STAFF_MANAGE` | ✔ | |
| `AUDIT_VIEW` | ✔ | |

Role → permission mapping lives in code for V1 (two fixed roles); every endpoint checks a permission, never a role.

## 4. Staff API (`/api/v1`, session cookie + CSRF header)

| Method & path | Permission | Notes |
|---|---|---|
| `GET /auth/csrf` | public | issues `XSRF-TOKEN` cookie |
| `POST /auth/login` | public | `{login, password}` → `Me`; throttled |
| `POST /auth/logout` | authenticated | |
| `GET /auth/me` | authenticated | `{id, login, fullName, role, permissions, mustChangePassword}` |
| `POST /auth/password` | authenticated | `{currentPassword, newPassword}`; only call allowed while `mustChangePassword` |
| `GET/POST /staff`, `GET/PUT /staff/{id}` | STAFF_MANAGE | |
| `POST /staff/{id}/activate · /deactivate · /reset-password` | STAFF_MANAGE | reset returns a temporary password once |
| `GET /clinics?q=&status=&region=&page=&size=` | CLINIC_VIEW | `q` matches code, name, legal name, owner, phone |
| `POST /clinics`, `GET/PUT /clinics/{id}` | CLINIC_VIEW/EDIT | PUT carries `version` |
| `POST /clinics/{id}/status` `{status, version}` | CLINIC_STATUS | |
| `DELETE /clinics/{id}?version=` | CLINIC_STATUS | archived only |
| `GET/POST /clinics/{id}/branches` | CLINIC_VIEW/EDIT | |
| `GET/PUT /branches/{id}`, `POST /branches/{id}/status`, `DELETE /branches/{id}` | as clinics | |
| `GET/POST /clinics/{id}/licenses` | LICENSE_VIEW/MANAGE | POST issues the first license |
| `GET /licenses/{id}`, `GET /licenses/{id}/file` | LICENSE_VIEW | file = `license-CL-0001-<serial>.lic` |
| `POST /licenses/{id}/renew` | LICENSE_MANAGE | new license, old → superseded |
| `POST /licenses/{id}/revoke` `{reason}` | LICENSE_MANAGE | |
| `GET /license-keys` | LICENSE_VIEW | public keys (for embedding into the Clinic Server build) |
| `GET /license-catalog` | LICENSE_VIEW | known module codes and grace-day limits for the UI |
| `GET/POST /branches/{id}/pairing-codes`, `POST /pairing-codes/{id}/cancel` | DEVICE_PAIR | POST returns the plain code once |
| `GET /branches/{id}/devices`, `POST /devices/{id}/revoke` | MONITORING_VIEW / DEVICE_REVOKE | |
| `GET /monitoring/servers?status=&q=` | MONITORING_VIEW | one row per active device + branches never paired |
| `GET /monitoring/alerts?open=` | MONITORING_VIEW | |
| `GET /audit?entityType=&entityId=&actorId=&from=&to=` | AUDIT_VIEW | |

Errors: `application/problem+json` with `code`, e.g. `validation.failed` (+ `fieldErrors`), `common.not_found`,
`common.version_conflict` (409), `auth.bad_credentials`, `auth.too_many_attempts` (429 + `Retry-After`),
`clinic.invalid_status_transition`, `license.current_exists`.

## 5. License format

A `.lic` file is a small JSON envelope; the signature covers the **exact payload bytes**, so no JSON
canonicalisation is needed on either side.

```json
{
  "format": "medclinic.license",
  "formatVersion": 1,
  "algorithm": "Ed25519",
  "keyId": "vk-2026-1",
  "payload": "<base64url(payload JSON bytes)>",
  "signature": "<base64url(Ed25519(payload bytes))>"
}
```

Payload (`schema: 1`):

```json
{
  "schema": 1,
  "licenseId": "0193…", "serial": 12,
  "clinicId": "0193…", "clinicCode": "CL-0001", "clinicName": "…",
  "plan": "STANDARD",
  "modules": ["REGISTRATION", "QUEUE", "CASHIER", "LAB", "DOCTOR", "INPATIENT", "OPERATIONS", "REPORTS", "TELEGRAM"],
  "maxUsers": 25, "maxBranches": 1,
  "validFrom": "2026-11-19T19:00:00Z", "validUntil": "2027-11-20T19:00:00Z",
  "expiryGraceDays": 14,
  "issuedAt": "2026-11-20T10:15:00Z"
}
```

- Dates are chosen in the UI as Tashkent calendar days; `validFrom` = start of the first day,
  `validUntil` = start of the day **after** the last day (exclusive).
- `modules` are strings; the Clinic Server ignores codes it doesn't know (forward compatible). Admin is always included.
- Keys: Ed25519 (JDK built-in). The private key is a PKCS#8 PEM file mounted into the container
  (`LICENSE_SIGNING_KEY_PATH`), never in the DB or git. Public keys are **embedded in the Clinic Server
  build** as a keyring (`keyId → key`); never trusted from the network. Rotation = add a new key id, ship
  it in a release, then switch signing.
- Verification (`libs/license`, used offline by the Clinic Server): envelope → key by `keyId` → verify
  signature → parse payload → check `clinicId` = paired clinic → evaluate time window with grace.
- Revocation cannot be enforced offline; the Clinic Server learns it from the heartbeat response
  (§6) and applies it when online.
- **ASSUMPTIONS:** plan names are free codes until the commercial plan catalogue exists; default
  expiry grace = 14 days; `maxUsers` is enforced by each Clinic Server, `maxBranches` by the vendor at pairing.
  The behaviour after grace (proposed: read-only, never hiding existing data) is a Clinic Server decision.

## 6. Clinic Server protocol (`/api/device/v1`, DTOs in `libs/vendor-protocol`)

### Pairing

1. Vendor staff creates a pairing code for a branch → `K7QM-2XRD-9WTF` (12 chars Crockford Base32,
   60 bits, valid 24 h, single use, only its SHA-256 is stored).
2. The installer on the Clinic Server asks for the code and calls
   `POST /pairing/claim {code, hostname, os, appVersion}` (no auth; throttled per IP).
3. The vendor, in one transaction: validates code (not expired/claimed/cancelled; clinic and branch active;
   branch count ≤ `maxBranches` when a current license exists), revokes the branch's previous device,
   creates the device with a fresh token, marks the code claimed, writes audit.
4. Response: `{deviceId, clinicId, clinicCode, branchId, branchCode, deviceToken, heartbeatIntervalSeconds, licenseFile?}`.
   `deviceToken` = `dvt1_` + base64url(32 random bytes); shown once, stored only as SHA-256. The Clinic Server
   stores it encrypted at rest (Windows DPAPI — decided later).

All failures return the same `pairing.invalid_code` to avoid leaking which codes exist.

### Device authentication

`Authorization: Bearer dvt1_…` → lookup by token hash; revoked/unknown → `401 device.unauthorized`.
`last_seen_at` is updated on every authenticated call.

### Heartbeat

`POST /heartbeats` every 60 s:

```json
{
  "heartbeatId": "uuid (idempotency key)", "sentAt": "…", "appVersion": "1.4.2", "uptimeSeconds": 86400,
  "disks": [{"mount": "C:", "totalBytes": 512000000000, "freeBytes": 210000000000}],
  "lastBackup": {"completedAt": "…", "status": "SUCCESS", "lastSuccessAt": "…", "sizeBytes": 123456789},
  "errors": {"since": "…", "errorCount": 0, "warningCount": 3},
  "installedLicenseId": "uuid | null"
}
```

Response:

```json
{
  "serverTime": "…", "nextHeartbeatInSeconds": 60,
  "license": {"currentLicenseId": "uuid | null", "installedLicenseStatus": "CURRENT | SUPERSEDED | REVOKED | UNKNOWN | NONE"}
}
```

- Duplicate `heartbeatId` → 200 with the same semantics, no new row.
- `serverTime` lets the Clinic Server detect clock drift (clock guard); the vendor also stores the skew.
- When `currentLicenseId ≠ installedLicenseId`, the Clinic Server downloads it: `GET /license` → `.lic` file
  (404 `license.none` if the clinic has none). This makes renewals automatic.
- Extension point: later the response gets `commands` (e.g. update available) — clients ignore unknown fields.

### Server status and alerts

| Status | Rule |
|---|---|
| `ONLINE` | last heartbeat ≤ `offline-after` (default 10 min) |
| `OFFLINE` | older than that |
| `NEVER_SEEN` | device paired, no heartbeat yet |

A scheduled job (every minute, idempotent: unique open alert per device and type) opens and resolves:

| Alert | Opens when | Resolves when |
|---|---|---|
| `SERVER_OFFLINE` | no heartbeat for `offline-after` | next heartbeat |
| `DISK_LOW` | any disk < 10 % free or < 5 GB | all disks above thresholds |
| `BACKUP_FAILED` | last backup status FAILED | a successful backup |
| `BACKUP_STALE` | last successful backup older than 36 h | a newer backup |
| `CLOCK_SKEW` | \|server − client clock\| > 5 min | skew back within limit |

Opening and resolving publish `AlertOpened` / `AlertResolved` events → the future `notifications` module
(Telegram/e-mail) subscribes; V1 only shows them in the dashboard. Revoked devices and archived
clinics/branches are excluded.

## 7. Security

- Sessions: Spring Session JDBC (survive restarts), cookie `VSESSION` HttpOnly + Secure (prod) + SameSite=Lax,
  idle timeout 30 min, absolute lifetime 12 h, session id rotated at login, all sessions of a user are
  invalidated on deactivation or password reset.
- CSRF: cookie-to-header (`XSRF-TOKEN` → `X-XSRF-TOKEN`) for staff API; device API is stateless bearer.
- Login throttling (in memory; one instance): 5 failures per login or 20 per IP within 15 min → blocked 15 min.
  Same generic error for unknown login and wrong password.
- Passwords: BCrypt (delegating encoder, upgradable), min 10 chars.
- First OWNER is bootstrapped from `VENDOR_BOOTSTRAP_OWNER_LOGIN` / `…_PASSWORD` only when no staff exist,
  with `must_change_password = true`.
- TLS is terminated by a reverse proxy (Caddy) in production; the app trusts `X-Forwarded-*` only from it.

## 8. Implementation notes (V1 as built)

- Web console: `vendor-platform/web` (React 19, TanStack Query, Tailwind, Radix dialog), bundled into the jar;
  hashed assets cached for a year, `index.html` never cached. TS types are generated from
  `docs/openapi/vendor-platform.json`, which a backend test regenerates (`pnpm gen:api` in the web folder).
- Monitoring list is assembled in memory (fine for thousands of branches); switch to a SQL view if that changes.
- Login and pairing throttles are in memory: correct for one instance; move to the DB/Redis before scaling out.
- Events `ClinicDeleted`/`BranchDeleted` (tenancy) revoke licenses and devices synchronously in the same transaction;
  `AlertOpened`/`AlertResolved` (monitoring) have no listeners yet (future notifications module).
- Run/deploy: see `deploy/vendor/README.md`.

## 9. Configuration (env)

`DB_URL`, `DB_USER`, `DB_PASSWORD` · `LICENSE_SIGNING_KEY_PATH`, `LICENSE_SIGNING_KEY_ID`,
`LICENSE_SIGNING_GENERATE_IF_MISSING` (dev only) · `MONITORING_OFFLINE_AFTER` (10m) ·
`PAIRING_CODE_TTL` (24h) · `VENDOR_BOOTSTRAP_OWNER_LOGIN/PASSWORD` · `SESSION_COOKIE_SECURE`.
