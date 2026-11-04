# KaziFlow ERP — Project Guide
### Foundational → Expert. For future developers and other AI agents picking this up.

**Read this first if you are new to this codebase — human or AI.** It exists so you don't have to reconstruct months of context from scratch, and so you don't repeat mistakes already made and fixed once.

---

## 0. The One Rule That Governs Every Design Decision Here

> **Everything should be as dynamic as possible. The system adapts to the human, not the other way around.**

This is not a vague aspiration — it is a concrete, recurring instruction from the project owner, and it should be the first lens applied to any new feature or fix:

- If a number could plausibly change (a tax rate, a fee, a threshold), it must be **read from the database**, not hardcoded — even if the current value is "obviously" correct today. VAT, payroll statutory rates, delivery pricing, and currency exchange rates are already built this way (see §4). New features should follow the same pattern from day one.
- If a UI concern applies to many places (e.g. "dialogs should be resizable"), the fix belongs in **one shared, reusable component**, not a mechanical sweep across every call site. See `DialogUtils` (§4.5) as the concrete example and the template to follow.
- If a business could plausibly need a different set of modules, fields, roles, or workflows than the default — **let them configure it**, don't hardcode an assumption about "how businesses work." This is why the Industry Profile system, module toggling, and (planned) custom module builder and dynamic roles system all exist — see §7 and §9.

When in doubt: **would this force every user to be identical to build correctly, or does it let each business be itself?** Favor the latter.

---

## 1. What This Is

KaziFlow ERP is an offline-first JavaFX desktop ERP for Kenyan SMEs — inventory, POS, purchases, HR/payroll, finance, and 20+ industry-specific verticals (pharmacy, restaurant, hotel, SACCO, and more), all built on one shared codebase that adapts per business via the **Industry Profile** system.

- **Language/runtime:** Java 21, JavaFX 21
- **Build:** Maven (`mvn javafx:run` to launch, `mvn clean compile` to just check compilation)
- **Database:** SQLite, single file, offline-first, WAL mode
- **Default login:** `admin@kaziflow.co.ke` / `admin123`
- **Repo:** https://github.com/Kuthita-Richard/KaziFlow-Java (push access currently blocked by a GitHub fine-grained token issue as of this writing — see §11)

---

## 2. Architecture Overview

### 2.1 Package structure
```
com.kaziflow.dao          — data access (one class per entity/module)
com.kaziflow.database     — DatabaseManager: schema, migrations, connection handling
com.kaziflow.models       — plain data classes
com.kaziflow.modules      — IndustryProfile enum + ModuleRegistry (the core adaptability engine)
com.kaziflow.network      — multi-computer support (transparent JDBC-over-TCP proxy, §5)
com.kaziflow.services     — cross-cutting: M-Pesa, ETIMS, AI, SMS, email, currency, payroll tax, VAT, audit
com.kaziflow.utils        — shared utilities: session, scene navigation, formatting, i18n, DialogUtils
com.kaziflow.views        — JavaFX screens, one class per screen
```

### 2.2 The Industry Profile + Module system (the core idea)

One codebase, many business types. `IndustryProfile` is an enum (22 entries: General Retail, Pharmacy, Restaurant, Hotel, SACCO, etc.) — each defines a display name, icon, POS layout, a **list of module IDs** to activate, and **terminology overrides** (e.g. a printing shop calls a "sale" a "Job Order"). `ModuleRegistry` reads the active profile and builds the sidebar/navigation from whichever modules that profile enables.

**As of v11.15**, module activation can also be **overridden per-installation**, independent of the profile default — see Settings → System Modules. This exists specifically so a business that spans more than one vertical (e.g. a pharmacy that also runs a small clinic) isn't stuck with one profile's fixed list.

**Known limitation (important, not yet solved):** one installation = one shared module set. Every "branch" tracked via the Branch Management feature shares the *same* active modules — there's no way to make Branch A pharmacy-only and Branch B nursing-home-only within a single installation. See §9 for why this matters and what real-world evidence supports fixing it.

### 2.3 Database

Single SQLite file (`~/KaziFlowERP/kaziflow.db` by default). ~57+ tables. **Every connection must run through `DatabaseManager.getConnection()` / `getLocalConnection()`** — these apply `PRAGMA foreign_keys = ON` and `PRAGMA busy_timeout` on every single connection, because SQLite pragmas are per-connection, not persisted in the file (this was a real, significant bug — foreign key enforcement was silently off for the entire life of this project until v11.12; see `docs/changelog/v11.12`).

Migrations are additive and idempotent: `ALTER TABLE x ADD COLUMN y` wrapped in a try/catch that swallows the "duplicate column" error on repeat runs. Never drop or rename existing columns — old data must keep working.

---

## 3. Multi-Computer Support (§ for the curious/expert reader)

