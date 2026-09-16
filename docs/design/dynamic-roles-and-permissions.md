# Design: Dynamic Roles & Permissions

**Status:** Design proposal — no code written yet. Needs sign-off before implementation.
**Requested:** replace fixed admin/super-admin tiers with dynamically assigned access levels, rules, and priorities. Research Odoo's approach first.
**Related:** `PROJECT_GUIDE.md` §7.1, §8.3, §9.5 (Tier 2, item 6)

---

## 1. What KaziFlow has today (audited, not assumed)

| Piece | State |
|---|---|
| `roles` table | Exists: `id`, `name`, `permissions TEXT DEFAULT '{}'`, `created_at` |
| `users.role_id` | Exists — each user has exactly **one** role |
| 5 seeded roles | Administrator `{"all": true}`, Store Manager, Cashier, Warehouse Staff, Accountant |
| `SessionManager.hasPermission(String)` | Exists — does substring matching on the raw JSON text |
| **Enforcement** | **None.** `hasPermission()` has **zero callers** anywhere in the codebase. |

**The critical finding: permissions are stored, displayed in Settings → Roles & Perms, and never once checked.** Every logged-in user can reach every module regardless of role. A "Cashier" has identical actual access to an "Administrator."

This is the same failure pattern already found and fixed twice this project: a settings surface that *looks* functional and saves real data, but nothing on the read side ever consults it (VAT rate — v11.20; Branch Management — v11.15). Worth noting explicitly because it means **this isn't a "extend the existing system" job — there is no existing enforcement to extend.** We're building the enforcement layer from scratch, which is actually freeing: no migration away from a half-working model.

Secondary issue: `hasPermission()` does `perms.contains("\"inventory\": true")` — raw substring matching on JSON. Fragile (whitespace-sensitive, no real parsing) and can't express anything beyond flat booleans.

---

## 2. How Odoo does it (research summary)

Odoo's model is genuinely well-designed and worth borrowing from. Four layers, evaluated together — if any layer denies, access is denied:

### Layer 1 — Groups (`res.groups`)
The core unit. **Not a role ladder — an arbitrary, composable set.** A user belongs to *any number* of groups. There is no "super admin above admin above user" hierarchy baked into the model; seniority is an emergent property of which groups you hold.

Groups support **inheritance** via `implied_ids`: granting "Accounting / Manager" automatically implies "Accounting / User". So hierarchy is *expressible* but not *mandatory* — exactly the flexibility requested.

### Layer 2 — Access Rights (`ir.model.access`)
Per-model CRUD: `perm_create`, `perm_read`, `perm_write`, `perm_unlink`. Each ACL row ties (model → group → which of the four operations).

**Crucially: access rights are ADDITIVE (union, not intersection).** If group A grants read and group B grants write, a user in both gets read *and* write. No group can subtract a permission another grants. This is the single most important design decision to copy — it makes permissions composable and predictable. The alternative (deny-overrides) produces systems where admins can't reason about why someone lacks access.

If *no* ACL matches a model+operation for any of a user's groups, access is **denied by default**.

### Layer 3 — Record Rules (`ir.rule`)
Row-level filtering via a domain expression, e.g. "salespeople see only *their own* leads." Evaluated per-record, *after* access rights pass.

Rules are **default-allow**: if access rights grant access and no rule applies, access is granted. Group-specific rules are OR'd (satisfy any one → allowed); global rules (no group) are AND'd (must satisfy all).

### Layer 4 — Field & UI visibility
Individual fields and menu items can be tagged with required groups.

### Multi-company pattern (directly relevant to our branch problem)
Odoo handles multi-entity with a **global record rule** on `company_id`:
```
['|', ('company_id', '=', False), ('company_id', 'in', company_ids)]
```
where `company_ids` = the companies the user currently has active. Records with a null company are shared across all; others are visible only to users in that company. **This is a proven answer to §9.5's Tier-2 gap #4/#5** — it's how a real system lets one installation serve a multi-facility group.

---

## 3. Proposed design for KaziFlow

Borrow the shape, cut the scope. Odoo's full four-layer system with domain-expression parsing is more machinery than KaziFlow needs right now, and building a domain-expression evaluator is a genuine mini-language project. Proposal: **implement Layers 1 and 2 properly and completely, defer Layer 3, skip Layer 4 initially.**

### 3.1 Schema

```sql
-- A named, composable permission set. Replaces the "role ladder" concept.
CREATE TABLE IF NOT EXISTS permission_groups (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL UNIQUE,     -- "Pharmacy Dispenser", "Branch Accountant"
    description TEXT,
    is_system   INTEGER NOT NULL DEFAULT 0, -- 1 = seeded, protects against deleting yourself out of access
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Group inheritance (Odoo's implied_ids). Hierarchy expressible, not mandatory.
CREATE TABLE IF NOT EXISTS permission_group_implies (
    group_id   INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    implied_id INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, implied_id)
);

-- Many-to-many: a user holds ANY NUMBER of groups. This is what replaces users.role_id.
CREATE TABLE IF NOT EXISTS user_permission_groups (
    user_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id)
);

-- Per-module CRUD, per group. Additive union across all a user's groups.
CREATE TABLE IF NOT EXISTS module_access (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id     INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
    module_id    TEXT NOT NULL,            -- matches Module.getId(): "sales", "inventory", "sacco"...
    can_read     INTEGER NOT NULL DEFAULT 0,
    can_create   INTEGER NOT NULL DEFAULT 0,
    can_write    INTEGER NOT NULL DEFAULT 0,
    can_delete   INTEGER NOT NULL DEFAULT 0,
    UNIQUE(group_id, module_id)
);
```

