# Clinic Server — design decisions

The program that runs **inside each clinic**: one server PC holds the database and serves the web app to every
staff PC over the clinic LAN. All patient and clinic data lives only here. See [ARCHITECTURE.md](ARCHITECTURE.md)
for system-wide rules; this document records what the owner decided for the Clinic Server.

Status: decisions agreed, M1 not started.

## 1. Deployment

| Topic | Decision |
|---|---|
| Where it runs | One server PC per clinic (per branch later), on-premise. Not cloud. |
| OS | Windows 10/11 only (Windows 7 is not supported: no current Chrome, no Java 21). |
| Hardware | 4+ cores, 16 GB RAM, 2 SSDs, UPS. Network and electricity assumed stable. |
| Staff PCs | Nothing installed except a desktop shortcut; current Chrome/Edge. Print Agent only on PCs with printers. |
| Address | Fixed IP via DHCP reservation on the router; shortcut `https://<ip>`. |
| Install | One `setup.exe` (Inno Setup): bundled PostgreSQL + Java runtime + app as Windows services that start on boot; asks for the vendor pairing code and creates the first Admin. *ASSUMPTION to verify at M9: redistribution terms of the PostgreSQL Windows binaries and WinSW.* |
| Internet | Expected to be available; used only for the Vendor Platform (license, heartbeat, updates, later metrics and encrypted backups). The clinic keeps working if it drops. |
| Updates | Vendor publishes a release → the clinic server downloads it (signed), installs at night with a pre-update backup and automatic rollback on failure → staff refresh Chrome. |
| Vendor Platform | Runs in the cloud (or on one chosen server). Holds clinics, licenses, server health only — **never patient or clinic business data**. |

## 2. Access control

Every screen and action is a permission; roles are templates (stored as data, so new roles need no code);
the Admin can grant or remove single permissions per user.

| Role | Sees / does |
|---|---|
| **Admin (= owner)** | Everything, including money reports, audit log, settings, staff, refunds, emergency approval |
| Reception | Patients, visits, queue tickets, ready results; adds/changes services **only while unpaid** |
| Cashier | Payments, shifts (X/Z), debts list; **no refunds** |
| Doctor | Own queue, patient history, consultations, ordering services |
| Lab | Analyses, samples, results |
| Nurse | Inpatient ward tasks |
| Vendor support | Full access, used by the vendor when the clinic asks for a check/test; via the secure tunnel; every action audited. *ASSUMPTION: needs the clinic's written consent in the contract (legal check).* |

Login: login + password (no PIN). **Switch user** button on shared PCs (reception, cashier) and automatic lock
after inactivity, so nobody works under someone else's name.

## 3. Payment rules (apply to all modules)

1. **Unpaid services are visible only to Reception and Cashier.** Doctors, lab and nurses see a patient/service
   only when it is **paid**, or when the **Admin allowed it from his own account** (emergency). The allowance is
   recorded as a debt: who allowed, when, why.
2. Normal staff can never make an unpaid patient visible.
3. Inpatients: the Admin allows the **whole stay once** at admission; daily charges then accumulate on the stay
   balance (advance + final payment) without separate approvals.
4. Reception may change or remove a service **only while it is unpaid** (with reason, audited).
5. After payment: only the Admin can cancel a service and refund (reason required, audited). Cashier and Reception
   cannot cancel or refund.

## 4. License behaviour in the clinic

| State | Behaviour |
|---|---|
| Valid | Normal work |
| Grace period (default 14 days after expiry) | Normal work + visible warning to all users |
| After grace | **Program stops**: "License expired — contact your vendor" screen instead of work screens. No data is deleted. |
| Renewed/extended by the vendor | The new license arrives automatically via heartbeat (about a minute when online) and the clinic unlocks. The vendor can extend at any time (new end date or more grace days). |

Verification is offline (Ed25519, `libs/license`); a clock guard detects the system clock being moved backwards.

## 5. Data import

Manual entry everywhere **plus Excel import** for price list, staff and patients: download template → fill →
upload → preview with errors and possible duplicates → confirm. Nothing is saved before confirmation.

## 6. Milestone M1 — foundation

1. Project setup (same stack and conventions as the Vendor Platform), shared web UI pieces.
2. Identity: staff CRUD with deactivation (never delete), signature image, roles from templates, per-user
   permission overrides, login, switch user, idle lock, login throttling.
3. Clinic settings: name, logo, letterhead, address, receipt texts, working hours.
4. Audit log (Admin only).
5. Vendor link: pairing, heartbeat, license install + offline check, grace warning and stop screen, clock guard.
6. Excel import framework (template → preview → confirm), first used for staff.
7. Web shell in Uzbek Latin, Uzbek Cyrillic and Russian; tests; seed data.

Later milestones (M2–M9) follow the original plan; their module-specific questions are asked at each milestone.