Three modes, chosen in Settings → Network: **Standalone** (default), **Server/Client** (one PC hosts, others connect over LAN), and **Shared Database File** (all PCs point at one file on a network share — simpler, but SQLite genuinely isn't built for concurrent multi-writer access this way, and the UI says so explicitly).

The Server/Client implementation (`com.kaziflow.network`) is a **transparent JDBC-over-TCP proxy** — `RemoteConnection`/`RemoteStatement`/`RemotePreparedStatement`/`RemoteResultSet` implement just enough of `java.sql.*` that every existing DAO works completely unmodified, unaware whether it's talking to a local file or another PC. This was the one part of the whole project actually **runtime-verified** against a real SQLite database (downloaded live from GitHub Releases) rather than just reviewed — see `docs/changelog/v11.7` for the full test results (transactions, rollback, NULL handling, error recovery all confirmed working for real).

---

## 4. The Dynamic-Configuration Pattern (study this before adding any new config)

Three services all follow the exact same template — copy this pattern for anything new:

```java
public class XConfig {
    private static XConfig instance;
    public static synchronized XConfig getInstance() { ... }
    private XConfig() { reload(); }
    public void reload() { /* SELECT from settings WHERE key LIKE 'x_%' */ }
    public void save(...) { /* INSERT OR REPLACE INTO settings ...; reload(); */ }
    public double getSomething() { return ...; }
}
```

- **`CurrencyService`** (v11.3) — KES stays the permanent storage currency; display currency (USD/EUR) and exchange rates are configurable, converted only at display time.
- **`PayrollTaxConfig`** (v11.18) — every Kenya statutory payroll rate (NSSF thresholds, SHIF rate, Housing Levy, NITA, personal relief, the full PAYE band table) is editable in Settings → Payroll Tax Settings, because these rates have changed **three times in under two years** — hardcoding them just guarantees another code update every time KRA/NSSF/SHIF revise something. Includes a togglable setting for one point that's genuinely disputed in current guidance (whether Housing Levy reduces PAYE's taxable income).
- **`VATConfig`** (v11.20) — same treatment for VAT. Worth knowing: this closed a real gap where a *working-looking* Settings field ("VAT Rate %", with UI text literally claiming "Changing this affects all new receipts") saved a value that **nothing except the receipt's display text ever actually read**. Ten separate hardcoded `0.16` calculation sites across the app were found and fixed. **Lesson: a settings field that saves successfully is not proof the value is actually consulted anywhere — always trace the read side too.**

### 4.5 `DialogUtils` — the same principle applied to UI, not just numbers

v11.21: ~76 dialogs across 43 files each hand-built their own title/style/width setup from scratch. When "dialogs should be resizable" came up, fixing that as 76 individual edits would itself have violated this project's own stated principle. `com.kaziflow.utils.DialogUtils.styledDialog(...)` is now the standard way to build a new dialog; `makeResizable(existingDialog)` retrofits old ones. **New dialogs should use `styledDialog()`, not `new Dialog<>()` directly.**

### 4.6 `PasswordFieldUtils` — show/hide eye toggle (v11.26)

Any `PasswordField` gets a show/hide toggle via `PasswordFieldUtils.withToggle(pf)` — build the field as usual, put the **returned node** in the layout, keep reading `pf.getText()`. Don't hand-roll a per-screen reveal button. Use `PasswordFieldUtils.requestFocus(pf)` (not `pf.requestFocus()`) to move focus programmatically, since the hidden half of the pair can't take focus. **New password fields should use this.**

### 4.7 Permissions — Positions, `PermissionService`, `PermissionGuard` (v11.25–v11.27)

- **Check access through ONE generic engine:** `PermissionService.can(moduleId, Action)` and `canPerform(actionKey)`. Never add a method per feature. Navigation is gated centrally in `SceneManager.navigateTo()` (via `ModuleRegistry.isAccessible`); controls are greyed with `PermissionGuard.requireAction/requireModule`.
- **New special action?** Register a row (`SpecialPermissions.register`) — the Positions editor shows a checkbox automatically — and **wire at least one real check**; a catalog entry nothing enforces is the "settings UI that saves data nothing reads" bug.
- **New module?** Nothing to do for permissions: it appears in the Positions editor from `ModuleRegistry.getAllAvailableModules()`. New modules are default-deny; Administrator always has access.
- **All permission edits go through `PermissionAdminService`** (one transaction + "an active administrator must remain" post-condition; bumps `permissions_epoch`, which `PermissionService` re-validates every ~3 s so edits reach logged-in users and other PCs).
- Sidebar is filtered at READ time in `ModuleRegistry` (not in `loadModules()`, which runs once before anyone logs in).

### 4.8 Custom modules — admin-built departments (v11.28)

