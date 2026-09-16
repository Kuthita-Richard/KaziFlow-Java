# KaziFlow ERP

Offline-first Desktop ERP for Kenyan SMEs.
M-Pesa Daraja | ETIMS/KRA | Multi-branch | Multi-computer | SQLite

> **New here — human or AI agent?** Read [`docs/PROJECT_GUIDE.md`](docs/PROJECT_GUIDE.md) first.
> It covers architecture, the governing design principles, compliance-critical areas,
> known architectural gaps, and hard-won lessons about verifying changes safely.
> This README is just setup and day-to-day usage.

---

## Requirements

| Tool | Version | Download |
|------|---------|----------|
| JDK  | 21      | https://adoptium.net |
| Maven| 3.8+    | https://maven.apache.org |

Verify both are installed:
```
java -version   # must say 21.x.x
mvn -version    # must say 3.8.x or higher
```

---

## Run

```powershell
cd kaziflow-erp
mvn javafx:run
```

If `mvn javafx:run` gives "No plugin found for prefix javafx", use the full coordinates:

```powershell
mvn org.openjfx:javafx-maven-plugin:0.0.8:run
```

To check compilation without launching:
```powershell
mvn clean compile
```

---

## Login

| Field    | Value                    |
|----------|--------------------------|
| Email    | admin@kaziflow.co.ke     |
| Password | admin123                 |

Change the password immediately after first login.
Settings → Users & Access → select admin → Change Password

---

## First Run

On a fresh database, the app opens the **Industry Profile selector** — pick the business
type closest to yours (22 available, grouped by category). This determines which modules
appear in the sidebar and what terminology the app uses (e.g. a printing shop sees
"Job Order" instead of "Sale").

You can change this later, or override module selection independently:

- **Settings → Industry Profile** — switch business type
- **Settings → System Modules** — turn individual modules on/off regardless of profile.
  Useful when a business spans more than one vertical (e.g. a pharmacy that also runs a clinic).

---

## Configurable Rates — Nothing Tax-Related Is Hardcoded

Kenyan statutory rates change often, so they're all editable without a code update:

| Setting | Where |
|---|---|
| VAT rate, and whether VAT applies at all | Settings → Tax Settings |
| NSSF, SHIF, Housing Levy, NITA, personal relief, full PAYE band table | Settings → Payroll Tax Settings |
| Display currency + exchange rates (KES always remains the stored currency) | Settings → Currency |
| Delivery per-km rate, per-kg surcharge, named-location overrides | Delivery → Pricing |

Defaults reflect rates verified current as of July 2026. If KRA/NSSF/SHIF publish new
figures, update them in-app rather than waiting for a release.

---

## Multi-Computer Setup

Settings → Network / Multi-Computer. Three modes:

| Mode | Use when |
|---|---|
| **Standalone** (default) | Single PC |
| **Server / Client** | Several tills/PCs. One hosts; others connect over LAN. Recommended for concurrent use. |
| **Shared Database File** | All PCs point at one file on a network share. Simpler, but SQLite isn't built for concurrent multi-writer access this way — use only for light, mostly-one-writer setups. |

Restart the app after changing mode.

---

## Keyboard Shortcuts

| Shortcut | Action            |
|----------|-------------------|
| Ctrl+N   | Sales & POS       |
| Ctrl+I   | Inventory         |
| Ctrl+P   | Purchases         |
| Ctrl+E   | Employees         |
| Ctrl+F   | Finance           |
| Ctrl+R   | Reports           |
| Ctrl+A   | Toggle AI Assistant |
| Ctrl+K   | Calculator        |
| Ctrl+B   | Manual backup     |
| Ctrl+Q   | Quit              |
| F5       | Refresh view      |
| F11      | Toggle fullscreen |

---

## Data Location

Database: `~/KaziFlowERP/kaziflow.db`
Backups:  `~/KaziFlowERP/backups/`
Network config: `~/KaziFlowERP/network.properties`

To reset to a clean database (destroys all data):
```powershell
Remove-Item "$env:USERPROFILE\KaziFlowERP\kaziflow.db" -Force
```

---

## Building Installers

Native installers can't be cross-compiled — each must be built on its own platform:

```bash
./installer/build-linux.sh      # produces .deb
./installer/build-macos.sh      # produces .dmg
installer\build-windows.bat     # produces .exe
```

Each script runs `jlink` first to bundle a trimmed Java runtime, so end users
don't need a JDK installed.

**Or let CI do all three:** pushing a version tag (`git tag v1.2.3 && git push --tags`)
triggers `.github/workflows/release.yml`, which builds all three installers on their
respective platforms and uploads them as artifacts.

---

## CI

`.github/workflows/ci.yml` compiles and runs tests on every push and pull request.
Since the project targets JavaFX, a full compile is the only reliable check that a
change is sound — see `docs/PROJECT_GUIDE.md` §6 for why this matters more here
than it might elsewhere.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/PROJECT_GUIDE.md`](docs/PROJECT_GUIDE.md) | **Start here.** Architecture, design principles, compliance notes, architectural gaps, verification practices |
| [`docs/changelog/`](docs/changelog/) | Per-version history — what changed, where, and how bugs were found and verified |
| [`docs/reference/`](docs/reference/) | Real-world reference material from a production Kenyan HMIS deployment |

---

## Build Notes

- Built with: JavaFX 21, SQLite, OkHttp 4, Gson, jBCrypt
- pom.xml uses `<release>21</release>` (not source/target)
- module-info.java uses `requires jbcrypt` (automatic module name from jbcrypt-0.4.jar)
- The `com.kaziflow.network` package deliberately uses only built-in JDK classes
  (hand-rolled JSON via `MiniJson`) so it can be compiled and tested without JavaFX
- No internet required at runtime (except M-Pesa STK Push, SMS, and email receipts)
