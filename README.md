# MedClinic platform

Medical center management platform for Uzbekistan (multi-clinic, multi-branch, offline-first clinic servers).

| Part | Status |
|---|---|
| Vendor Platform V1 (tenants, licenses, pairing, monitoring, console) | ✅ `vendor-platform/` |
| Clinic Server, Print Agent | planned |

- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Vendor Platform design: [docs/vendor-platform.md](docs/vendor-platform.md)
- Run locally: [deploy/vendor/README.md](deploy/vendor/README.md)

```bash
docker compose -f deploy/vendor/docker-compose.yml up --build
```