`module_id` deliberately reuses the **existing** `Module.getId()` strings the sidebar already keys off — so permissions and navigation stay automatically in sync, and a newly added module needs no separate permission registry.

### 3.2 Service: `PermissionService`

Follows the established config-service pattern (`VATConfig`/`PayrollTaxConfig` — `PROJECT_GUIDE.md` §4):

```java
PermissionService.getInstance()
    .can(String moduleId, Action action)   // Action = READ|CREATE|WRITE|DELETE
    .canRead(String moduleId)              // convenience
    .getAccessibleModuleIds()              // for sidebar filtering
    .reloadForCurrentUser()                // on login / group change
```

Resolution algorithm, mirroring Odoo exactly:
1. Collect the user's directly-assigned groups.
2. **Transitively expand** via `permission_group_implies` (cycle-guarded — a group implying itself through a chain must not infinite-loop).
3. For the requested module+action, return `true` if **any** group grants it (additive union).
4. Default deny if nothing grants it.
5. Cache the resolved set per session; invalidate on login and on any group edit.

### 3.3 Enforcement points (the part that actually matters)

Building the service without wiring it in would repeat the exact bug this document opens by describing. Three concrete integration points, all of which must ship together:

1. **`ModuleRegistry.loadModules()`** — already filters modules by industry profile and per-installation override. Add permission filtering as a third filter. A user without `read` on a module simply never sees it in the sidebar.
2. **`SceneManager.navigateTo(moduleId)`** — defence in depth. Keyboard shortcuts (Ctrl+N → sales, etc.) bypass the sidebar entirely, so a hidden module is still reachable by shortcut unless navigation itself checks.
3. **Action buttons** — Save/Delete/Edit disabled (not hidden) when the user lacks `write`/`delete`. Disabled-with-tooltip beats invisible: the user learns the capability exists and who to ask, rather than being confused about a missing button.

### 3.4 Migration from the current model

Non-destructive, following the project's established additive-migration pattern:
- Keep `users.role_id` and the `roles` table intact initially.
- On first run of the new version, seed `permission_groups` mirroring the 5 existing roles, and populate `user_permission_groups` from each user's current `role_id`.
- Administrator's group gets full CRUD on every module.
- `roles` becomes legacy/unused once verified, but is not dropped — consistent with how `nhif` columns were left in place during the SHIF migration (v11.18).

**Lockout safety:** at least one active user must always retain full admin access. Enforce at the service layer — refuse to remove the last remaining full-access assignment, with a clear error. This is the single most dangerous failure mode in a permissions feature; getting locked out of your own ERP with no recovery path would be severe.

---

## 4. Deliberately deferred

**Record Rules (Odoo Layer 3)** — "this cashier sees only their own sales," "this manager sees only their branch's data." Genuinely valuable and directly relevant to the multi-facility case in §8.6. Deferred because a real implementation needs a domain-expression parser/evaluator, which is a substantial subproject of its own. The schema above doesn't preclude adding it later.

**Branch-scoped access** — when branch support becomes real (§9.5 Tier-2 #4/#5), Odoo's `company_ids` pattern (§2 above) is the model to follow: a user holds a set of accessible branches, and a global rule filters every branch-scoped table. Worth designing *together* with branch work rather than bolting on afterward.

**Field-level permissions (Layer 4)** — e.g. hiding salary figures from non-HR staff within an otherwise-visible screen. Lower priority; revisit if a concrete need appears.

---

## 5. Why this answers the original request

> *"have level of access and rules and priorities set dynamically... once a user is assigned a position, let them choose/be assigned the modules they need, rather than having super admins and admins"*

- **No fixed tiers** — "Administrator" becomes just another group with broad grants, not a privileged concept the code knows about.
- **Assign a position → pick its modules** — creating a group *is* defining a position; checking modules against it *is* assigning what that position needs. Directly, not by analogy.
- **Composable** — a person who is both cashier and part-time bookkeeper gets both groups. No need to invent a "Cashier+Bookkeeper" role, which is exactly the combinatorial explosion fixed role ladders cause.
- **Dynamic, DB-driven** — consistent with §0's governing principle. No code change to add a position.
- **§8.3 alignment** — the reference HMIS's employee self-service module is a reduced-permission view over the *same* data admins use. This design produces that naturally: a "Self Service" group with read-only access to payroll/leave gives employees their own view with no duplicated screens.

---

## 6. Open questions for sign-off

1. **Scope check:** Layers 1+2 now, Layer 3 (record rules) later — agreed, or is row-level filtering ("only see your own sales") needed in the first cut?
2. **UI placement:** Settings → Roles & Perms currently exists as a read-only list. Replace it with the group editor, or add a separate "Positions & Access" screen?
3. **Naming:** "Permission Group" (Odoo's term) vs. "Position" (closer to your phrasing) vs. "Access Profile". This surfaces in the UI, so worth picking deliberately — I'd lean "Position" since it matches how you described the need and how a business owner actually thinks.
4. **Granularity:** is per-module CRUD enough, or are there specific actions needing their own permission (e.g. "can apply discount", "can void a sale", "can view cost prices")? These are real POS concerns that don't map cleanly onto CRUD-per-module.
