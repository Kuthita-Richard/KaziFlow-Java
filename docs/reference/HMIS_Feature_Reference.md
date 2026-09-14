# Hospital Management Information System (HMIS) — Full Feature Reference

**Source:** Reverse-engineered from 47 sidebar-navigation screenshots of an existing production
system ("Reliable Healthcare" instance, URL path `reliable_healthcare/main_frame.jsp`).
**Context clues:** Kenyan deployment — references to NHIF (National Hospital Insurance Fund),
Pesapal (Kenyan payment gateway), and Ministry of Health ICD-10 reporting confirm this.
**Purpose of this document:** a complete map of the system's modules and menu items, with plain
explanations of what each does, intended as a build spec for an app replicating or drawing on
this feature set.

The original screenshots only show the **expanded left-hand navigation menu** (no data-entry
screens were captured), so explanations below are based on standard hospital/clinic ERP domain
knowledge plus the menu wording itself.

---

## 1. Price List
Master pricing tables used everywhere else in the system for billing.
- **Services Price List** — consultation/procedure prices.
- **Drugs & Non-Pharm Price List** — retail prices for medicines and non-drug consumables.
- **Ward Charges** — daily bed/ward fee schedule.

## 2. Appointments
Front-desk scheduling and visit tracking.
- **Patient Queue** — live queue of waiting patients.
- **Patient Visits** — log of all visits.
- **Patient Visits Report** (+ grouped by diagnosis, + including walk-ins) — analytics on visit volume/case mix.
- **View Appointments** (+ by departmental status, + yet to be closed/finalized) — appointment lifecycle tracking across departments.
- **Upload Appointment Files** — attach documents (referral letters, scans) to a visit.
- **Invoices Report** — billing tied to appointments.
- **Walk-In Patients Queue (Close Visit)** / **Closed Walk-In Visits Report** — separate flow for patients without pre-booked appointments.
- **View Appointments (Patient Departmental Turn-Around-Time)** — measures how long a patient spends in each department (queue efficiency metric).

## 3. Patients
Core patient registry and admissions (inpatient) management.
- **Register Out-Patient** / **Admit Patient** — two entry points: OPD vs inpatient.
- **View Patients / View Admitted Patients / View Discharged Patients**
- **Patient Register Report** — full patient list export.
- **View Current Admitted Patients Report / View Discharged Patients Report**
- **Admissions Report / Admissions Per Doctor Report**
- **Unoccupied Beds Report** — bed occupancy/availability tracking (implies a bed/ward master table).
- **Upload NHIF Capitation Member List** — bulk-import of patients pre-registered under NHIF's capitation (fixed per-member-per-period payment) scheme, so the front desk can verify insurance eligibility instantly.

## 4. Lab
Laboratory workflow, from order to result.
- **Lab Requests (Outpatient / Inpatient)** — doctors order tests.
- **Internal Patient Lab Visits / External Patient Lab Visits** — distinguishes samples processed in-house vs sent to an external reference lab.
- **Lab Visits Report** (+ per patient, + grouped by test)
- **Lab Referrals Report** — tracking of externally referred tests.
- **Lab Tests Prices**
- **Register/View Lab Template**, **Lab Templates Order** — configurable result-entry templates per test type (e.g., a full blood count template with pre-set fields), and control over the order they display in.

## 5. Radiology
Mirrors the Lab module structure for imaging.
- **Radiology Requests**, **Internal/External Patient Radiology Visits**, **Radiology Visits Report (Per Patient)**, **Radiology Tests Prices**.

## 6. Nursing Station
Ward-level nursing workflow.
- **Record Vital Signs & Allergies** — core nursing chart entry.
- **Triaged Patients** — patients who have been through initial nurse assessment/prioritization.
- **Patient Monitoring Notes** — ongoing observation notes.
- **Procedures Report**
- **Upload Appointment Files**
- **Pharmacy Drugs & Procedure Requests** / **Mark Procedures As Complete** / **Pharmacy Drugs Requests List** — nurses can request medication/procedures on a patient's behalf and track fulfillment.
- **Patients Queue**
- **Bill Packages / Bill Dialysis Package** — bundled billing for grouped services (e.g., a maternity package or a dialysis session bundle) rather than itemized billing.

