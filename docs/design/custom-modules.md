# Custom modules (admin-defined departments) — feasibility & design

**Status (v11.29):** feasibility proven on the app's own SQLite version; engine, generic runtime screen, builder UI, **saved views & reports, subforms, CSV import/export and validation rules are built and verified** (see "Implementation status" at the bottom). Starter packs, built-in links and entity-level permissions are still to do.
**Why this exists:** a hospital group with no transport department later adds an Ambulance department, or a non-pharma company adds Staff Transport. An administrator must be able to create that department — naming it and choosing the entities (and their fields) it holds — without a developer, a code change or a redeploy. Closes PROJECT_GUIDE §7.2 / gap #7.

## 1. Verdict: implementable — without per-field schema changes

The question "does the database have to grow new columns?" has a better answer than yes: **no**. Store each record as a validated JSON document in ONE generic table, keep the *definition* of modules/entities/fields in metadata tables, and (only if a field needs to be searched fast) add an **expression index** — a `CREATE INDEX`, not a column. If real columns are ever wanted, `ALTER TABLE ... ADD COLUMN ... GENERATED ... VIRTUAL` also works (verified below), so that door stays open.

### Verified on SQLite 3.44.1 (the exact version the app bundles) — prototype built on the HMIS reference §37 "Vehicles & Mileage"
| Check | Result |
|---|---|
| `CHECK (json_valid(data))` rejects malformed documents at the database level | ✅ |
| 3-entity example (Vehicle Type → Vehicle → Mileage Log) stored as rows of one table; the "Vehicle Mileage Report" = lookup joins + `SUM(json_extract(...))` grouped per vehicle | ✅ (e.g. `KDA 123A / Ambulance / 200.5 km / 23.5 L`) |
| Expression + **partial** index `ON custom_records (json_extract(data,'$.vehicle')) WHERE entity_id = 3` is chosen by the planner (`SEARCH ... USING INDEX`) | ✅ |
| `ALTER TABLE ADD COLUMN ... GENERATED ALWAYS AS (json_extract(...)) VIRTUAL` + index works at run time, no table rewrite | ✅ |
| `STORED` generated column via `ALTER` | ❌ refused ("cannot add a STORED column") — use VIRTUAL |
| `DROP COLUMN` of that virtual column | ✅ **only after dropping its index first** (otherwise SQLite errors) — DDL order matters |
| Adding a new field to existing records needs no migration (`json_set`; old rows simply have no value) | ✅ |
| A computed field ("km per litre") as a whitelisted expression compiled to SQL | ✅ |
| LAN mode: the client↔server protocol has a DDL passthrough (`NetProtocol.OP_EXECUTE`), so index creation from a client PC works | ✅ (code-confirmed; not run over a live socket) |

Not yet verified: behaviour under heavy concurrent LAN writes, and very large record counts (see §8).

## 2. Research findings that shaped the choice
- **Frappe** (the ERP behind ERPNext/HRMS/Healthcare) is *metadata-driven*: a "DocType" definition generates the forms, permissions, reports and API. That is the model to copy — definitions in data, one generic runtime.
- **Odoo** custom/Studio fields are real `x_`-prefixed columns. It works, but Odoo's own migration docs warn that custom-field data is **lost** when the underlying model changes during an upgrade, and its UI custom fields cannot be computed or relational. Real-column-per-field buys speed we don't need and costs migration risk we can't afford on offline SMEs' machines.
- **Odoo Studio computed fields use a sandboxed expression**, not arbitrary code — the same line §7.2 asks us to hold ("calculations without arbitrary code execution").
- **SQLite as a document store** is an established pattern: JSON in a column, `json_extract` generated columns / expression indexes added later "without touching existing rows".

## 3. Options compared
| | A. Real table + `ALTER` per entity/field | B. EAV (one row per value) | **C. JSON document + expression indexes (recommended)** |
|---|---|---|---|
| New field | `ALTER TABLE` at run time (migration risk, LAN/backup/rollback complications) | new rows | none — metadata row only |
| Typed queries / reports | native | painful pivots | `json_extract` (+ casts) — fine for SUM/GROUP BY |
| Speed on searchable fields | native index | poor | expression/partial index (verified) |
| Rollback / backup / restore | schema drifts per install | fine | fine — plain rows |
| Injection surface | table/column names built from user text | low | none — fields addressed by immutable generated keys |
| Integrity | FKs | weak | `json_valid` CHECK + engine-level validation; lookups validated by the engine |

## 4. Proposed schema (additive, like the permissions migration)
```
custom_modules  (id, module_key UNIQUE  -- becomes the module id, always prefixed "x_"
                 label, icon, description, active, sort_order, created_at)
custom_entities (id, module_id FK, entity_key, label, plural_label, title_field_id, sort_order)
custom_fields   (id, entity_id FK, field_key  -- immutable "f_<id>", NEVER derived from the label
                 label, type, required, default_value, choices JSON, lookup_target,
                 formula, unit, show_in_list, searchable, sort_order, archived)
custom_records  (id, entity_id, data TEXT CHECK(json_valid(data)),
                 created_at, updated_at, created_by, deleted_at)   -- soft delete
```
- **Types:** text, long text, integer, decimal, money (KES), date, date-time, yes/no, choice (list), **lookup** (another entity — custom *or built-in*: Product, Customer, Employee, Branch), calculated.
- **Immutable field keys:** renaming a label changes nothing stored, in formulas, or in indexes. Fields are *archived*, never dropped; data is never deleted by an edit.
- Type changes after data exists are restricted to safe widenings (e.g. integer → decimal).

