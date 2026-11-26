# Clinic Server — design decisions

The program that runs **inside each clinic**: one server PC holds the database and serves the web app to every
staff PC over the clinic LAN. All patient and clinic data lives only here. See [ARCHITECTURE.md](ARCHITECTURE.md)
for system-wide rules; this document records what the owner decided for the Clinic Server.

Status: **M1 implemented** (see §7); **M2 implemented** (see §8); M3 doctor's conclusions (§9), M4 laboratory (§11) and M5 cashier (§12) implemented. Next: daily backups, then M6 inpatients.

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
| `queues` | queues set up by the Admin (`SETTINGS_MANAGE`): name, 1–2 letter prefix, room, optionally a doctor; services without a doctor are listed in one queue each. On payment (same transaction, `VisitItemsPaid`) every paid service is routed — the chosen doctor's queue first, otherwise the service's queue — and **one ticket per room** gets the day's next number (Tashkent date, restarts daily, atomic counter): `K-001`, `L-015`. The cashier prints one 80 mm slip per room plus the money receipt, each on its own page so the printer cuts between them. Queue screen: today's tickets in number order, "seen" toggle (audited); doctors call by the number on the slip, no TV or calling system. Services with no queue get no slip. |
| `patients` | patient card (card number from a sequence, names, gender, birth date, phone required, address, notes), soft delete (`PATIENT_DELETE`, Admin), audit on every change. One search box: up to 6 digits = card number, 7+ digits = phone, otherwise name in Latin or Cyrillic (`NameNormalizer` + `pg_trgm`). Duplicate warning (same name + birth date, or same phone + similar name) while typing and again on save (409 `patient.possible_duplicate` with candidates); Reception may confirm "different person". No merge (owner's decision). Medical staff opening a card is audited (`patient.viewed`, once per person and patient per 30 minutes). |

Web: keyboard-first patients screen (type → ↑/↓ → Enter, F2 = new patient), patient page with a visits-history placeholder.

M2 decisions (owner, 2026-11): consultation price differs per doctor, lab price is the same for everyone; one queue per
doctor/room with daily ticket numbers; pay first, then the patient appears in the queue (basic cashier in M2: take
payment, receipt; shifts, refunds and debts in M5); one discount per visit chosen from the Admin's list; no automatic
repeat-visit price (the doctor orders a new service at full price); referral agents managed by the Admin with a fixed
amount, totals in the Admin report; printing from Chrome (80 mm receipt / A4) until printer models are known; no TV
screen for now (doctors call by the number on the slip); one slip per room plus one money receipt; doctors' and lab's opening of patient cards is audited.

## 9. M3 as built — doctor's conclusions

Owner's decisions (2026-11): free-text conclusion inside a fixed printed layout (letterhead, title, patient, date, doctor and
signature); no ICD-10 codes (clinics do not report them); doctors do not order services — the patient returns to Reception.

| Module | Contents |
|---|---|
| `conclusions` | one conclusion per paid (or Admin-allowed) service; a service ordered with a doctor is concluded only by that doctor (`CONSULTATION_WRITE`). The author edits it on the same Tashkent day; afterwards corrections are dated, signed notes (the original text never changes). Doctors and nurses read the patient's history (`MEDICAL_HISTORY_VIEW`), Reception reads and reprints for the patient (`RESULTS_HANDOVER`). Each doctor's own text templates. Author's signature image served for printing. Audited. |

Printed conclusion: logo and clinic details from Settings, accent rule, title and date, patient box (name, card, birth date and age, sex), text, notes, doctor's name with signature image over a line, place for the stamp, footer.

Web: "Xulosa yozish" on the doctor's paid services → editor (title from the service, free text, own templates inserted at the
cursor) → A4 print on the clinic letterhead; conclusions history on the patient page with print, same-day edit and notes.

## 10. Printing

Printing goes through Chrome's print dialog, which lists every printer connected to that PC and remembers the last one.
A web page cannot list printers itself; choosing printers once inside the program (receipts → thermal, A4 → laser) and
printing without the dialog needs the **Print Agent** on PCs with printers (planned with the installer, M9).
Receipts and room slips can be reprinted from the visit ("Cheklar", cashier and Reception); copies are marked **NUSXA**
and every reprint is audited (`payment.reprinted`). No fiscal receipts for now (owner, 2026-11).

## 11. M4 as built so far — laboratory forms

Owner's decisions (2026-11): the lab creates its own result forms; ranges can differ by sex, age and state (pregnancy
etc.); results typed by hand, no barcodes or analyzer link yet; "Tayyor" makes results visible to doctors and printable
by Reception. The program ships with **no medical values**.

| Module | Contents |
|---|---|
| `lab` | result forms (`LAB_TEMPLATES_MANAGE`, now in the Lab role template): rows of kind header / number / choice / positive-negative / text; number rows carry reference ranges qualified by sex, age (days, months, years; upper bound exclusive) and physiological state (pregnancy trimester, cycle phase, postmenopause, custom label), kind normal or critical (partitioning as in CLSI EP28 / HL7 FHIR `ObservationDefinition.qualifiedInterval`). The most specific matching range is used; critical limits are checked first; with no matching range the value is printed without a norm, never guessed. Changing rows stores a new version (results keep theirs). A price-list service has at most one form. Audited. |

Results: paid (or Admin-allowed, `VisitItemsAllowed`) services with a form appear on the lab work list in the same
transaction. The lab (`LAB_WORK`) saves values validated against the form version, picks the patient's state when the
form has state ranges (norms preview for that state), and presses "Tayyor"; afterwards corrections need a reason, are
audited and printed. Norms and flags are computed on the server; the entry screen marks ↑ ↓ live with the bounds the
server chose. Doctors, nurses and Reception (`RESULTS_HANDOVER`) read ready results only.

Web: "Laboratoriya" → work list ("Ishlar") and forms ("Shakllar"); result entry; "Tahlil natijalari" on the patient
page; A4 printout on the shared letterhead (values outside the norm bold with ↑/↓, ↑↑/↓↓ for critical, the norm used,
the patient's state, the lab worker's signature).

## 12. M5 as built — cashier shifts, expenses, refunds, debts

Owner's decisions (2026-11): no "pay later" (the only debts are the Admin's emergency allowances); refunds only by the
Admin, whole service, always given back in **cash from the drawer**; cashiers work in shifts; cashiers record cash
expenses (recipient = a staff member or anyone else, mandatory comment, amount).

| Part | Contents |
|---|---|
| Shift | `SHIFT_MANAGE`: open with the opening cash (change money, 0 by default); one open shift per cashier; payments need an open shift and are row-locked against closing. X report = live totals; closing (Z report) compares counted cash with **opening + cash payments − refunds paid out − expenses**, stores the difference and locks the shift. Admin (`REPORTS_VIEW`) lists every cashier's shifts by period and reprints any X/Z report. |
| Expenses | in the open shift; recipient from the staff list or typed in, amount, mandatory comment; audited. |
| Refunds | Admin (`REFUND`) approves for one paid service with a reason → the service becomes **REFUNDED** (no longer counted or visible to medical staff) → the cashier gives the cash back from an open shift ("Pul berildi"), which subtracts it from that shift. |
| Debts | `DEBT_VIEW`: services allowed by the Admin before payment and still unpaid, with patient and total. |

Every action is in the audit log with who, when and where (computer address and browser).
