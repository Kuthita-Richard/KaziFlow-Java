# KaziFlow ERP — v11 Changelog Index

This folder is a debugging/reference repository: one `.md` file per sub-version edit within the v11 task ("work through the Phase 10 TODO list, being dynamic rather than hardcoding"). A new top-level version number is only used when a genuinely new task/TODO list is assigned — everything below was one continuous piece of work.

**Starting point:** `kaziflow-erp-v9.zip`, as uploaded and independently verified against the full prior conversation transcript (see the project's own root-level documentation for that verification).

| Version | What it covers | File |
|---|---|---|
| v10 | Critical bug fixes: POS numpad had no click handlers; first-run profile-selection routing was dead code; a second bug found while fixing the first (profile selection skipped login entirely) | `v10-critical-fixes.md` |
| v11 | Dashboard drill-through/date-filter/top-10 lists/cash-flow chart (Phase 10C); in-app calculator via Ctrl+K (Phase 10E); two bonus fixes (hardcoded sample data in two dashboard cards replaced with real DB queries) | `v11-dashboard-and-calculator.md` |
| v11.1 | Stock-take / physical inventory count module (Phase 10E); quotation→sale conversion — found the existing button was a stub, made it create a real sale | `v11.1-stocktake-and-quotation-conversion.md` |
| v11.2 | Loyalty points redemption (Phase 10E) — found the POS customer dropdown was decorative and fixed that first | `v11.2-loyalty-points.md` |
| v11.3 | Multi-currency support (KES stays the DB's base currency; display-only conversion) and email receipts (dependency-free raw SMTP) (Phase 10E) | `v11.3-multicurrency-and-email-receipts.md` |
| v11.4 | Hash-chained, tamper-evident audit trail (security hardening) — plus a real concurrency bug found and fixed in the old audit logger | `v11.4-hash-chained-audit-trail.md` |
| v11.5 | SACCO / SHG module (Phase 10A) — reducing-balance loans (confirmed via research as the real-world standard), dividends, merry-go-round | `v11.5-sacco-shg-module.md` |
| v11.6 | Delivery management (Phase 10B) — per-km pricing + named-location overrides, full POS checkout integration | `v11.6-delivery-management.md` |
| v11.7 | Multi-computer support (Phase 10D) — both Shared-Database-File and Server/Client modes, selectable at setup. Includes a genuine runtime-verified network layer (see that file for the full test results) | `v11.7-multi-computer-support.md` |

## Status at the close of v11

Every item originally scoped for "Phase 10 / do everything" is now implemented:
- ✅ Phase 10A — SACCO/SHG
- ✅ Phase 10B — Delivery management
- ✅ Phase 10C — Dashboard drill-through, filtering, top-10 lists, cash flow
- ✅ Phase 10D — Multi-computer support (both modes)
- ✅ Phase 10E — In-app calculator, stock-take, loyalty redemption, quotation→sale conversion, multi-currency, email receipts

## A note on verification method

Two very different verification methods were used across this changelog, and it matters which applies to which file:
- **v10 – v11.6**: verified by careful manual code review only (brace/paren balance scripts, cross-referencing DAO column names against schemas, array-index consistency checks, import verification) — **no compiler was available** for most of this work, since this sandbox has no Maven Central access and no local JavaFX/dependency cache.
- **v11.7**: partway through this file, a real JDK (`javac`) was successfully installed via `apt-get` (Ubuntu's own archive mirrors are reachable even though Maven Central isn't), which made it possible to genuinely compile — and, further, to download a real `sqlite-jdbc` jar from GitHub Releases and **runtime-test** the entire new network layer end-to-end against an actual database. This is a meaningfully stronger form of verification than anything used earlier in this changelog, and is called out explicitly in that file.

If a bug turns up anywhere in v10–v11.6, treat "it was reviewed" as weaker evidence than "it was compiled and run" — that distinction is the main reason this index calls it out.
