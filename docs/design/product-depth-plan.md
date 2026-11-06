# Product depth plan — how deep each feature needs to be

**Why this exists.** The product works, but much of it is shallow: it records data and shows it, without helping the owner *understand* the business. This document says, per feature, how far it needs to go, measured against what established products offer, and in what order to get there.
**Evidence base.** (1) Structural measurements of every module (`python3 tools/depth_audit.py`, reproducible). (2) Spot checks of code and schema. (3) Published documentation of comparable products (sources at the end). (4) The HMIS sample company analysis (`docs/design/hmis-learnings.md`). Where a statement is a judgement rather than a measurement, it is labelled.

---

## 0. The short version

1. **The data is mostly there; the analysis layer is not.** Sales already carry cashier, time, discount, VAT and payment method; sale lines snapshot the cost price (so true gross profit by product is computable); returns carry reason and processor; purchases carry due date and balance. Most benchmark reports can be built from existing data.
2. **Analytics is the thinnest area:** the entire Reports module is 192 lines with 2 charts; the whole product has **4 charts**. "Browse Reports" is six buttons that each dump one raw table to CSV.
3. **There are correctness and compliance problems that come before any new feature** (§1) — the most serious: **receipts print an "ETIMS Serial: KRA-…" number that KaziFlow invents locally.**
4. **Recommended order:** fix trust problems → build the analytics foundation and the Sales/Inventory analytics pages → finance & VAT views → real eTIMS → people → vertical depth for the profiles you choose.

---

## 1. Fix first: things that are wrong, not just thin

