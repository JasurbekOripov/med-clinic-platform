# Vendor Platform — running and deploying

## Local (development profile)

```bash
docker compose -f deploy/vendor/docker-compose.yml up --build
```

Open <http://localhost:8080>, sign in as `owner` with `Change-Me-Now-2026` and set your own password.
The dev profile generates a throw-away license signing key into the `signing-keys` volume, uses
plain-HTTP cookies and plain-text logs.

Without Docker (fast UI work): run PostgreSQL, start the backend with `SPRING_PROFILES_ACTIVE=dev`
(`./gradlew :vendor-platform:backend:bootRun`), then `cd vendor-platform/web && pnpm dev` → <http://localhost:5173>.

## Production checklist (vendor VPS)

1. **TLS**: put a reverse proxy (e.g. Caddy) in front; expose only 443. The app trusts `X-Forwarded-*`
   (`server.forward-headers-strategy=framework`), so the app port must not be reachable from outside.
2. **Profile**: do not set `SPRING_PROFILES_ACTIVE=dev`. Secure cookies, JSON logs and fail-fast key loading are the defaults.
3. **License signing key** (the most valuable secret of the business):
   ```bash
   openssl genpkey -algorithm ed25519 -out vendor-license.pem
   openssl pkey -in vendor-license.pem -pubout -out vendor-license.pem.pub
   chmod 600 vendor-license.pem
   ```
   Mount both files read-only, set `LICENSE_SIGNING_KEY_PATH` and a new `LICENSE_SIGNING_KEY_ID`
   (e.g. `vk-2026-1`). Keep an offline copy. The public key (`GET /api/v1/license-keys`) is embedded into
   Clinic Server builds. Losing the private key means re-issuing every license with a new key.
4. **Secrets**: `DB_PASSWORD`, `VENDOR_BOOTSTRAP_OWNER_PASSWORD` (used only while no staff exist) from an
   `.env` file outside git (`chmod 600`).
5. **Backups** of the vendor database (pg_dump nightly, off the VPS). It holds no patient data.
6. **Monitoring**: `/actuator/health/liveness` and `/actuator/health/readiness`.

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | localhost/vendor | PostgreSQL connection |
| `LICENSE_SIGNING_KEY_PATH`, `LICENSE_SIGNING_KEY_ID` | – / `vk-dev-1` | Ed25519 key (PEM + `.pub` next to it) |
| `SESSION_IDLE_TIMEOUT`, `SESSION_ABSOLUTE_TIMEOUT` | `30m`, `12h` | staff sessions |
| `SESSION_COOKIE_SECURE` | `true` | only `false` for local http |
| `PAIRING_CODE_TTL` | `24h` | lifetime of a pairing code |
| `HEARTBEAT_INTERVAL`, `MONITORING_OFFLINE_AFTER` | `60s`, `10m` | heartbeat cadence and offline alert threshold |
| `MONITORING_HEARTBEAT_RETENTION` | `30d` | heartbeat history kept |
| `SWAGGER_UI_ENABLED` | `false` | Swagger UI at `/swagger-ui.html` |
