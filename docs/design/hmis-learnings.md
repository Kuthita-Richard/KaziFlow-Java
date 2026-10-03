# What KaziFlow should learn from the HMIS sample company

**Source:** 47 screenshots of a working Kenyan hospital system (a JSP web app, "Reliable Healthcare"), captured as its expanded sidebar menu. They show **navigation only** — no data-entry screens — so everything below is about *how the system is organised and what it offers*, not how its screens behave.
**Method:** the screenshots were read by OCR and checked against `docs/reference/HMIS_Feature_Reference.md` (accurate; two omissions fixed), then transcribed into `docs/reference/hmis_menu_catalog.tsv` (405 entries, each tagged by kind). Every number below comes from `python3 tools/hmis_catalog_stats.py`; every statement about KaziFlow was checked against the code or schema, not remembered.

## 1. What the menu is, measured

| | Entries | Share |
|---|---|---|
| List / view | 121 | 30% |
| **Report** | 113 | 28% |
| Transaction / data entry | 55 | 14% |
| Register a master record | 45 | 11% |
| Setting / configuration | 38 | 9% |
| Workflow action (confirm, reverse, dispatch, close, reconcile…) | 24 | 6% |
| Live work queue | 9 | 2% |

| Domain | Entries | Share |
|---|---|---|
| Clinical (patient-facing: appointments, patients, lab, radiology, nursing, pharmacy, procedures, physio, mortuary) | 103 | 25% |
| **HR, payroll & leave** | 78 | 19% |
| Billing & insurance | 61 | 15% |
| Configuration (Administration) | 48 | 12% |
| General master data (taxes, terms, payment methods, suppliers, departments, cost centres, items, assets, vehicles) | 40 | 10% |
| Accounting & banking | 38 | 9% |
| Procurement & stores | 31 | 8% |
| Messaging & management reports | 6 | 1% |

## 2. The lessons (evidence → meaning for KaziFlow)

