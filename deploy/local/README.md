# Local trial: Vendor Platform + one Clinic Server

```bash
docker compose -f deploy/local/docker-compose.yml up --build
```

The first build takes 5–10 minutes. Then:

1. **Vendor Platform** – <http://localhost:8080>, sign in as `owner` / `Change-Me-Now-2026`, set your own password.
   Create a clinic → add a branch → issue a license → on the branch press **Connect server** and copy the code.
2. **Clinic Server** – <http://localhost:8081> opens the first-run setup.
   Vendor address: `http://vendor:8080` (the vendor's name inside Docker), pairing code from step 1.
   Then create the Admin account and sign in.
3. Back in the Vendor Platform, **Servers** shows the clinic server as online within a minute.

Stop with `Ctrl + C`. Start again later with `docker compose -f deploy/local/docker-compose.yml up`.
To delete all local trial data, add `down -v` instead of `up` (this removes the Docker volumes).

This trial uses development settings (plain http, generated keys). In production the Clinic Server runs on the
clinic's Windows PC as Windows services (installer milestone) and the vendor's public key is built into the release.
