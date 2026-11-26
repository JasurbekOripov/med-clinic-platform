# Clinic Server — design decisions

The program that runs **inside each clinic**: one server PC holds the database and serves the web app to every
staff PC over the clinic LAN. All patient and clinic data lives only here. See [ARCHITECTURE.md](ARCHITECTURE.md)
for system-wide rules; this document records what the owner decided for the Clinic Server.

Status: **M1 implemented** (see §7); M2 in progress (patients, price list, agents, visits and basic cashier done, see §8). Next: queues.

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

## 7. M1 as built

| Module (`clinic-server/backend`) | Contents |
|---|---|
| `identity` | role templates per clinic (Admin, Vendor support locked), custom roles, per-person granted/denied permissions, staff (never deleted), signatures, sessions (`CSESSION`), throttled login, last-Admin rule, license user limit (vendor account not counted) |
| `settings` | clinic profile, logo, letterhead and receipt texts, working hours, idle lock minutes; public subset for the login page |
| `audit` | append-only, rows carry `clinic_id`/`branch_id` from `ClinicContext` |
| `vendorlink` | first-run setup (pair → first Admin), AES-GCM encrypted device token, heartbeat every minute (technical data only), automatic renewal/revocation, offline verification against build-time keys (`license/keyring.properties`), grace banner then stop, clock rollback guard (vendor time resets it), `.lic` upload |
| `imports` | Excel template in the user's language → preview with per-row problems → confirm valid rows; staff import with one-time passwords |

Shared code lives in `libs/spring-support` (errors, paging, request ids, SPA serving, login throttle) and `libs/web-ui`
(components, API client, formatting, phone input). Web app: `clinic-server/web` (Uzbek Latin, Uzbek Cyrillic, Russian).

**Before the first real release:** put the vendor's production public key into
`clinic-server/backend/src/main/resources/license/keyring.properties` (from the Vendor Platform's license keys page).
Development and tests use `clinic.vendor.dev-keyring`, honoured only in the dev/test profiles.

## 8. M2 as built so far

| Module | Contents |
|---|---|
| `catalog` | price list, changed only with `PRICE_LIST_MANAGE` (Admin); read by reception and cashiers. Groups → services (optional code, one name as the clinic writes it, price in whole UZS). A service can have **an own price per doctor** (consultations; doctors = active staff of non-system roles with `CONSULTATION_WRITE`; no own price → base price). Lab and other services: one price for all. Services can be deactivated or soft-deleted; visits will copy name and price, so past visits never change. **Discount types** (name + 1–100 %), created by the Admin; Reception picks one per visit from a list (no document number). Percent discount rounds half up to whole sum. Every change audited. |
| `agents` | referral agents managed with `AGENT_MANAGE` (Admin): name, optional phone, **fixed fee per paid referred visit** (UZS), notes, active. Reception reads the list to record who referred a visit; the visit will copy the fee, the Admin report sums fees owed and payouts. Soft delete, audited. |
| `visits` | a visit groups services ordered for one patient; name, price, the visit's single discount and the agent fee are copied at ordering. Reception (`VISIT_CREATE`) orders, a consultation needs a doctor (doctor's own price); `VISIT_EDIT_UNPAID` cancels only unpaid services (reason required) and changes the discount (re-prices unpaid services only); the agent is fixed after the first payment. **Doctors, lab and nurses get only paid services or those the Admin allowed** (`EMERGENCY_ALLOW`, reason required, the amount stays owed) — enforced by the server. Publishes `VisitItemsPaid`. |
| `cashier` | basic cashier (`PAYMENT_ACCEPT`): list of visits with unpaid services, pay chosen services in cash, card or transfer; the visit row is locked so a service is paid once, and the total the cashier saw must still match (409 `payment.amount_changed`). Immutable payment with copied lines and a receipt number; 80 mm receipt printed from Chrome with the clinic's receipt texts (`/settings/print`). Shifts (X/Z), refunds (Admin) and the debts list come in M5. |
| `patients` | patient card (card number from a sequence, names, gender, birth date, phone required, address, notes), soft delete (`PATIENT_DELETE`, Admin), audit on every change. One search box: up to 6 digits = card number, 7+ digits = phone, otherwise name in Latin or Cyrillic (`NameNormalizer` + `pg_trgm`). Duplicate warning (same name + birth date, or same phone + similar name) while typing and again on save (409 `patient.possible_duplicate` with candidates); Reception may confirm "different person". No merge (owner's decision). |

Web: keyboard-first patients screen (type → ↑/↓ → Enter, F2 = new patient), patient page with a visits-history placeholder.

M2 decisions (owner, 2026-11): consultation price differs per doctor, lab price is the same for everyone; one queue per
doctor/room with daily ticket numbers; pay first, then the patient appears in the queue (basic cashier in M2: take
payment, receipt; shifts, refunds and debts in M5); one discount per visit chosen from the Admin's list; no automatic
repeat-visit price (the doctor orders a new service at full price); referral agents managed by the Admin with a fixed
amount, totals in the Admin report; printing from Chrome (80 mm receipt / A4) until printer models are known; no TV
screen for now; doctors' and lab's opening of patient cards is audited.