1. **Reports and lists are the product.** 58% of the menu (234 of 405) is looking at data; reports alone are 28%, and "Collections Reports" is the largest single module (21 entries). *For KaziFlow:* the view/report engine built in v11.29 is the right investment, and the built-in modules' bespoke report screens are candidates to be re-expressed on it.
2. **41% of the menu is mechanical** — "Register X" + "View X" pairs (45 registers, 121 lists). That is exactly what the custom-module engine already generates. *For KaziFlow:* many "system modules" should be **starter packs** (importable module definitions), not hand-written Java.
3. **75% of a hospital system is not hospital-specific** (302 of 405 entries). Strip out the clinical modules and what remains is an ordinary ERP: HR, accounting, procurement, stores, assets, billing. *For KaziFlow:* this validates the architecture (generic engines + thin vertical layers) and says the highest-value work is the **general** back office, which every one of the 22 industry profiles benefits from, not more hospital screens.
4. **Workflow states are real but smaller than expected.** 33 entries (8%) exist because records move through generic states (confirm, reverse, finalize, dispatch, batch, close, reconcile, approve, post), plus 14 hospital-specific admit/discharge entries. **8 are "Yet To Be …" reports** — i.e. a *status filter over a list*. *For KaziFlow:* a status/lifecycle engine plus the saved views we now have gives all eight for free.
5. **Accounting is mapping-driven.** Four settings ("Service Types / Service Categories / Consultation Categories / Item Categories **Credit Accounts**") tell the ledger which account each kind of income posts to, so nobody posts by hand. Around them: opening balances, journals (including *unbalanced* and *yet-to-be-finalised compound* journals), bank accounts with reconciliation, petty cash, bank loans, and the standard statements (general ledger, trial balance, P&L with month comparison, balance sheet, payables aging, supplier ledger). *For KaziFlow:* see §3 — this is the biggest gap.
6. **The same report shapes are reused across modules.** Stock report / movement / reorder / stock take appear in Pharmacy, Physiotherapy and Inventory Reports. *For KaziFlow:* one inventory engine with shared reports (KaziFlow's `stock_takes`, `stock_transfers`, `stock_movements` and `batches` already exist), exposed to any module that holds stock.
7. **Pricing is keyed by who pays and where.** 22 insurance/scheme entries, 14 corporate/company, 4 branch, 7 co-pay/allowance/discount. *For KaziFlow:* a hospital detail, but the general form — price tiers by customer group and per-branch overrides — suits retail and wholesale SMEs.
8. **The administrator's settings tell you what should be configurable.** 38 entries. Notable ones KaziFlow lacks: password-expiry policy, invoice numbering **per document type**, how selling price is derived from cost, show/hide toggles per user group, a billing limit per patient/customer, configurable approval privileges. *For KaziFlow:* consistent with the "everything dynamic" principle; becomes a settings backlog.
9. **Cash-desk control reports are first-class.** *Canceled Charges*, *Edited Payments*, *Refunds*, *User Activity Logs*, *Collections Per Shift*, *Close Shift*. KaziFlow already records the audit trail and returns; a "controls" pack built on views would be cheap and valuable to any shop owner worried about cashier fraud.
10. **Self-service is row-level, not module-level.** "View Payslips", "My Performance Reviews", "My Subordinates' Reviews". KaziFlow's permissions grant a module or an action; they can't say "only your own rows" or "your team's". *For KaziFlow:* the permission engine needs a **scope** (all / own / team) before an employee self-service portal is possible.
11. **50 flat top-level items is too many.** The HMIS even ships a "Navigation Style" preference to cope. *For KaziFlow:* Positions already hide what a person can't use; as modules multiply (custom modules, packs) the sidebar needs **groups** (e.g. Sales, Stock, People, Money, Admin) and a compact/expanded choice.

## 3. How KaziFlow compares (each row verified in code)

| HMIS area | KaziFlow today | Gap |
|---|---|---|
| Appointments, patients, encounters, prescriptions, lab results | Present (basic): `appointments`, `patients`, `patient_encounters`, `patient_prescriptions`, `lab_results` | No admissions / wards / beds, no dispensing confirm-reverse flow |
| Radiology, nursing, physio, mortuary, insurance, MoH reporting | None | Vertical; defer (see §4) |
| Billing | Sales, quotations, deni (credit book) | No invoice lifecycle (dispatch → batch), no insurer claims |
| Payroll | `payroll_runs/entries`, statutory rates as data (SHIF/NSSF/PAYE via `PayrollTaxConfig`) | Only deductions and allowances: no overtime, advances, loans, tardiness, earnings/benefits master, provident fund |
| **Leave** | `leave_requests` with a **free-text type**, pending/approved/rejected | No leave types, periods, holidays, entitlements or balances |
| Performance | None | KPIs, reviews, supervisors |
| **Accounting** | `accounts` (seeded chart) and `transactions` (a **single-entry** income/expense log, one account per row). The "Chart of Accounts" screen is a **derived summary** built from other modules' totals ("Cash & Bank (est. from net cash flow)"); `accounts.balance` is **never written by any code** | **No ledger:** no journal lines, no bank-account balances, no trial balance / balance sheet from books, no posting rules, no period close |
| Banking, petty cash, bank loans, opening balances | None | All missing |
| Taxes | `VATConfig` (rates as data) | Fine for VAT; no general tax master |
| Payment methods, terms of payment | Payment methods are a **`CHECK (payment_method IN ('cash','mpesa','card','bank'))` constraint** on `sales`, so adding one (cheque, SACCO deduction…) needs a schema migration; there is no terms-of-payment concept | Both should be master data (the HMIS has a *Payment Methods* and a *Terms Of Payment* list) |
| Suppliers, purchase orders | Present (`suppliers`, `purchases`, `purchase_returns`, outstanding balance) | No supplier invoices, credit notes, quotations, vouchers |
| Stores, requisitions, issue/loan to cost centre | `stock_transfers`, `stock_takes`, `stock_movements`, `batches`, `branches` | No requisitions, order notes, cost centres, issue/loan |
| Departments | `departments` table | — |
| Assets, vehicles & mileage | None | Custom-module / starter-pack territory |
| SMS | Africa's Talking integration | No templates, no "message everyone who visited between dates" |
| Self-service | None | Needs scoped permissions (lesson 10) |

## 4. Findings in KaziFlow surfaced by this comparison (not fixed yet)

- **Invoice numbers are `COUNT(*)+1`** (`SaleDAO.generateSaleNumber`) on a `UNIQUE` column, read and then written in one transaction. In server/client LAN mode, two PCs selling at the same moment can fail the second with `SQLITE_BUSY_SNAPSHOT` — the same read-then-write pattern that caused the v11.28 permissions bug — **in the checkout path**. (No code deletes sales, so permanent collisions don't happen today.) It is also not configurable per document type, which the HMIS treats as a setting.
- **The "Chart of Accounts" screen can mislead:** it presents derived figures as ledger accounts, and the underlying `accounts.balance` column is dead. The code comments are candid about the estimate; the screen itself isn't.
- **`SessionManager.getBusinessSetting()`** is a dead stub (PROJECT_GUIDE §11).

## 5. Recommended order for the "system modules" work

| # | Work | Why now | Size | Depends on |
|---|---|---|---|---|
| 1 | **Numbering service + fix the checkout race** — a sequence table, atomic next-number per document type, configurable prefix/format, bounded retry | Small, fixes a real POS risk, and every later document type needs it | S | — |
| 2 | **Lifecycle engine** — status (Draft → Confirmed/Final → Reversed), immutable once final, reversal creates an audit trail; offered as an option on custom entities | Unlocks requisitions, approvals, "Yet to be…" views; 8% of the menu | M | 1 |
| 3 | **Links to built-in records** (Employee, Supplier, Department, Branch, Product, Customer) in custom modules | Asset custodian, vehicle driver, requisition store all need them | M | — |
| 4 | **Starter packs** — export/import a module definition as JSON; ship Fleet & Mileage, Assets, Requisitions, Cost Centres, Petty Cash, Terms of Payment, Payment Methods | 41% of the menu is this shape; delivers many "modules" without new Java | M | 2, 3 |
| 5 | **HR depth** — leave types/periods/holidays/entitlements/balances; payroll earnings, benefits, overtime, advances, loans as data | HR is 19% of the menu (second largest) and the most visible gap for any employer | L | — |
| 6 | **Accounting core** — journal entries and lines, posting rules (category → account), opening balances, bank accounts + reconciliation, petty cash, trial balance / P&L / balance sheet from the books | The biggest structural gap; needs careful design with an accountant | XL | 1, 2 |
| 7 | **Permission scope** (all / own / team) → employee self-service | Needed for payslips and leave requests | M | 5 |
| 8 | **Sidebar groups + navigation style**; **controls report pack** (voids, edits, refunds, activity, per-shift) | Cheap; matters as modules multiply | S | — |
| — | Hospital clinical depth (admissions, radiology, nursing, insurance claims, MoH) | 25% of the menu but vertical; only if the Hospital profile becomes a priority | XL | 2, 4, 6 |

## 6. Open questions for the owner

1. Which of these is the next piece of "system modules" work? (My recommendation: 1 → 2 → 3 → 4, then 5.)
2. Is the **Hospital** profile a near-term priority, or are the general back-office areas (HR, accounting, procurement, assets) what you want to strengthen first?
3. For accounting (item 6): will KaziFlow be used to produce statutory books, or as a management view alongside an accountant's own system? That decides how strict the ledger must be (period locking, immutable journals).
4. Do you want the starter packs to be editable by customers after import (a copy they own), or locked templates?
