# KaziFlow ERP

Offline-first Desktop ERP for Kenyan SMEs.
M-Pesa Daraja | ETIMS/KRA | Multi-branch | SQLite

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

---

## Login

| Field    | Value                    |
|----------|--------------------------|
| Email    | admin@kaziflow.co.ke     |
| Password | admin123                 |

Change the password immediately after first login.
Settings → Users & Access → select admin → Change Password

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
| Ctrl+B   | Manual backup     |
| Ctrl+Q   | Quit              |
| F5       | Refresh view      |
| F11      | Toggle fullscreen |

---

## Data Location

Database: `~/KaziFlowERP/kaziflow.db`
Backups:  `~/KaziFlowERP/backups/`

---

## Build Notes

- Built with: JavaFX 21, SQLite, OkHttp 4, Gson, jBCrypt
- pom.xml uses `<release>21</release>` (not source/target)
- module-info.java uses `requires jbcrypt` (automatic module name from jbcrypt-0.4.jar)
- No internet required at runtime (except M-Pesa STK Push)