## 7. Pharmacy
Full drug dispensing and stock control.
- **New Over The Counter (OTC) Request / OTC Patients Queue** — walk-in pharmacy sales without a doctor visit.
- **Walk-In Patients Queue**
- **Confirm Pharmacy Requests / Reverse Confirmed Pharmacy Requests** — a two-step confirm/undo workflow (likely separating "pharmacist prepares" from "pharmacist finalizes and deducts stock").
- **Dispense Out-Patient / In-Patient / Dialysis Patients Pharmacy Drugs** — dispensing flows per patient category.
- **Delete Undispensed Drugs Requests / Drugs Dispensed Reversal / Drugs Dispense History**
- **Medicine Sales Report**, **Sales Summary Per Medicine/Per Patient Report**
- **Stock Report** (current, per medication, specify date, suspended drugs)
- **Stock Movement Report / Item Summary** — audit trail of stock ins/outs.
- **Stocks Reorder Report** — low-stock alerts.
- **Medicine Batch Expiry Report** (+ within 3 months) — batch/lot expiry tracking, critical for pharmacy compliance.
- **Medicine Internal Transfers Report** — stock moved between pharmacy stores/branches.
- **Medication Prices**
- **Pharmacy Returns Report** — patient/ward returns of unused medication.
- **Medication Sales Visibility Setting** — admin control over what's shown at point of sale.
- **Edit Medication Group** — drug categorization (e.g., by therapeutic class).
- **Medication Last Purchase Details** — last supplier cost, for pricing/margin decisions.
- **Medication Costing & Consumption** — cost accounting and usage analytics.

## 8. Procedures
- **Procedures Report**, **Edit Procedures Group**, **Procedures Referral** — a catalog of billable clinical procedures (minor surgery, dressing, etc.) separate from lab/radiology/pharmacy.

## 9. Physiotherapy
Mirrors Pharmacy's structure for physio items/sessions:
- Dispense/reverse physio items, dispense history, sales report, stock report, stock take & stock take report, reorder report, stock movement report, item prices.

## 10. Mortuary
- **Storage Fees**, **New/View Mortuary Miscellaneous Fees**, **New Mortuary Admission**, **Mortuary Admissions** — a small billing/admission module for the mortuary as a hospital revenue center.

## 11. Insurance Payments
- **Insurance Payments** — recording payments received from insurers/NHIF.
- **Invoices Reconciliation Report** — matching insurer payments against issued invoices.

## 12. Donations
- **New Donation / View Donations** — tracks non-billable inflows (charity, NGO support, CSR).

## 13. General Receipts
- **New/View General Receipt** — catch-all receipting for income not tied to a specific patient invoice (e.g., cafeteria, parking, sundry income).

## 14. Charges
Cashier/billing operations layer.
- **Bill Transport / Bill Services** — ad hoc billing entries.
- **Confirm Patient Charges** — locking in charges before invoicing.
- **Personal Account Charges** — charges billed to a patient's personal (non-insurance) account.
- **Reconciliation**, **Canceled Charges Report**, **Edited Payments Report** — audit/control reports (important for fraud prevention at the cash desk).
- **InPatient Cash Deposits** — advance deposits taken from admitted patients.
- **Close Shift** — cashier shift close-out/reconciliation.
- **Set Patient Billing Limit** — credit limit control per patient.

## 15. Patient Invoices
Formal invoice generation and lifecycle.
- **Individual Accounts (Outpatient / Inpatient — discharged / inpatient — yet to be discharged)** — invoice generation split by patient status.
- **View Invoices** — same splits, for viewing.
- **View Miscellaneous Income Invoices**
- **Create/View Consultation Retainer Invoice** — for retainer-style corporate consultation contracts.
- **Dispatch Invoices / Dispatched Invoices Report / Invoices Yet To Be Dispatched Report** — a workflow for sending invoices out (e.g., to insurers or corporate clients) and tracking what's pending.
- **Confirm Dispatched Invoices & Create Batch / Batched Invoices Report** — batching invoices, likely for bulk insurer submission.
- **My Notes** — user's personal notes/reminders within this module.

