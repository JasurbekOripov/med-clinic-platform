# MedClinic platform

Medical center management platform for Uzbekistan (multi-clinic, multi-branch, offline-first clinic servers).

| Part | Status |
|---|---|
| Vendor Platform V1 (tenants, licenses, pairing, monitoring, console) | ✅ `vendor-platform/` |
| Clinic Server M1 (setup, staff, roles, settings, audit, license, Excel import) | ✅ `clinic-server/` |
| Clinic Server M2+ (registration, queue, cashier, lab, …), Print Agent | planned |

- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Vendor Platform design: [docs/vendor-platform.md](docs/vendor-platform.md)
- Clinic Server decisions: [docs/clinic-server.md](docs/clinic-server.md)
- Try everything locally: [deploy/local/README.md](deploy/local/README.md)

```bash
docker compose -f deploy/local/docker-compose.yml up --build
```