- A department the product doesn't ship with is DATA, not code: rows in `custom_modules` / `custom_entities` / `custom_fields`, records as validated JSON in `custom_records`. **Do not create a new `Module` class or table for a new department** — `CustomModule` + `CustomModuleView` render any of them. The module id is `x_<key>`, so permissions work with no extra step.
- Field keys are immutable (`f_<id>`); never derive a key from a label. Fields are archived, never dropped; type/link target can't change after creation.
- Calculations go through `FormulaEngine` only (own parser, whitelisted functions). **Never** `eval`, a script engine, or user text concatenated into SQL.
- The only DDL this feature issues is an optional expression `CREATE INDEX` named from numeric ids. SQLite rule learned the hard way: **drop an index before dropping the column it references**.
- **Never concatenate user text into SQL for views/reports.** `CustomQueryService` addresses fields only by validated key, passes JSON paths and every filter value as bound parameters, and embeds only the validated `f_<digits>` key as a literal in ORDER BY. New query features must do the same. Calculated fields can't be filtered/sorted/grouped (they are evaluated in Java).
- **Per-row work in a list is a performance bug.** Read an entity's fields ONCE per query (`ViewContext`), cache link-target existence per batch, and run the schema check once per process — measured: this was 20× on every list and 20× on bulk create.
- Any user-visible text going into generated HTML must go through `CustomReportHtml.esc`; any cell going into CSV through `CustomCsv.quote` (defuses `=`/`+`/`@` formula injection).
- In WAL mode a transaction that reads-then-writes can fail instantly with `SQLITE_BUSY_SNAPSHOT` (the busy timeout doesn't apply) when another connection commits in between — the audit writer does, constantly. Wrap such transactions in a bounded retry (see `PermissionAdminService.guarded`).

### 4.9 The HMIS sample company — what it teaches (v11.30)

`docs/reference/HMIS_Feature_Reference.md` describes a real Kenyan hospital system from 47 sidebar screenshots; `docs/reference/hmis_menu_catalog.tsv` holds all 405 menu entries as data, and `tools/hmis_catalog_stats.py` reproduces the numbers. Read `docs/design/hmis-learnings.md` before planning any "system module": it measures the menu (58% reports + lists; 41% mechanical register/view CRUD; 75% not hospital-specific; 8% generic workflow states), compares it with KaziFlow area by area (verified against the schema), and gives a prioritised plan (numbering service → lifecycle engine → links to built-in records → starter packs → HR depth → accounting core → scoped permissions → sidebar groups).

### 4.10 Brand identity and product pictures (v11.31)

- **Names are data.** Tihems (the group) → Tihems Software (the software department that owns the product) → the product. Every name lives in `src/main/resources/brand/brand.properties` and is read through `Brand` (JavaFX-free, so receipts/email/exports/AI prompts can use it). **Never type the product or company name in Java**: `BrandGuardTest` fails the build, naming file and line. An installation can override any key in `<data folder>/brand-override.properties` (white-label) without rebuilding. The product name "KaziFlow" is the *interim* name.
- **One logo set for all three levels** (`/brand/*.png`, `installer/icons/*`), rebuilt from the originals in `docs/brand/source/` by `python3 tools/build_brand_assets.py` (the supplied JPEGs were converted to transparent PNGs and the app icon rebuilt crisply from the 1254 px master). Use `BrandAssets` for images; a global hook gives every window (dialogs, alerts, report windows) the icon automatically.
- **Data location is NOT brand.** `AppPaths` holds the data-folder and database file names. They stay `KaziFlowERP` / `kaziflow.db` forever (renaming would orphan every existing install). Likewise the licence signing secret and the update User-Agent — the guard allows exactly these three and says why.
- **Customer-facing text speaks for the BUSINESS** (`BusinessInfo.name()`: debtor SMS sign-off, receipt heading), not the software vendor.
- **Product pictures** live in `product_images` as base64 TEXT (the LAN protocol cannot carry BLOBs), two sizes (160 px square thumb, 640 px picture), written only through `ImageProcessor` (validates the file really decodes, refuses > 50 megapixels / > 20 MB *before* decoding, applies EXIF rotation, flattens transparency, re-encodes). Screens read thumbnails through `ProductImages` (LRU cache + batched `prefetch` — one query for a whole grid). Use `ProductImagePicker` for any new "photo" field. `products.image_path` is an old dead column (nothing reads or writes it).

### 4.11 Product depth — read before building or "improving" any module (v11.32)

`docs/design/product-depth-plan.md` defines a depth scale (D1 record → D2 operate → D3 manage → D4 optimise), measures where every module stands (`python3 tools/depth_audit.py`), sets a target per module, specifies the analytics suite page by page (with a check of which data already exists), and sequences the work (R1 trust → R2 analytics → R3 money → R4 compliance → R5 people → R6 verticals). Headline facts: the whole product has 4 charts; Reports is 192 lines; the data analytics needs mostly already exists; and §1 of that plan lists correctness/compliance problems (notably the invented "ETIMS Serial: KRA-…" printed on receipts) that come before any new feature.

### 4.12 Trust, periods and exports (v11.33–v11.35)

- **A screen shows a real number or says there is none.** Never a sample, fallback or hardcoded caption: no `x > 0 ? x : 4200000`, no "+12% vs last month" that nothing computes, no month name typed in code. Empty → "—" or an honest empty-state message. (The sweep is complete as of v11.39: every `statCard`/`sCard` value in the views is computed; re-run the grep in `docs/changelog/v11.39…` after adding screens.)
- **eTIMS:** local numbers are `LOCAL-<year>-<seq>`. Only `ETIMSService.getTransmittedSerial(saleId)` may produce a KRA number, and only when KRA received that invoice; `isConnected()` is `false` until onboarding exists. Receipts print an eTIMS line only from `getTransmittedSerial`.
- **`utils/DateRange`** is the one period type: a record `(from, to)` with `ALL`, `Preset` + `of(preset, today)` (pure, testable on fixed dates), `previousPeriod()`, `sameLastYear()`, `label()`, `fileSuffix()`, and `whereClause(column)` + `bind(ps, index)` so SQL never builds dates by string concatenation. Analytics pages (R2) embed the same type, through `utils/DateRangePicker` (inline control; day-first dates; `getRange()` is null while invalid). `DateRangeDialog.ask` is the "which period?" dialog; `ReportExportService.exportCSV(type)` calls it for period-based reports (cancel exports nothing).
- **Export rules** (`ReportExportService`; `exportCSV(type)` = all time, `exportCSV(type, DateRange)`; `isPeriodBased(type)` says which reports take a period): no row cap; every TOTAL row is computed from the rows written to the file; UTF-8 with BOM; `Locale.ROOT` numbers; CSV-injection defence on text cells; the filename carries the period; a derived report (Finance Summary) prints its definitions and any data-quality warning; **every column an export reads must exist in the schema** (the Purchases export had failed since it was written because it read `tax_amount`; the column is `vat_amount`).
- **Guards:** `TrustGuardTest` scans source string literals for phrases that once made the product say something false (fake serial, customer-count claim, Google button, sample pie slices, invented expense split, this-week revenue under a Total label) — add a phrase here when you fix a new one. `BrandGuardTest` does the same for names. Behaviour checks: `tools/checks/EtimsHonestyCheck`, `ReportExportCheck`, `TrustFixesUiCheck` (renders login and Reports), `InventedFiguresCheck` (money screens) and `PeopleFiguresCheck` (people and purchases screens), each rendered empty and seeded; `DateRangePickerCheck` (periods, picker, dialog, export flow), `ReportsViewCheck` (period-driven KPIs, trend chart, exports). All were mutation-tested. Demo/sample data never enters a customer database by itself: only `DemoDataService` (opt-in) may create it; `EmptyDatabaseSmokeCheck` builds every screen on an empty database, `DemoCompanyCheck` guards load/remove. Reusable pieces: `utils/TopShare` (top entry and its share), `services/ExpenseStats` (expense card figures and this month's breakdown), `DateRange.monthLabel`.

### What's already dynamic vs. what's still a hardcoded default (backlog)
- ✅ Currency, Payroll tax (all statutory rates), VAT, Delivery pricing (per-km + named overrides), which modules are active per installation
- ❌ **Loyalty points earn-rate** — hardcoded "1 point per KES 100 spent" in `SalesView`. Same treatment as VAT/payroll would apply here; flagged, not yet built.
- ❌ **User roles/permissions** — currently a fixed admin/user model. See §7 — this is the next big one, and there's a specific direction requested for it (Odoo-style dynamic roles).

---

## 5. Compliance-Critical Areas — Handle With Real Care

Payroll and VAT calculations are not just code correctness issues — they're real money and real tax filings for whoever uses this. Two hard lessons from this project's history:

1. **Statutory rates go stale fast.** NHIF → SHIF (Oct 2024), NSSF thresholds jumped again (Feb 2026), Housing Levy is mandatory since Jul 2023 but was missing from payroll entirely until v11.18. If you're touching payroll or VAT, **search for the current rate before assuming the existing code is right** — it may already be a year out of date by the time you read this.
2. **When sources genuinely disagree** (e.g. whether Housing Levy reduces PAYE's taxable income — found conflicting claims across multiple current sources), **don't silently pick a side**. Implement the more defensible interpretation, expose it as a togglable setting, and say plainly in a comment that it's disputed. Never present manufactured confidence on a compliance question you're not certain about.
3. **Fabricated financial data is a real, serious bug class, not a cosmetic one.** `FinanceView`'s "Chart of Accounts" was found displaying entirely made-up numbers (e.g. "Cash & Bank" = revenue × 0.45, an arbitrary multiplier with no basis) dressed up as real balance-sheet data, and VAT was double-counted in **four separate places** across the app, including a dedicated VAT Summary report a business might actually file from. Always ask: *is this number derived from something real, or did I just make it look plausible?*

---

## 6. Verification Discipline — Read This Before Editing Anything

**This sandbox environment has no working JavaFX compiler**, and at various points in this project's history, no compiler at all. This has real, demonstrated consequences:

- A real `mvn javafx:run` build (run by the project owner, not this environment) has caught genuine compile errors that slipped through pure code review multiple times: a `Dialog<Void>` that should have been `Dialog<ButtonType>`, a missing `List`/`ArrayList` import, a variable accidentally deleted during an edit.
- **A `str_replace`/find-and-replace edit can silently delete an adjacent line even when the total brace/paren count stays balanced.** This actually happened once (see `docs/changelog/v11.19`) — a `TextField searchF` declaration was accidentally dropped while inserting an unrelated fix a few lines above it, and the balance-checking script used throughout this project's history could not and cannot detect this class of mistake, because deleting one line changes nothing about total counts.
- **UPDATE (v11.26): a real JavaFX compile IS possible in this sandbox.** Ubuntu's apt mirrors are reachable even though Maven Central is not, and they package OpenJFX (v11), gson, jbcrypt and okhttp. Run `tools/sandbox-verify.sh` — it bootstraps everything from scratch and compiles the whole project (a scratch copy with a tiny okhttp-3-vs-4 shim; real source untouched). With `xvfb` installed, JavaFX code can also be run and screenshotted (`Scene.snapshot`), see `tools/checks/`. **Do this first in any session; it is far more reliable than review.** Caveat: OpenJFX 11 ≠ 21, so the owner's `mvn javafx:run` remains the final word.
- **Practical mitigation used throughout this project, recommended for anyone continuing it:**
  1. After any edit, **re-view the actual resulting file content** around the change — don't just trust that the edit "succeeded."
  2. Prefer **pure additions** (inserting a new line without touching existing ones) over **replacements** when applying the same fix across many files — see how the `DialogUtils` rollout (v11.21) was deliberately done this way specifically to avoid repeating the v11.19 mistake.
  3. Where genuinely possible, get a real compiler. This project's `com.kaziflow.network` and `com.kaziflow.database` packages use *only* built-in JDK classes (no JavaFX, no third-party deps) — they were successfully compiled and even **runtime-tested** against a real downloaded SQLite driver (see `docs/changelog/v11.7`). If you're working in a similarly constrained sandbox, isolate and compile whatever doesn't depend on JavaFX; it's much better than nothing.
  4. **Try installing a real JDK if one isn't present** — in the sandbox this project was built in, `apt-get install openjdk-21-jdk-headless` worked even though Maven Central itself was blocked, because Ubuntu's own package mirrors were reachable. Worth trying before assuming no compiler is available at all.
  5. If you have no compiler for JavaFX code, the single highest-value check available is: **for every class/method reference you add, actually open the target file and confirm the exact name, signature, and field type** — most of this project's real bugs were simple mismatches (wrong generic type, undeclared variable, wrong import) that a five-second cross-check would have caught.

**The sandbox itself is not durable.** This container has reset mid-task multiple times, wiping the entire working directory including any un-pushed git history. The only thing that has reliably survived a reset is `/mnt/user-data/outputs/` — the folder where delivered zips land. **Practical rule adopted: commit and package a zip after every meaningful unit of work, don't batch multiple fixes before saving a checkpoint.** If you're an AI agent reading this after a reset: check `/mnt/user-data/outputs/` for the most recent zip before assuming work is lost, and ask the user to re-share the last zip they have if it isn't there.

**UI checks need `/tmp/pt`.** The `tools/checks/*UiCheck` suites write screenshots there; after a sandbox reset run `mkdir -p /tmp/pt` first, or `BrandingCheck` throws on its first screenshot and hangs (the JavaFX thread keeps the JVM alive). Run every suite under `timeout`, and run `xvfb-run -a` for the UI ones.

---

## 7. Two Big Features Requested But Not Yet Built

These are real, current requests from the project owner — not this document's own ideas. Both need real design thought before building, not a rushed first pass.

### 7.1 Dynamic roles & permissions (replacing fixed "admin/super admin" tiers)

> **STATUS (v11.25–v11.27): BUILT.** Positions & Access editor, per-user multi-Position assignment, enforcement (sidebar, `navigateTo`, special actions), seeding/migration with break-glass self-heal, lockout safety. See `docs/design/dynamic-roles-and-permissions.md` (implementation status at the bottom) and §4.7 below.

Explicit direction: rather than hardcoded admin/super-admin roles, **access level, permissions, and priority should be set dynamically** — when a user is assigned a position, they (or an admin) choose/are assigned exactly the modules and permissions that position needs, rather than the system assuming a fixed hierarchy.

**Explicitly requested: research how Odoo implements this**, since Odoo is a mature, widely-used open-source ERP with exactly this kind of flexible, per-user/per-group access model (Odoo uses "Groups" — arbitrary, composable permission sets assigned per user per app/module, rather than a fixed role ladder). This research has not yet been done as of this writing — **do this before designing KaziFlow's version**, since re-deriving the same design from scratch without looking at a proven model would be wasted effort.

### 7.2 Custom module builder ("admin-defined modules")

> **STATUS (v11.29): BUILT** — engine, generic runtime screen, builder UI (Settings → Custom Modules), plus validation rules, saved views & reports, subforms, CSV import/export and a printable report page. See `docs/design/custom-modules.md` and §4.8 below. Starter packs, built-in-entity links and entity-level permissions remain.

Direct request, paraphrased: *"we need a place where modules can be written/created/defined — if a company adds a department not in the module packages provided by the system, they can define their own: name it, define what data it holds, and calculations if needed."*

This is, honestly, a mini low-code/no-code layer — a significant architectural undertaking, not a quick feature. It needs real design work before any code, likely covering:
- A schema for admin-defined "custom modules" (name, fields with types, and simple calculation/formula support)
- How custom modules integrate with `ModuleRegistry`'s existing module-activation system
- How a custom module's data gets a real table (dynamic schema generation?) vs. a flexible generic storage model (EAV-style key/value, JSON column, etc.) — this is the central technical decision and has real tradeoffs either way
- How "calculations if needed" are expressed safely without turning this into an arbitrary-code-execution feature

**Not started. Needs a dedicated design discussion with the project owner before building anything** — this is exactly the kind of big, foundational decision that shouldn't be guessed at.

---

## 8. Real-World Reference Material (added August 2026)

The project owner shared two things from a real Kenyan healthcare group ("Unshur Group" / "Reliable Healthcare Services"): an internal memo (financial/clinical reporting requirements) and 47 screenshots of a live production HMIS covering its full navigation menu (46 distinct modules, from `Screenshot 2026-08-20 181215` to `182958`). A separate summary document (`HMIS_Feature_Reference.md`) catalogued all 46 modules from those screenshots. **This section captures the actual intent behind sharing that material — the architectural gaps it exposes — not a feature checklist to build one-for-one.**

### 8.1 The real finding: multi-payer, multi-branch pricing is a first-class data model there, and isn't here

Nearly every module in the reference prices the same item differently by **payer type** (cash / insurance scheme / corporate account) *and* by **branch**. The Unshur memo independently confirms this is real, not incidental — it names real Kenyan insurance schemes (TSC, Usalama, UHC, FFS-Others, M-Tiba, CIC, Britam, KPLC, APA) that a single hospital's daily reporting must break patient statistics down by.

**KaziFlow today: one flat price per product, no payer-type concept at all.** This is the single most consequential structural gap the reference material exposes — bigger than any individual missing module. Any future work on pricing, sales, or insurance-adjacent features should treat "price can vary by who's paying and where" as a first-class model, not a bolt-on.

### 8.2 Confirm→Reverse and Draft→Finalize is the correct pattern for financial records

Real financial systems don't hard-delete or silently recompute — they **reverse** with a traceable counter-entry, and draft records only become real once explicitly finalized/posted. This is the opposite of a bug already found and fixed in this project: `FinanceView`'s old "Chart of Accounts" fabricated numbers outright (e.g. "Cash & Bank" = revenue × 0.45, an arbitrary multiplier — see `docs/changelog/v11.19`). The reference shows what a *real* accounting module looks like: actual General Ledger, Trial Balance, P&L, and Balance Sheet, all traceable back to postable journal entries — not display-only numbers computed from a formula. KaziFlow's Finance module now uses real data everywhere (v11.19/v11.20 fixed the fabrication), but it still doesn't have real double-entry bookkeeping (a General Journal, Chart of Accounts as actual structured accounts, opening balances) — the reference's Administration section has all of these as real, separate modules.

### 8.3 Self-service is a permissions layer over existing data, not a separate feature

The reference's "Payslips, Leave Requests & Self Evaluation" module is explicitly just a reduced-permission view into the same Payroll/Leave/Performance data admins already see — not a duplicated set of screens. This is directly relevant to §7.1 (the dynamic roles/Odoo-style permissions request): it's concrete real-world confirmation that the right model for permissions is **a view/action filter over shared data**, not separate employee-facing screens that duplicate admin ones.

### 8.4 One inventory engine, many verticals

Pharmacy, Physiotherapy, and general Stores in the reference all reuse the identical report shapes (stock levels, movement history, reorder points, batch/expiry, stock take). KaziFlow already leans this way — `ProductDAO`/`StockTakeDAO` are already shared across verticals rather than duplicated per profile. Good confirmation this part of the existing architecture is already pointed the right direction.

### 8.5 Compliance/reporting modules are explicitly swappable by country

The reference material calls out Kenya-specific pieces (NHIF/NSSF-equivalents, Pesapal, ICD-10-to-Ministry-of-Health reporting formats) as "the modules to localize first" if this were deployed somewhere other than Kenya. This is the same instinct already behind `VATConfig`/`PayrollTaxConfig` (§4) — just at a bigger scale: not just *rates* being swappable, but entire *reporting formats/standards* being country-specific and swappable.

### 8.6 Group-level structure (from the Unshur memo specifically)

A real holding company operates facilities of genuinely different types (hospitals, nursing homes, a "family medical centre") under one group, some with their own sub-branches (e.g. "Reliable Healthcare Services — Main Branch & Kivaa Branch") — a second, nested level of structure beyond just "facility." Group-level reporting rolls up both financial (targets vs actual, petty cash, creditors/debtors) and clinical (per-clinician-per-shift statistics by insurance scheme, bed occupancy, visiting doctors) figures monthly to a Group General Manager. This is the concrete real-world case behind §9's "one module set per installation" limitation — it's not a hypothetical edge case, it's how this exact reference organization actually operates today.

**Status: this is architectural evidence gathered, not a build plan.** None of §8.1–8.6 has been scoped into actual work yet — each represents a real gap worth discussing with the project owner before any of it gets designed or built.



---

## 9. Why the "One Module Set Per Installation" Limitation Matters (context for §7/§8)

This came up directly in conversation with the project owner around a hypothetical (a pharmacy + clinic + nursing home under one company) and is now independently confirmed by real reference material in §8. The honest current answer, given today's architecture: pick **CUSTOM** as the Industry Profile, use **System Modules** to turn on the union of what's needed, and use **Branch Management** to track locations. The real limitation, stated plainly: **every branch shares the same active module set** — there's no way for one branch to be pharmacy-configured and another nursing-home-configured within a single installation. For genuine per-branch operational separation today, running separate installations (using the multi-computer modes in §3) is the more honest fit than pretending the single-database model handles it. Whether/how to build real per-branch module configuration is an open design question, not yet decided.

---

## 9.5 Consolidated Architectural Gaps — What Actually Stands Between "Foundation" and "Serves a Real Organization"

This section collects every structural gap identified across the whole review, ordered by how consequential each is. It exists because the project owner's own framing was precise: *"what we have now is the foundation and can't serve a real organization work setting to its fullest."* These are the specific reasons why.

**None of these are bugs.** Every bug found during the review was fixed (see the changelog). These are architectural limitations — things the current data model or design genuinely cannot express, no matter how much feature work is layered on top. Each needs a design decision before code.

### Tier 1 — Structural, affects everything built on top

**1. One flat price per product. No payer-type concept.**
The reference HMIS prices the same item differently per payer (cash / each insurance scheme / corporate account) *and* per branch. The Unshur memo independently confirms this is load-bearing, naming nine real Kenyan schemes (TSC, Usalama, UHC, FFS-Others, M-Tiba, CIC, Britam, KPLC, APA) that daily reporting must break down by. KaziFlow's `products` table has a single `selling_price` column. **Every pricing, invoicing, and insurance-adjacent feature built before this is resolved will need reworking afterward.** This is the most consequential single finding of the entire review.

**2. No real double-entry bookkeeping.**
v11.19/v11.20 removed fabricated financial figures and replaced them with real summed data — genuine progress, but the Finance module still computes reports directly from sales/expenses rather than from posted journal entries. The reference has General Journal, a real structured Chart of Accounts, Opening Balances, and Trial Balance as separate first-class modules. Without postable journal entries there is no audit trail from a report back to the transactions that produced it, and no way to express adjustments, accruals, or corrections that aren't themselves sales or expenses.

**3. No Confirm→Reverse / Draft→Finalize lifecycle on financial records.**
Real financial systems never hard-delete; they post a traceable reversing entry. KaziFlow currently deletes or edits in place. Related: v11.17 introduced exactly this pattern for purchases (pending → received, with stock only moving on receipt, guarded against double-application) — that's the correct shape, and it should generalize to sales, invoices, and journal entries rather than remaining purchase-specific.

### Tier 2 — Blocks real multi-entity organizations

**4. One module set per installation.**
Every branch shares the same active modules. A group running a pharmacy, a clinic, and a nursing home cannot configure them differently within one installation. Confirmed as a real scenario, not hypothetical, by the Unshur memo (§8.6) — nine facilities of genuinely different types, several with their own sub-branches. Current honest workaround: separate installations per facility using the multi-computer modes (§3). See §9 for the full discussion.

**5. No group-level roll-up reporting.**
The memo shows monthly consolidated reporting to a Group General Manager spanning both financial and clinical metrics across all facilities. Nothing in KaziFlow aggregates across installations or branches today.

**6. Fixed admin/user role model.** **RESOLVED in v11.27** — Positions replace it (§7.1).
No way to define a position and assign it a specific set of modules and permissions. This is §7.1's requested work — and §8.3 supplies the design principle from the reference: self-service is **a permissions filter over shared data**, not a separate set of duplicated screens.

### Tier 3 — Real, but narrower in blast radius

**7. No custom module builder** — §7.2. Admin-defined modules with their own fields and calculations. Needs a dedicated design discussion; the central open question is dynamic schema generation vs. a flexible generic storage model (EAV/JSON), which has real tradeoffs either way. **Built in v11.28 (engine + runtime screen + builder): `docs/design/custom-modules.md`.**

**8. Country-specific reporting formats are not swappable.** `VATConfig`/`PayrollTaxConfig` made the *rates* dynamic (§4), but statutory *report formats* (ETIMS, MoH-style returns) remain Kenya-shaped in code. §8.5.

**9. Loyalty earn-rate still hardcoded** ("1 point per KES 100" in `SalesView`). The smallest item on this list and the most mechanical to fix — same treatment as VAT/payroll.

**10. ~76 dialogs still construct their own boilerplate.** `DialogUtils` exists (§4.5) and resizability was applied project-wide, but migrating existing call sites to `styledDialog()` — eliminating the duplicated title/style/width setup itself — hasn't been done. Deliberately not rushed: a careless mechanical sweep at that scale is exactly how the v11.19 regression happened.

### What the reference material confirms is already right

Worth stating explicitly so it doesn't get "fixed" unnecessarily: **one inventory engine serving all verticals** (§8.4) — the reference reuses identical stock/movement/reorder/batch/stock-take shapes across Pharmacy, Physiotherapy, and general Stores, exactly as KaziFlow already shares `ProductDAO`/`StockTakeDAO` rather than duplicating per profile. The Industry Profile system's basic premise is sound.

---



## 10. File Map — Where Things Live (quick reference)

| Concern | File(s) |
|---|---|
| Industry profiles & module activation | `modules/IndustryProfile.java`, `modules/ModuleRegistry.java` |
| Database schema & connection handling | `database/DatabaseManager.java` |
| Multi-computer networking | `network/*` |
| Dynamic config services | `services/CurrencyService.java`, `services/PayrollTaxConfig.java`, `services/VATConfig.java` |
| Shared dialog construction | `utils/DialogUtils.java` |
| Password show/hide toggle | `utils/PasswordFieldUtils.java` |
| Permission checks (read side) | `services/PermissionService.java`, `services/PermissionResolver.java` |
| Permission edits + lockout safety | `services/PermissionAdminService.java` |
| Permission seeding / legacy migration / break-glass | `services/PermissionGroupSetup.java` |
| Special-action catalog | `services/SpecialPermissions.java` (table `permission_actions`) |
| Positions & Access editor | `views/PositionsAccessView.java` |
| Greying controls by permission | `utils/PermissionGuard.java` |
| Custom (admin-built) modules: definitions / records / formulas | `custom/CustomModuleService.java`, `custom/CustomRecordService.java`, `custom/FormulaEngine.java` |
| Product depth plan and the tool that measures it | `docs/design/product-depth-plan.md`, `tools/depth_audit.py` |
| Brand names / assets / paths | `brand/Brand.java`, `brand/BrandAssets.java`, `resources/brand/brand.properties`, `utils/AppPaths.java`, `utils/BusinessInfo.java`, `tools/build_brand_assets.py`, `docs/brand/` |
| Product pictures | `utils/ImageProcessor.java`, `dao/ProductImageDAO.java`, `utils/ProductImages.java`, `views/ProductImagePicker.java` |
| HMIS reference, menu catalog (data) and its statistics | `docs/reference/HMIS_Feature_Reference.md`, `docs/reference/hmis_menu_catalog.tsv`, `tools/hmis_catalog_stats.py`, `docs/design/hmis-learnings.md` |
| Custom modules: views & reports engine / editor dialog | `custom/CustomQueryService.java`, `custom/CustomViewEditorDialog.java` |
| Custom modules: generated form + subforms / CSV / printable page / formatting | `custom/CustomRecordForm.java`, `custom/CustomCsv.java`, `custom/CustomReportHtml.java`, `custom/CustomFormat.java` |
| Custom modules: generic runtime screen / builder / registry glue | `custom/CustomModuleView.java`, `custom/CustomModuleBuilderView.java`, `custom/CustomModule.java`, `custom/CustomModuleLoader.java` |
| Sandbox compile/test tooling | `tools/sandbox-verify.sh`, `tools/checks/*` |
| POS / checkout | `views/SalesView.java`, `dao/SaleDAO.java` |
| Payroll | `views/PayrollView.java`, `dao/PayrollDAO.java` |
| Settings (all dialogs) | `views/SettingsView.java` — **large file, many independent `if (title.equals(...))` dispatch blocks; see §6 and the v11.10 changelog entry for a real bug that hid in exactly this kind of dispatch chain (a misplaced brace made 6 settings dialogs completely unreachable for an unknown period)** |

---

## 11. Known Open Issues (as of this writing)

- **GitHub push is not working.** A fine-grained PAT with seemingly full permissions still gets a 403 on push, despite the repo being confirmed to exist and be readable via the API. Likely cause not yet confirmed: fine-grained tokens have a separate "Repository access" allow-list distinct from the permissions checklist — if `KaziFlow-Java` isn't explicitly added there, every operation on that repo fails exactly like this even with every permission checked. Not yet resolved as of this writing.
- Everything in §4's "backlog" list (loyalty rate, dynamic roles, module builder).
- §8's reference material review is incomplete.

---

## 12. Full Detailed History

Every version from the original upload (v9) onward has its own dated entry in `docs/changelog/`, each documenting exactly what changed, where, and — for real bugs — how they were found and verified. `docs/changelog/README.md` is the index. This guide is the map; the changelog is the territory. When in doubt about *why* something is built a certain way, check there before assuming.

---

## 11. Known issues found during v11.27 (not fixed — flagged for decision)

- **`mvn test` has not compiled since v11.18:** `PayrollDAOTest` still tests `PayrollDAO.calculateNHIF`, removed when NHIF became SHIF. With that one file excluded, the suite runs 233 tests and **25 fail on pristine v11.25 too** (stale expectations: "exactly 19 profiles" vs 22, "core modules" lists, `KESFormatter` null/billions cases, `normalizePhone(null)`). CI would be red. These need a decision per test: update the expectation or fix the code.
- **AI Assistant panel is not permission-gated:** a user without Finance access could still ask it about financial data.
- **Own position changes** show in the sidebar after the next sign-in (permissions themselves refresh within seconds).
- **`SessionManager.getBusinessSetting()` is a dead stub** that always returns `""` (zero callers). Business settings are read straight from the `settings` table (see `ReceiptPrinter`, `CustomModuleService.businessName()`); the stub should be implemented or deleted.
- **Invoice numbers are `COUNT(*)+1`** (`SaleDAO.generateSaleNumber`) on a `UNIQUE` column, read then written in one transaction: in server/client LAN mode two PCs selling together can fail the second with `SQLITE_BUSY_SNAPSHOT` (the v11.28 read-then-write pattern, in the checkout path). Not configurable per document type. Fix = a sequence service with atomic next-number and bounded retry (hmis-learnings.md §5 item 1).
- **Payment methods are a `CHECK` constraint** on `sales` (`cash/mpesa/card/bank`): adding one needs a schema migration. Should be master data.
- **The Finance "Chart of Accounts" screen is a derived summary**, not a ledger; `accounts.balance` is never written by any code, and "Cash & Bank" is estimated from net cash flow. There is no journal / double-entry layer (hmis-learnings.md §3).
- **Three different version numbers:** `UpdateService.CURRENT_VERSION` = 2.0.0 (now the single source for `Brand.version()`), `pom.xml` and the installers = 1.0.0. Decide which is the product version and align them.
- **The AI assistant's system prompt contains the default admin login** (`admin@kaziflow.co.ke` / `admin123`) and is sent to a third party; the M-Pesa callback URL is hardcoded to `kaziflow.co.ke`; `LicenseService.SIGNING_SECRET` is hardcoded and labelled "CHANGE IN PRODUCTION" (anyone with the jar can forge licences).
- **`installer/LICENSE.txt` (the EULA) names an individual as licensor/author**; installer publisher metadata now says Tihems Software. Which legal entity should be the licensor needs a decision before the legal text changes.
- `support.email` / `website` in `brand.properties` still point at the old KaziFlow addresses — replace with the Tihems Software ones.
- **Typed policies still in code (found by the v11.39 sweep).** The attendance "late" cut-off (8:30, `AttendanceView` ~363, and the caption "After 8:30 AM") and the standard leave entitlements are constants, not settings — they should be per-company settings (and per leave type). `LeaveRequestView` is unreferenced dead code (v11.39) — delete it. `PurchaseDAO.generatePurchaseNumber` uses `COUNT(*)+1` like the sales number (step C of R1 covers both).
- **No branch on transactional data.** `sales`, `expenses`, `purchases` and `products` do not record a branch (only `stock_transfers` does), so "report by branch" is impossible; the branch box on Reports was removed in v11.41 rather than left decorative. Multi-branch reporting needs `branch_id` on those tables plus a current-branch notion in the session — a data-model decision for R2/R6.
- **Report definitions.** Reports revenue is `sales.total_amount` of completed sales (VAT inclusive; sale returns are not netted off here) and "net profit" is revenue minus recorded expenses (no cost of goods, no payroll, no depreciation): a cash-style view, not accounting profit. R3 (ledger/P&L) should define these properly and the cards should then say which definition they use.
- **Demo company (resolved in v11.37, owner decision).** New databases hold no demo suppliers, employees, products, transactions or expenses; `DemoDataService` loads or removes them on request (Settings → Demo Company). Databases created before v11.37 still contain the old demo rows — and, before v11.36, duplicates of the expenses and transactions made on every launch — until the owner presses Remove demo data (exact-identity match; rows used by real records are kept). Still hardcoded for every business type: the seeded `categories` (Cement, Paints, Plumbing, Tools, Electrical, Steel, Hardware) and the POS category chips — starter packs (depth plan) should replace them.
- **Two definitions of "stock value".** `ProductDAO.getTotalStockValue()` (Reports/Inventory cards, and the Inventory export) uses selling price; the Reports pie uses cost price. Both are now labelled, but one definition (cost, the accounting standard) should be chosen and used everywhere.
- **Smaller findings (v11.36):** the POS category chips (Cement, Paints, Plumbing, Tools, Electrical, Safety Gear, Hardware) are hardcoded instead of read from `categories`; `ReportsView` logs `no such table: deni_entries` when the module's table has not been created yet; `SalesView` stat cards live on the history tab, not the POS tab.
- **`AuditLog` fails silently without its hash columns.** `ensureHashColumns()` (adds `prev_hash`/`record_hash`) is called only from `App.start()`, not `DatabaseManager.initialize()`; any other entry point logs `[AuditLog] Failed to write: no such column: record_hash` and the audit entry is lost. Seen in `EnforcementCheck`, `PermissionsCheck`, `PositionsAccessViewCheck`. Fix = create the columns in the schema/initializer.
- **Fixed in R1, kept for the lesson:** the fake eTIMS serial (v11.33); the pie fallback, the 1,000-row Sales export, the fabricated Finance Summary P&L, the this-week "Total Revenue" footer, the login claim and dead Google button (v11.34); the **Purchases export had never worked** (read non-existent `tax_amount`; real column `vat_amount`) — nothing exercised the export paths, which is why `ReportExportCheck` exists.