## 16. Insurance Credit Notes & Deductions
- **Insurance Credit Notes** — adjustments/refunds issued against insurer invoices (e.g., a rejected claim line item).
- **Insurance Deductions** — amounts insurers deduct (co-pays, disallowed items) from claim payments.

## 17. Administration
The largest module — system configuration and master data.
- **Register/View Item(s)** — general item master (could overlap with Pharmacy/Items module).
- **View Insurance Service Costs / Insurance Services Discounts & Withholding Taxes / Insurance Medicine Costs / Insurance Ward Daily Charges** — per-insurer pricing tables (insurers often negotiate different rates than cash-paying patients).
- **Upload Clinic Logo / Clinic Logo Details** — branding for printed invoices/receipts.
- **Consultation Types Costing / Register & View Consultation Cost(s)** — pricing by consultation type (e.g., new vs. follow-up, specialist vs. GP).
- **View Patient Registration Charges**
- **Review Charges**
- **Register/View Time Shift** — cashier/staff shift definitions.
- **Register/View Lab Sub-Tests / Lab Sub-Tests Order** — granular sub-components of a lab test panel.
- **View Doctor Consultancy Costs (Overall / Branch)**
- **Service Type Charges Confirmation Configuration**
- **Doctors Notes & Results Saving Configuration**
- **User Activity Logs** — audit trail of user actions (security/compliance).
- **User Passwords Expiration Configuration** — password policy.
- **Personal Accounts Usage**
- **Doctor Notes Categories Visibility**
- **Consultation Type Selection Point Configuration**
- **Register/View Insurance Services Package** — bundled insurance service packages.
- **Remove Undispensed Drugs**
- **Receipt Printer Type** — printer/hardware configuration.
- **Diagnosis Display Setting**
- **Drugs Selling Price Derivation Settings** — rules for auto-calculating retail price from cost (e.g., markup %).
- **Service Costs (Corporate)** — special pricing for corporate clients.
- **Insurance/Company/Scheme Medicine & Services Sales Allowance Mapping** — maps which insurers/companies/schemes cover which specific medicines/services and at what allowance.
- **Invoice Number Per Service Type Setting** — custom invoice numbering sequences.
- **View Supplier Service Costs**
- **Define Branch Service/Medication Cash Costs** — per-branch (multi-site) pricing overrides.
- **View Company Service Costs**
- **Set Insurance/Company Copay Amounts** — patient co-payment configuration.
- **View Company Ward Daily Charges**
- **Consultation Types Costing (Branch) / View Doctor Consultancy Costs (Branch)** — branch-specific variants.

*(This module confirms the system is multi-branch and multi-payer, with layered pricing: cash, corporate, insurance-scheme, and branch-specific.)*

## 18. Collections Reports
Finance/management reporting on money collected.
- **Outpatient Visits Summary Report**
- **Corporate Accounts Charges Report** (+ breakdown)
- **Insurance Statement of Account** (+ reconciled)
- **Debtor Ledger**
- **Collections per Service Report** (corporate / non-corporate / non-corporate non-detailed)
- **Collections grouped By Payment Mode Summary Report**
- **Collections Per Shift Summary Report** — cashier accountability.
- **Collections per Service Report (Non Corporate) Grouped Per Payment Mode**
- **Refunds Report**
- **Corporate Accounts Receivables Aging Report / Receivables Balances Report** — how much corporate clients owe and for how long.
- **NHIF Rebate Report**
- **Insurance Out-Patient Capitation Report**
- **Insurance/Corporate Summary Report**
- **Admissions Report (Billed Amounts & Balances)**
- **Pesapal Transactions Utilization & Balances / Close Pesapal Transactions** — reconciliation of mobile-money/online payments processed through Pesapal (a widely used Kenyan payment gateway aggregating M-Pesa, cards, etc.).

## 19. Ministry Of Health Reports
- **Ministry Of Health Reports** — standardized government reporting output.
- **Map ICD 10 Diseases To Ministry Diseases** — crosswalk between the international ICD-10 diagnosis coding standard and Kenya's MoH disease reporting categories (used for statutory health facility reporting, e.g. via DHIS2).
- **Map Lab Tests** — similarly mapping local lab test names to standardized MoH reporting categories.