| # | Problem (verified in code) | Why it matters | Fix |
|---|---|---|---|
| 1 | **Fake eTIMS serial.** `ETIMSService.generateSerial` returns a local counter formatted like a KRA serial (`KRA-2026-000142`), stored with `status='local'`, never sent to KRA; `ReceiptPrinter` prints it as "ETIMS Serial". | A shop owner and their customer will believe the receipt is an eTIMS invoice. Kenyan guidance and commentary say eTIMS invoicing is required for businesses since 1 January 2024 and that, from 2026, KRA checks income-tax returns against eTIMS data, so a customer's expense or VAT claim can be disallowed. **Printing a look-alike KRA number is a misrepresentation on a tax document.** | Immediately: stop printing a `KRA-` serial; print an honest "eTIMS: not transmitted" (or nothing) until real integration exists. Then do the real integration (§6, R4). *Confirm exact obligations and exemptions with KRA / an accountant.* |
| 2 | **Reports pie chart shows invented data** (Cement 40 / Steel 30 / Paints 20 / Hardware 10) whenever the real query returns nothing. | A new business sees a confident chart that is entirely fictional. | Show an empty state ("No sales in this period"). |
| 3 | **"Sales Report" export silently contains only the newest 1,000 sales** (`SELECT * … LIMIT 1000`); the six "reports" are unfiltered table dumps with no date range or totals, under descriptions promising shift reports, supplier aging, COGS analysis and performance. | Anyone who exports a year of sales gets a partial file labelled complete. | Date-ranged, totalled reports; say "first N rows" if ever capped (v11.29's report engine already does this for custom modules). |
| 4 | **Finance "Chart of Accounts" is a derived summary**, not a ledger; `accounts.balance` is never written; "Cash & Bank" is estimated from net cash flow. | Presents estimates in the shape of books. | Label honestly now; build a real ledger (§5, R3+). |
| 5 | **Invoice numbers are `COUNT(*)+1`** in a read-then-write transaction. | In LAN mode two PCs selling together can fail a sale with `SQLITE_BUSY_SNAPSHOT`. | Numbering service (atomic, retried, configurable per document type). |
| 6 | **Payment methods are a `CHECK` constraint** (`cash/mpesa/card/bank`) on `sales`. | Adding a tender (cheque, SACCO deduction, voucher) needs a schema migration. | Payment methods as master data. |
| 7 | Login screen claims "Trusted by 500+ businesses in Nairobi & Mombasa" and shows a "Sign in with Google" button that does nothing. | Unsubstantiated marketing claim; dead control. | Replace with true copy; hide or implement. |
| 8 | The AI assistant's prompt contains the default admin login and goes to a third party; `LicenseService.SIGNING_SECRET` is hardcoded and labelled "CHANGE IN PRODUCTION". | Credential disclosure; licences can be forged. | Remove from prompt; move the secret out of the jar. |

---

## 2. A depth scale

| Level | Means | Typical checklist |
|---|---|---|
| **D0** | Absent | — |
| **D1 Record** | Create / edit / list | Forms, a table, search by text. |
| **D2 Operate** | Usable day to day | Validation; statuses; filters and sorting; date-ranged lists **with totals**; per-record history; print / export; permissions on actions; nothing silently truncated. |
| **D3 Manage** | The owner can steer by it | **Analytics pages with several tabs**; period selector with *compare to previous period*; drill-down from any number to its rows; saved and scheduled reports; exception / control reports; targets and alerts. |
| **D4 Optimise** | The product advises | Forecasts, reorder suggestions from sales velocity, anomaly flags, benchmarks, statutory submission. |

**Targets.** Every module reaches **D2**. The core business modules (Sales, Inventory, Purchases, Finance, Customers, Analytics) reach **D3**. A few items reach **D4** where the value is obvious (reorder suggestions, eTIMS submission). Vertical modules stay at D2 unless the owner picks a vertical to deepen (§7, question 2).

---

## 3. Where we are (measured)

```
module           loc  tabs  charts  tables  dialogs  filters  daos  dao methods   screens
settings        1571     0       0       1       20        5     1            7   Settings, UserManagement
sales           1066     0       0       2        5        7     2           22   Sales
purchases        641     0       0       3        6       11     3           25   Purchases
dashboard        571     0       1       0        1        3     6           62   Dashboard
employees        554     0       0       3        3       16     1            7   Employees
batches          491     0       0       2        3        3     3           31   Batch
inventory        478     0       0       1        6        7     3           25   Inventory
restaurant       475     0       0       2        4        2     1           16   Restaurant
quotations       449     0       0       2        3        2     2           21   Quotation
finance          417     0       1       1        2        5     2           20   Finance
deni             406     0       0       1        2        1     1           10   Deni
appointments     402     0       0       3        2        5     2           17   Appointment
sacco            384     5       0       5        5        3     1           18   Sacco
patients         370    10       0       3        3        3     1           14   Patient
gym              356     0       0       3        3        3     1           14   Gym
payroll          355     0       0       2        3        2     1           15   Payroll
workshop         265     6       0       3        3        1     2           21   Workshop
help             246     0       0       0        0        0     0            0   Help
hotel            231     0       0       2        5        1     1           12   Hotel
fuel_station     212     0       0       3        4        5     1           12   FuelStation
delivery         192     4       0       2        2        0     1           13   Delivery
reports          192     0       2       0        0        2     3           36   Reports
canteen          155     0       0       2        3        0     1            9   SchoolCanteen
laundry          154     0       0       1        1        1     1            7   Laundry
TOTAL          10633    25       4      47       89       88
```
*Columns: loc = non-blank lines of screen code; tabs = TabPane tabs (undercounts screens that build their own tab bar from buttons, e.g. Finance); charts = Line/Bar/Pie/Area chart constructors; dialogs = Dialog/Alert flows; filters = ComboBox / DatePicker / search fields; dao methods = query methods on the data classes the screen uses.*

Reading it (judgement, labelled): **no module reaches D3.** The largest screen is Settings (1,571 lines, mostly dialogs). Sales (1,066) and Purchases (641) are the deepest operational modules and are D2-ish. **Reports (192 lines) is smaller than Help (246) and about the size of Laundry (154)**, which is the clearest sign of under-investment relative to its importance. Verticals are 150–480 lines each: D1 with a few D2 features.

---

## 4. Target depth per module

*"Today" is assessed from the measurements above plus spot checks; confirm screen by screen before building. Priority: **P1** every customer needs it; **P2** most customers; **P3** only for the matching business type.*

| Module | Today | Target | What closes the gap | Priority |
|---|---|---|---|---|
| **Reports / Analytics** | D1 | **D3** | The multi-page analytics suite in §5 | **P1** |
| **Dashboard** | D1–D2 | **D3** | Period selector + compare; 8–10 KPIs; trend; drill-through; alerts strip (low stock, overdue receivables, unreconciled M-Pesa) | **P1** |
| **Sales / POS** | D2 | **D3** | Per-sale and per-shift reconciliation; discounts / voids / refunds audit; tender master data; customer-linked sales; held sales; price tiers; per-line tax class (needed for eTIMS) | **P1** |
| **Inventory** | D2 | **D3** | Valuation, ageing / dead stock, ABC, turnover, reorder suggestions (D4); units & variants; barcode labels; photos *(done in v11.31)* | **P1** |
| **Purchases** | D2 | **D3** | Payables ageing; supplier invoice number + eTIMS number capture; supplier price history; goods-received matching | **P1** |
| **Finance** | D1–D2 | **D3** | Real ledger (journals, posting rules), P&L with comparisons, balance sheet, cash flow, bank accounts + reconciliation, petty cash; VAT summary | **P1** |
| **Compliance (new)** | D0 | **D3–D4** | Real eTIMS submission; VAT3 helper; PAYE / SHIF / NSSF / Housing Levy remittance summaries; "expenses lacking an eTIMS invoice" | **P1** |
| **Customers / Deni** | D1–D2 | **D3** | Receivables ageing; statements; credit limits; reminders (SMS exists); repeat-purchase view | P1 |
| **Quotations** | D2 | D2 | Convert-to-sale audit; expiry; templates | P2 |
| **Delivery** | D1–D2 | D2 | Status workflow, rider assignment, delivery report | P2 |
| **Employees** | D2 | D2–D3 | Documents, contracts, org chart; self-service view (needs row-level permissions) | P2 |
| **Payroll** | D2 | D3 | Earnings / benefits / overtime / advances / loans as data; payslips; statutory returns | P2 |
| **Leave (inside Employees)** | D1 | D3 | Leave types, periods, holidays, entitlements, balances, approvals (HMIS shows how much this is) | P2 |
| **Batches / Expiry** | D2 | D2 | Expiry alerts feeding the dashboard | P2 |
| **Settings** | D2 | D2 | Most of the work here is the correctness fixes in §1 | P2 |
| **Restaurant, Workshop, Gym, Laundry, Hotel, Fuel station, SACCO, Canteen, Appointments, Patients** | D1 (some D2) | D2 each; **D3 for the owner's chosen verticals** | Each needs its own review; the custom-module engine (views, subforms, reports) can carry several of them | P3 |
| **Help** | D1 | D2 | Context help per screen; keyboard shortcuts list | P3 |

---

## 5. The analytics suite (what "several pages with several tabs" means)

**Cross-cutting rules** (these are what make it feel professional, and they are cheaper to build once than per report):
- **Period control** on every page: Today · Yesterday · This week · This month · Last month · This quarter · This year · Custom; plus **"compare with previous period"** (previous period and same period last year).
- **Branch filter** and **drill-down**: click any number to see the rows behind it.
- **Consistent definitions**, printed on the report: *Net sales = subtotal − discounts − returns, excluding VAT. Gross profit = net sales − cost of goods sold, using the cost price recorded on the sale line.*
- **Export** (CSV) and **printable page** on every report; **saved views** (the v11.29 engine); later, scheduled delivery.
- **Honest empty states** — never sample data.
- **Performance budget:** a report over 100,000 sales opens in under a second. (Measured on the v11.29 engine: grouped report over 20,000 records ≈ 200 ms.)

Data check legend: ✅ the data exists today · ⚠ needs a small new column or table · ❌ needs a new feature.

### Page 1 — Overview (executive dashboard)
Revenue · Gross profit and margin % · Orders · Average basket · Units sold · Discounts given · Refunds % · Cash collected · Receivables outstanding · Payables due · Stock value · Low-stock count — each with change vs previous period; revenue trend line (this vs previous period); top 5 products; payment mix; alert strip. **Data:** ✅ (all from `sales`, `sale_items.cost_price`, `sale_returns`, `purchases`, `products`).

### Page 2 — Sales analytics (tabs)
| Tab | Shows | Data |
|---|---|---|
| Summary | Sales over time (day / week / month), day-of-week × hour heat map | ✅ `sales.created_at` |
| Products | Top and bottom sellers; qty, revenue, cost, profit, margin %; trend per product | ✅ `sale_items` + `cost_price` |
| Categories | Same by category; share of revenue | ✅ via `products.category_id` → `categories.name` |
| Customers | Top customers, new vs returning, share of credit sales | ✅ `sales.customer_id` |
| Staff | Per cashier: sales count and value, average basket, discounts given, refunds processed, voids | ✅ `sales.served_by`, `sale_returns.processed_by` |
| Payments | Tender mix and % of revenue; M-Pesa vs cash; M-Pesa reference checks | ✅ `sales.payment_method`, `mpesa_ref` |
| Discounts & refunds | Audit list: who, what, when, why — the fraud-control report | ✅ `discount_amount`, `sale_returns.reason` |
| Shifts | Takings per shift: expected vs counted vs variance | ⚠ needs a shift / cash-up record |

### Page 3 — Inventory analytics (tabs)
Stock summary · **Valuation** (cost and retail value, by category) · **Movement** (in / out / adjustments per product and period) · **Ageing & dead stock** (buckets 0–30 / 31–60 / 61–90 / 90+ days since last sale) · **ABC** (A/B/C by revenue contribution) · **Reorder** (below minimum now; *suggested quantity from sales velocity* — D4) · **Expiry** (batches) · **Shrinkage** (stock-take variances). **Data:** ✅ `products`, `stock_movements`, `batches`, `stock_takes`; ageing needs last-sale date per product, derivable from `sale_items` ✅.

### Page 4 — Finance (tabs)
**Profit & loss** (this vs previous month, vs last year) · **Expenses** (by category, trend) · **Cash flow** (in / out by method) · **Receivables ageing** (Deni; current / 30 / 60 / 90+) · **Payables ageing** (by `purchases.due_date`) · **VAT summary** (output vs input VAT by period) · later **Trial balance / balance sheet** once a real ledger exists. **Data:** ✅ for all except VAT-by-tax-class (⚠ needs a tax class per product/line) and a true balance sheet (❌ needs the ledger, `docs/design/hmis-learnings.md` §5 item 6).

### Page 5 — Customers
Top customers · Frequency and recency (a simple RFM view) · Credit exposure vs limit · Statements. **Data:** ✅.

### Page 6 — People
Attendance summary · Payroll cost trend · Overtime · Leave balances. **Data:** ✅ for attendance and payroll cost; ❌ for leave balances (needs leave types and entitlements).

### Page 7 — Compliance (Kenya)
eTIMS status (transmitted / pending / failed) · VAT3 helper · PAYE / SHIF / NSSF / Housing Levy remittance summaries · **Expenses lacking an eTIMS invoice**. **Data:** payroll statutory amounts ✅; eTIMS status ❌ (needs the real integration); expenses-without-invoice ⚠ (needs an eTIMS invoice number on purchases and expenses).

### Page 8 — Report builder
The point-and-click views / reports engine built for custom modules (v11.29), extended to the built-in entities (sales, products, customers, expenses), so owners can make their own reports.

**Genuine data gaps** (everything else already exists): tax class per product and per sale line; eTIMS invoice number and supplier invoice number on purchases and expenses; VAT split on expenses; a shift / cash-up record; leave types and entitlements.

---

## 6. Suggested sequence

| Release | Theme | Contents | Size |
|---|---|---|---|
| **R1** | **Trust — DONE (v11.33–v11.43)** | §1 items 1–3 and 5–7 (honest eTIMS label, no fake chart data, real exports, numbering service, tender master data, truthful login copy) | S–M |
| **R2** | **See your business** | Analytics foundation (shared aggregation service, period / compare / branch controls, drill-down, empty states) + Overview + Sales analytics + Inventory analytics | L |
| **R3** | **Money** | Finance tabs, receivables / payables ageing, VAT summary; start of the real ledger | L–XL |
| **R4** | **Compliance** | Tax classes; real eTIMS integration (an **external dependency**: it needs KRA onboarding / certification, so start the paperwork early); expenses-without-invoice; remittance summaries | L, external |
| **R5** | **People** | Leave depth, payroll items, self-service (needs row-level permissions) | L |
| **R6** | **Verticals** | D3 for the profiles you choose | per vertical |

R1 can start immediately and is independent of everything else. R2 is the answer to "analytics is so limited" and does not need any new data.

---

## 7. Decisions needed from the owner

1. **eTIMS (§1 item 1):** may I make the receipt honest now (stop printing a `KRA-` serial)? And who will handle KRA vendor onboarding for the real integration?
2. **Which business types matter most** (retail, hardware, pharmacy, restaurant, hospital…)? That decides which verticals reach D3 first.
3. **Ledger:** will KaziFlow produce statutory books, or sit beside an accountant's system? That decides how strict the ledger must be.
4. **Sequence:** R1 → R2 as above, or is something more urgent?

---

## Sources
- Zoho Inventory — Inventory reports (summary, FIFO valuation, aging with configurable intervals, ABC classification, turnover, batch and adjustment reports): `zoho.com/inventory/help/reports/inventory-reports.html`; slow-moving stock: `zoho.com/inventory/kb/reports/slow-moving-stock`.
- Lightspeed (X-Series reporting comparison; K-Series and ShopKeep support: sales by employee), Square (Analytics and Reporting), PayPal Zettle (POS reports): sales by product / category / employee / hour / tender, refunds and discounts, reconciliation, shifts, saved and scheduled reports — `product-info.lightspeedhq.com`, `shopkeep-support.lightspeedhq.com`, `squareup.com/help`, `paypal.com/us/smarthelp`.
- Accounting report sets (P&L with comparisons, balance sheet, cash flow, trial balance, general ledger, AR/AP ageing, tax and expense reports): Odoo accounting reports overview (`cybrosys.com`), QuickBooks Online report lists, Sage and Zoho Books help.
- Kenya eTIMS: KRA *eTIMS Taxpayer Guidelines 2024* (`kra.go.ke`), KPMG Kenya eTIMS note (2026), Business Daily commentary, and practitioner guides. **These are secondary summaries of a moving regulation — verify obligations, exemptions and dates with KRA or an accountant before relying on them.**
- Structural measurements: `tools/depth_audit.py`. HMIS analysis: `docs/design/hmis-learnings.md`.
