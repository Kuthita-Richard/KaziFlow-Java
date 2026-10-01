# Custom modules (admin-defined departments) — feasibility & design

**Status:** feasibility proven on the app's own SQLite version; design proposed; **not built yet** (only the registry hook exists).
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