## 20. Payroll & HR
Split across several sidebar sections but functionally one HR/Payroll suite:

**Register Payroll Items** (setup/master data): Employee Category, Employee, Tax Status, Overtime Type, Loan Type, Benefit, Earning, Deduction, Job Title, Bank, Provident Fund Bracket.

**Payroll Transactions** (monthly processing): Create Payroll, Advance Pay, Employee Loan Details, Regular Time Details, Overtime Details, Tardiness Details, Employee Benefit, Employee Monthly Deduction, Employee Earning, Employee Deduction.

**View Payroll Items**: employee categories, employees, tax brackets/status, NHIF brackets, NSSF brackets (Kenya's National Social Security Fund), overtime types, loan types, benefits, earnings, deductions, job titles, banks, benefits' minimum taxable aggregate, fringe benefit tax rate, market lending rates, provident fund brackets. *(These NHIF/NSSF/fringe-benefit-tax references confirm Kenyan statutory payroll compliance is built in.)*

**View Payroll Transactions**: view payroll, advance pay, loan details, regular time, overtime, tardiness, benefits, monthly deductions, earnings, deductions, NSSF & provident fund volunteered amounts.

**Payroll Reports**: Employee List Report, View Payroll, Specific Payments Reports, Payments Summary Report.

## 21. Leave Management
- **User Leave Requests Approvals Privileges** — configurable approval hierarchy.
- **New/View Leave Period, Leave Type, Holiday** — setup.
- **Working Days** — calendar configuration.
- **New/View Employee Leave Entitlement**
- **View Leave Requests**
- **Leave Balances Report / Employees Currently On Leave Report / Leave History Report**

## 22. Performance Management
- **Define Employee Supervisors** — reporting-line setup.
- **New/View Key Performance Indicator (KPI)**
- **New/View Employee Performance Review**
- **Employees Performance Reviews Report**

## 23. Accounting — Chart of Accounts
- **Register Account / View Chart Of Accounts / Print-Export / Chart Of Accounts Report**
- **New Service Type / Service Types Credit Accounts / Consultation Categories Credit Accounts / Service Categories Credit Accounts / Item Categories Credit Accounts** — maps each clinical/billing category to the correct GL account, so revenue posts automatically to the right ledger line when a bill is created.

## 24. Opening Balances
- **Insurance Opening Balances / Suppliers Opening Balances** — for migrating historical balances when first setting up the system.

## 25. General Journal
- **New/View Journal Entry**
- **New/View Compound Journal** (+ unbalanced, + yet to be finalized) — multi-line journal entries with draft/review states before posting.

## 26. Banking
- **New/View Bank Account**
- **View/Print Bank Account Transactions**
- **Bank Account Reconciliation** (+ report, + select multiple references)

## 27. Petty Cash
- **Petty Cash Transactions / Print Petty Cash Transactions**

## 28. Bank Loans
- **New/View Bank Loan** — tracking loans taken by the organization (not employee loans, which live under Payroll).

## 29. Taxes
- **Register/View Tax** — tax rate master (VAT etc.).

## 30. Terms Of Payment
- **Register/View Term Of Payment** — e.g., "Net 30", used on supplier/corporate invoices.

## 31. Payment Methods
- **Register/View Payment Method** — cash, M-Pesa, card, cheque, bank transfer, etc.

## 32. Suppliers
- **Register/View Supplier** — vendor master for procurement.

## 33. Departments
- **Register/View Department** — organizational units (also used for cost allocation).

## 34. Cost Centres
- **Register/View Cost Centre** — for departmental cost accounting, separate from Departments.

## 35. Items
General inventory item catalog (drugs, consumables, assets feed from here):
- Register/View Item Category, Item Measuring Unit, Brand, Item.
- **View Items (Edit Multiple)** — bulk edit.
- **Print Items List**
- **Store Items Mapping / Store Item Categories Mapping** — which items are stocked at which store/branch.

## 36. Assets
Fixed asset register.
- Register/View Asset Category, Asset Location, Asset.
- **Asset Register / Asset Register Entry Sheet** — formal asset register output, likely for audit.

## 37. Vehicles & Mileage
- Register/View Vehicle Type(s), Register/View Vehicles, View Vehicles List.
- **New/View Vehicle Mileage Details / View Vehicle Mileage Report** — fleet mileage logging (fuel/maintenance cost tracking for ambulances or hospital vehicles).

## 38. Money Out (Procurement / Payables)
- **New/View Purchase Order**
- **New/View Supplier Invoice**
- **View Payment Vouchers**
- **View Supplier Credit Notes**
- **New/View Supplier Quotation** — a full procure-to-pay flow: quotation → PO → invoice → payment.

## 39. Requisitions
- **New/View Store Requisition** — internal department requests for stock.
- **New/View Order Note**

## 40. Stores
- **Register/View Store** — multiple stock locations (e.g., main pharmacy store, ward sub-stores).
- **Issue Item(s) To Cost Center / View Items Issued To Cost Centers**
- **Loan Item(s) To Cost Center / View Items Loaned To Cost Centers** — temporary inter-department stock loans.
- **View Internal Transfers**

## 41. Accounting Reports
- **General Ledger** (+ inclusive of invoices yet to be finalized)
- **Trial Balance**
- **Profit & Loss** (+ inclusive of unfinalized invoices, + month comparison)
- **Balance Sheet**
- **Accounts Payable Aging Report** (+ totals only)
- **Supplier Balances Report / Supplier Ledger Report**

## 42. Inventory Reports
- **Items Issued To Cost Centers / Item Loan Balances / Internal Transfers**
- **Stock Report / Stock Re-Order Report**
- **Supplier Invoices Report / Store Requisitions Report / Purchase Orders Report / Rejected Purchase Orders Report**
- **Stock Movement Report**
- **Stock Take Report / Stock Take Yet To Be Posted Report** — physical stock count reconciliation workflow.

## 43. Send SMS
- **Send SMS (Open)** — free-form messaging.
- **Send SMS (To Clients Who Have Visited In A Given Period)** — targeted campaign by visit history (e.g., appointment reminders, follow-up prompts).
- **New/View SMS Template**

## 44. Management Reports
- **Monthly Reports / Daily Statistics** — high-level dashboards for management/executives.

## 45. Payslips, Leave Requests & Self Evaluation (Employee Self-Service)
- **View Payslips**
- **New Leave Request / View Leave Requests**
- **My Performance Reviews / My Subordinates' Performance Reviews** — a self-service portal layered on top of the HR/Payroll admin modules above.

## 46. Navigation Style
- **Preferred Navigation Style** — user-level UI preference (the screenshots show a "Default Navigation" dropdown with a Save button) letting each user choose how the sidebar menu behaves/displays.

---

## Cross-cutting architectural observations (useful for building a similar app)

1. **Multi-payer pricing model**: nearly every clinical module has parallel price lists for cash, insurance, corporate, and branch-specific rates. Your data model should treat "price" as a lookup keyed by (item/service, payer type, payer ID, branch) rather than a single flat price field.
2. **Multi-branch support**: many Administration items are explicitly duplicated "(Branch)" — the system is designed for hospital groups with multiple sites sharing one back office.
3. **Confirm/Reverse pattern**: Pharmacy, Charges, and Invoices all use a two-step "confirm then optionally reverse" pattern rather than hard deletes — good for audit trail and matches financial-system best practice.
4. **Draft → Finalize workflow** for journals and invoices ("yet to be finalized" reports appear repeatedly) — supports an accrual accounting style where draft transactions can still be reported on separately from posted ones.
5. **Kenya-specific compliance baked in**: NHIF (health insurance), NSSF (social security), Pesapal (payments), ICD-10-to-MoH disease mapping — if you're targeting a different country, these are the modules to localize first.
6. **Inventory is shared infrastructure**: Pharmacy, Physiotherapy, and general Stores all report on stock using the same report shapes (current stock, movement, reorder, batch/expiry, stock take) — suggests one generic inventory engine reused across drug and non-drug items.
7. **Self-service portal is a thin layer**: the employee-facing "Payslips, Leave Requests & Self Evaluation" module is a reduced-permission view over the same Payroll/Leave/Performance data admins manage — worth modeling as a permissions layer rather than a separate feature.