## 5. How it plugs into what already exists
- `CustomModule implements Module` with `isUserDefined() = true` (**built in v11.27**) — always active once registered, immune to profile switches and System Modules overrides. One generic runtime view renders any entity from its metadata (list, search, create/edit form, lookup pickers).
- `ModuleRegistry.registerModule(Module)` (**built in v11.27**, verified: appears in the Positions editor immediately, administrators can open it, everyone else is denied until granted a Position). On startup the registry registers every `active` custom module from the database.
- **Permissions come for free:** `module_access.module_id` is a free string, so `x_transport` gets Read/Create/Write/Delete rows with no schema change. It is also forward-compatible with *entity-level* control later (`x_transport:vehicle_mileage`) with no migration.
- **Audit:** every create/update/delete goes through `AuditLog`.
- **Reuses one engine for all departments** — the same principle as the shared inventory engine (§8.4).

## 6. Calculations without arbitrary code
A tiny expression language, parsed by our own parser against a whitelist: numbers, `+ - * / ( )`, field references (`{km}`), `IF`, `ROUND`, `MIN`, `MAX`, `TODAY()`, and aggregates over child records (`SUM`, `COUNT` of a related entity, e.g. a vehicle's total km). It is **compiled to parameterised SQL over `json_extract` of looked-up field keys** — user text is never concatenated into SQL and nothing is evaluated as code. Divide-by-zero yields empty, not an error. Calculated fields are computed on read, not stored.

## 7. Worked example — HMIS reference §37, built by an administrator in the (future) builder
Module **Vehicles & Mileage** (`x_vehicles`): entities **Vehicle Type** (name) → **Vehicle** (registration, type↩lookup, capacity, assigned branch↩Branch) → **Mileage Log** (vehicle↩lookup, date, km, litres, cost money, `km per litre` = `{km}/{litres}`); report "Mileage by vehicle" = SUM km, litres, cost grouped by vehicle. The same builder produces "Staff Transport" for a non-pharma company, or any department we haven't anticipated.

## 8. Risks and open questions
- **Concurrency on LAN:** JSON read-modify-write could lose a concurrent edit → update through `json_set` for single fields and/or a `version` column with optimistic check.
- **Scale:** expression indexes make per-field lookups fast; very large report scans over JSON should be measured before promising numbers.
- **Per-branch availability** of a custom module touches gap #4 (one module set per installation).
- **Localisation:** labels need Swahili variants (or a per-label translation table).
- **Import/export:** module definitions should export/import as JSON so a "Fleet & Mileage" starter pack can be installed in one click and shared between installations.
- **Decisions needed from the owner:** (1) module-level vs entity-level permissions in v1? (2) lookups into built-in entities (Product/Customer/Employee/Branch) in v1 — recommended yes; (3) which reports in v1; (4) Draft→Final lifecycle states per entity (guide gap #3)?

## 9. Suggested phases
1. **Engine (pure JDK, testable in the sandbox):** metadata + records DAO, validation, lookup resolution, formula compiler, expression-index management — with real-SQLite assertions like `PermissionService` had.
2. **Generic runtime view:** list / search / form / lookup picker / CSV export.
3. **Builder UI** in Settings (create module → entities → fields → live preview).
4. **Reports:** grouped sums over entities; link into Finance cost centres.
5. **Templates:** import/export; ship "Fleet & Mileage" (from §37), "Assets" (§36), "Requisitions" (§39) as starter packs.

---

## Implementation status (v11.28)

**Built** — package `com.kaziflow.custom`, verified with `tools/checks/CustomModuleCheck` (71 assertions, real SQLite) and `CustomModuleUiCheck` (19, real JavaFX; the screens were rendered and inspected):

| Phase | What exists |
|---|---|
| 1. Engine | `CustomModuleService` (modules → entities → fields; immutable `f_<id>` keys; type/link fixed once created; archive-not-drop; optional expression index = the only DDL), `CustomRecordService` (validated JSON records; type/required/choice/link checks; soft delete; "still used by N records" protection; values-only search; lookup titles), `FormulaEngine` (own parser, whitelisted functions, no code evaluation; `SUMOF`/`COUNTOF` child aggregates through bound parameters) |
| 2. Generic runtime screen | `CustomModuleView`: a tab per record type, searchable list (columns = "show in list" fields), totals for numeric columns, generated add/edit form (one control per field type; validation keeps the dialog open), buttons follow Create/Write/Delete on `x_<key>` |
| 3. Builder | `CustomModuleBuilderView` (Settings → **Custom Modules**): create/edit/deactivate/delete modules, add/rename/delete record types, add/edit/archive fields incl. choices, link targets and formulas typed with readable names (`{Km} / {Litres}`, `SUMOF(Mileage Log, Km)`) |
| Wiring | `CustomModule` (one `Module` class for every custom module; `isUserDefined`), `CustomModuleLoader.sync()` (startup + after builder saves; deactivated modules are unregistered), `ModuleRegistry.registerModule/unregisterModule`, hidden from System Modules toggles, permissions automatic via `x_<key>` |

**Verified end to end:** the HMIS §37 "Vehicles & Mileage" example built through the builder's real controls → appears in the registry → administrators can open it, a Cashier cannot (default deny) → a Position granting `x_ambulance` opens it live → read-only Position gets a disabled "+ New" → deactivation removes it while the data stays.

**Decisions taken (change if you disagree):** module-level permissions in v1 (`module_access.module_id` is a free string, so `x_transport:vehicle` entity-level rules need no migration later); lookups target custom entities only in v1 (built-in Product/Customer/Employee/Branch pickers are the natural next step); deleting is refused while data exists (deactivate instead).

**Found while building (real bugs, fixed):** list search ran over raw JSON and matched internal field keys (`_` matched everything) → now `json_each` values only; the WL-mode "read-then-write" transaction in `PermissionAdminService.guarded()` could fail with `SQLITE_BUSY_SNAPSHOT` whenever the async audit writer committed in between (the busy timeout does not apply in that case) → bounded retry of the rolled-back transaction (also in the seeder).

**Still to do:** reports (grouped sums beyond the per-list totals), starter-pack import/export (Fleet & Mileage, Assets, Requisitions from the HMIS reference), lookups into built-in entities, Draft→Final states, per-branch availability, Swahili labels, optimistic locking for concurrent LAN edits, a live-socket test of record creation from a client PC.

---

## Implementation status (v11.29) — the "Access-style" layer

Built on the v11.28 engine, verified with `tools/checks/CustomReportingCheck` (88 assertions, real SQLite, incl. a 20,000-record performance run) and `CustomReportingUiCheck` (19, real JavaFX; screens rendered and inspected).

| Feature | What exists |
|---|---|
| **Validation rules** | Per-field min/max (numbers bound the value, text bounds its length) with an optional custom message; enforced on every create/update/import; additive migration for older databases (`custom_fields.min_value/max_value/validation_message`). Ignored for types where a range is meaningless. |
| **Views & reports** (`CustomQueryService`) | Conditions (is / is not / > / ≥ / < / ≤ / contains / is empty / is not empty), sort, group by a field or by month/year of a date, totals (sum / average / smallest / largest + record count), grand totals, saved per record type and shared. Relative date tokens (`@today`, `@month_start`, `@month_end`, `@year_start`, `@days_ago_N`) keep a saved "this month" view correct. Built with dropdowns (`CustomViewEditorDialog`) — no query is ever typed. |
| **Subforms** (`CustomRecordForm`) | Opening a record shows every record type that links to it (a Vehicle's Mileage Logs), filtered to that record, with add / edit / delete in place; the parent link is pre-filled and locked; buttons follow the CHILD module's Create/Write/Delete access. |
| **CSV** (`CustomCsv`) | Export of a list or report (UTF-8 with BOM for Excel; links as titles; leading `=`/`+`/`@` defused against spreadsheet-formula injection). Import is two-step: plan (column matching by header label, per-row problems with spreadsheet row numbers, nothing written) then commit of the valid rows in ONE transaction. Tolerates `;` or `,` delimiters, `dd/mm/yyyy`, `KES 1,200`, yes/no, case-insensitive link and choice matching. No de-duplication. |
| **Printable report** (`CustomReportHtml`) | A self-contained, print-styled HTML page (browser → print / save as PDF), every value HTML-escaped. |

**Decisions taken:** filters, sorting and grouping work on STORED fields; calculated fields can be shown in a list but not filtered/sorted/grouped (they're computed per record in Java); views are shared by everyone with access to the module, and saving/deleting one needs Write on the module; reports page through the browser rather than `PrinterJob` (robust, testable, matches `ReceiptPrinter`'s approach).

**Performance, measured on SQLite 3.44.1 with 20,000 records / 200 vehicles (this sandbox):** grouped report ~200 ms, monthly report with a filter ~150 ms, filtered list (1,000 rows + totals) ~200 ms, plain list ~65 ms, full-scan text search ~130 ms, bulk create (one transaction) ~1.2 s. Two inefficiencies found by this measurement and fixed: the schema check ran on every connection and each row re-read its field definitions and re-verified link targets (bulk create 24 s → 1.2 s; every existing list also got ~20× faster).

**Not verified / still to do:** the field-validation inputs in the builder dialog and the import preview dialog were compiled and their logic tested through the services, but the modal dialogs themselves were not driven end-to-end; opening the printable page in a real browser (`Desktop.open`) is unexercised here; no live-socket test from a LAN client PC; Excel files (`.xlsx`) aren't read directly (save as CSV UTF-8); the 50–100k-record range is untested.

