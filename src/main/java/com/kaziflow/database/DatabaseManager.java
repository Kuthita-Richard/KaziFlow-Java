package com.kaziflow.database;

import com.kaziflow.network.NetworkConfig;
import com.kaziflow.network.RemoteConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Paths;
import java.nio.file.Files;

public class DatabaseManager {

    private static DatabaseManager instance;
    private static final String DB_DIR = com.kaziflow.utils.AppPaths.dataDir();
    private static final String DB_PATH = com.kaziflow.utils.AppPaths.dbFile();

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Mode-aware entry point used by every DAO. STANDALONE/SERVER modes
     * return the real local SQLite connection; SHARED mode points at a
     * network file path instead of the default local one; CLIENT mode
     * returns a RemoteConnection that transparently proxies every JDBC call
     * to another PC's NetworkServer over the LAN (Phase 10D) — no DAO
     * anywhere in this codebase needs to know or care which of these it got.
     */
    public Connection getConnection() throws SQLException {
        NetworkConfig net = NetworkConfig.getInstance();
        return switch (net.getMode()) {
            case CLIENT -> new RemoteConnection(net.getHost(), net.getPort());
            case SHARED -> {
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + net.getSharedDbPath());
                applyConnectionPragmas(conn);
                // A longer busy_timeout here specifically: this file may genuinely be contended by
                // several PCs at once over a network share, which is slower and less predictable
                // than local multi-threaded contention on the same machine.
                try (Statement s = conn.createStatement()) { s.execute("PRAGMA busy_timeout = 15000"); }
                yield conn;
            }
            case STANDALONE, SERVER -> getLocalConnection();
        };
    }

    /**
     * Always the true local SQLite file, regardless of network mode.
     * Used by NetworkServer to serve client requests from the real database
     * it's hosting — a server must never accidentally proxy to itself or
     * anywhere else, no matter what getConnection() would otherwise return.
     *
     * PRAGMAs foreign_keys and busy_timeout are per-connection settings in
     * SQLite, not persisted in the database file — unlike journal_mode=WAL,
     * which IS a database-level setting that sticks once set. Every DAO in
     * this codebase opens a brand-new connection per call (no pooling), so
     * these must be applied here, on every single connection, or they only
     * ever take effect on the one connection used during initialize() and
     * are silently OFF (SQLite's default) everywhere else in the app —
     * including every declared ON DELETE CASCADE across the schema, which
     * simply never fires without foreign_keys actually being on.
     */
    public Connection getLocalConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        applyConnectionPragmas(conn);
        return conn;
    }

    private void applyConnectionPragmas(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000"); // retry up to 5s on a locked DB instead of failing immediately — matters more now with concurrent server-mode clients
        }
    }

    public void initialize() {
        try {
            // Create data directory in user home
            Files.createDirectories(Paths.get(DB_DIR));
            // Register driver
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
                createTables(stmt);
                createIndexes(stmt);
                createPermissionTables(stmt);
                seedDefaultData(stmt);
                com.kaziflow.network.NetworkConfig net = com.kaziflow.network.NetworkConfig.getInstance();
                String location = switch (net.getMode()) {
                    case CLIENT -> "remote server at " + net.getHost() + ":" + net.getPort();
                    case SHARED -> "shared file at " + net.getSharedDbPath();
                    case STANDALONE, SERVER -> DB_PATH;
                };
                System.out.println("[DB] Database initialized — mode " + net.getMode() + ", location: " + location);
            }
        } catch (Exception e) {
            System.err.println("[DB] Initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables(Statement stmt) throws SQLException {

        // ─── USERS & AUTH ───────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                permissions TEXT DEFAULT '{}',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role_id INTEGER NOT NULL REFERENCES roles(id),
                status TEXT DEFAULT 'active' CHECK(status IN ('active','inactive','suspended')),
                last_login DATETIME,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ─── INVENTORY ──────────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                description TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                address TEXT,
                category TEXT,
                payment_terms INTEGER DEFAULT 30,
                outstanding_balance REAL DEFAULT 0,
                payment_status TEXT DEFAULT 'current' CHECK(payment_status IN ('current','pending','overdue')),
                status TEXT DEFAULT 'active' CHECK(status IN ('active','inactive')),
                notes TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sku TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                description TEXT,
                category_id INTEGER REFERENCES categories(id),
                supplier_id INTEGER REFERENCES suppliers(id),
                selling_price REAL NOT NULL DEFAULT 0,
                cost_price REAL NOT NULL DEFAULT 0,
                stock_quantity REAL DEFAULT 0,
                min_stock_level REAL DEFAULT 0,
                unit TEXT DEFAULT 'pcs',
                barcode TEXT,
                status TEXT DEFAULT 'active' CHECK(status IN ('active','inactive')),
                image_path TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Product pictures. Separate from `products` so list queries never carry image data; stored as base64 TEXT
        // (not BLOB) because the LAN client/server protocol cannot transport binary values. See ImageProcessor.
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS product_images (
                product_id INTEGER PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
                mime       TEXT NOT NULL DEFAULT 'image/jpeg',
                image      TEXT NOT NULL,
                thumb      TEXT NOT NULL,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL REFERENCES products(id),
                movement_type TEXT NOT NULL CHECK(movement_type IN ('in','out','adjustment')),
                quantity REAL NOT NULL,
                reference TEXT,
                notes TEXT,
                created_by INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ─── SALES & POS ────────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                address TEXT,
                total_purchases REAL DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sales (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_number TEXT NOT NULL UNIQUE,
                customer_id INTEGER REFERENCES customers(id),
                customer_name TEXT DEFAULT 'Walk-in Customer',
                subtotal REAL NOT NULL DEFAULT 0,
                discount_amount REAL DEFAULT 0,
                vat_amount REAL DEFAULT 0,
                total_amount REAL NOT NULL DEFAULT 0,
                amount_paid REAL DEFAULT 0,
                change_amount REAL DEFAULT 0,
                payment_method TEXT DEFAULT 'cash' CHECK(payment_method IN ('cash','mpesa','card','bank')),
                mpesa_ref TEXT,
                status TEXT DEFAULT 'completed' CHECK(status IN ('completed','refunded','pending')),
                served_by INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sale_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL REFERENCES products(id),
                product_name TEXT NOT NULL,
                quantity REAL NOT NULL,
                unit_price REAL NOT NULL,
                cost_price REAL NOT NULL DEFAULT 0,
                discount REAL DEFAULT 0,
                line_total REAL NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sale_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                return_number TEXT NOT NULL UNIQUE,
                sale_id INTEGER NOT NULL REFERENCES sales(id),
                sale_number TEXT NOT NULL,
                product_id INTEGER REFERENCES products(id),
                product_name TEXT NOT NULL,
                quantity REAL NOT NULL,
                unit_price REAL NOT NULL,
                refund_amount REAL NOT NULL,
                reason TEXT,
                processed_by INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ─── PURCHASES ──────────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS purchases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                purchase_number TEXT NOT NULL UNIQUE,
                supplier_id INTEGER NOT NULL REFERENCES suppliers(id),
                subtotal REAL NOT NULL DEFAULT 0,
                vat_amount REAL DEFAULT 0,
                total_amount REAL NOT NULL DEFAULT 0,
                amount_paid REAL DEFAULT 0,
                balance REAL DEFAULT 0,
                status TEXT DEFAULT 'pending' CHECK(status IN ('pending','received','partial')),
                payment_method TEXT DEFAULT 'credit',
                payment_status TEXT DEFAULT 'unpaid' CHECK(payment_status IN ('unpaid','partial','paid')),
                due_date DATE,
                notes TEXT,
                created_by INTEGER REFERENCES users(id),
                received_by INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ── Migration: add columns if upgrading from older schema ──
        try { stmt.execute("ALTER TABLE purchases ADD COLUMN status TEXT DEFAULT 'pending'"); } catch (Exception ignored) {}
        try { stmt.execute("ALTER TABLE purchases ADD COLUMN payment_method TEXT DEFAULT 'credit'"); } catch (Exception ignored) {}
        try { stmt.execute("ALTER TABLE purchases ADD COLUMN created_by INTEGER REFERENCES users(id)"); } catch (Exception ignored) {}
        try { stmt.execute("ALTER TABLE purchases ADD COLUMN payment_status TEXT DEFAULT 'unpaid'"); } catch (Exception ignored) {}

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS purchase_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                purchase_id INTEGER NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL REFERENCES products(id),
                product_name TEXT NOT NULL,
                quantity REAL NOT NULL,
                unit_cost REAL NOT NULL,
                line_total REAL NOT NULL
            )
        """);

        // ─── EMPLOYEES & HR ─────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS departments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                description TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS employees (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employee_number TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                email TEXT,
                phone TEXT,
                department_id INTEGER REFERENCES departments(id),
                position TEXT,
                employment_type TEXT DEFAULT 'full-time' CHECK(employment_type IN ('full-time','part-time','contract')),
                salary REAL DEFAULT 0,
                hire_date DATE,
                status TEXT DEFAULT 'active' CHECK(status IN ('active','inactive','on-leave')),
                user_id INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS attendance (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employee_id INTEGER NOT NULL REFERENCES employees(id),
                date DATE NOT NULL,
                check_in TIME,
                check_out TIME,
                status TEXT DEFAULT 'present' CHECK(status IN ('present','absent','late','on-leave')),
                notes TEXT,
                UNIQUE(employee_id, date)
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS leave_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employee_id INTEGER NOT NULL REFERENCES employees(id),
                leave_type TEXT NOT NULL,
                start_date DATE NOT NULL,
                end_date DATE NOT NULL,
                days_count INTEGER DEFAULT 1,
                reason TEXT,
                status TEXT DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected')),
                approved_by INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        try { stmt.execute("ALTER TABLE leave_requests ADD COLUMN days_count INTEGER DEFAULT 1"); } catch (Exception ignored) {}

        // ─── FINANCE / LEDGER ───────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                account_type TEXT NOT NULL CHECK(account_type IN ('asset','liability','equity','revenue','expense')),
                balance REAL DEFAULT 0,
                is_active INTEGER DEFAULT 1
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reference TEXT NOT NULL,
                description TEXT NOT NULL,
                account_id INTEGER REFERENCES accounts(id),
                transaction_type TEXT NOT NULL CHECK(transaction_type IN ('income','expense','transfer')),
                category TEXT,
                amount REAL NOT NULL,
                vat_amount REAL DEFAULT 0,
                payment_method TEXT DEFAULT 'cash',
                notes TEXT,
                related_sale_id INTEGER REFERENCES sales(id),
                related_purchase_id INTEGER REFERENCES purchases(id),
                created_by INTEGER REFERENCES users(id),
                transaction_date DATE NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                amount REAL NOT NULL,
                payment_method TEXT DEFAULT 'cash',
                receipt_number TEXT,
                notes TEXT,
                created_by INTEGER REFERENCES users(id),
                expense_date DATE NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);


        // ─── BATCH TRACKING ──────────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS batches (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id      INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                batch_number    TEXT NOT NULL,
                lot_number      TEXT,
                quantity        REAL NOT NULL DEFAULT 0,
                remaining       REAL NOT NULL DEFAULT 0,
                cost_price      REAL NOT NULL DEFAULT 0,
                manufacture_date DATE,
                expiry_date     DATE NOT NULL,
                supplier_id     INTEGER REFERENCES suppliers(id),
                notes           TEXT,
                status          TEXT DEFAULT 'active'
                                    CHECK(status IN ('active','expired','depleted')),
                created_by      INTEGER REFERENCES users(id),
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(product_id, batch_number)
            )
        """);

        // ─── BRANCHES & STOCK TRANSFERS ─────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS branches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                address TEXT,
                phone TEXT,
                manager TEXT,
                status TEXT DEFAULT 'active',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS stock_transfers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transfer_number TEXT NOT NULL UNIQUE,
                from_branch_id INTEGER REFERENCES branches(id),
                to_branch_id INTEGER REFERENCES branches(id),
                from_branch_name TEXT NOT NULL,
                to_branch_name TEXT NOT NULL,
                product_id INTEGER NOT NULL REFERENCES products(id),
                product_name TEXT NOT NULL,
                quantity REAL NOT NULL,
                status TEXT DEFAULT 'pending' CHECK(status IN ('pending','approved','dispatched','received')),
                notes TEXT,
                created_by INTEGER REFERENCES users(id),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // Seed default head-office branch
        stmt.execute("""
            INSERT OR IGNORE INTO branches (id, name, address, status) VALUES
            (1, 'Head Office', 'Nairobi CBD', 'active'),
            (2, 'Branch — Westlands', 'Westlands, Nairobi', 'active'),
            (3, 'Branch — Mombasa Road', 'Mombasa Road, Nairobi', 'active')
        """);

        // ─── AUDIT LOG ──────────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER REFERENCES users(id),
                user_name TEXT NOT NULL,
                action TEXT NOT NULL,
                description TEXT NOT NULL,
                module TEXT,
                record_id INTEGER,
                ip_address TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);

        // ─── SETTINGS ───────────────────────────────────────────────
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    /**
     * Dynamic roles & permissions (Odoo-style groups, see
     * docs/design/dynamic-roles-and-permissions.md). Its own method rather
     * than folded into createTables() since it was added well after the
     * original schema — same additive-migration philosophy as everything
     * else here: CREATE TABLE IF NOT EXISTS only, old roles/users.role_id
     * untouched so existing data keeps working during migration.
     *
     * Generic-by-design (see PROJECT_GUIDE.md's governing principle and the
     * "generic modular monolith" framing): module_access is keyed by the
     * SAME module_id string Module.getId() already uses, so a newly added
     * module needs zero permission-schema changes to be governable — no
     * per-module special-casing anywhere in this table or the service that
     * reads it. special_permissions applies the identical idea one level
     * further: action_key is an arbitrary string (e.g. "void_sale"), not a
     * fixed enum baked into the schema, so PermissionService can expose one
     * generic canPerform(actionKey) rather than growing a bespoke
     * canVoidSale()/canApplyDiscount()/... method per feature.
     */
    private void createPermissionTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS permission_groups (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT NOT NULL UNIQUE,
                description TEXT,
                is_system   INTEGER NOT NULL DEFAULT 0,
                created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS permission_group_implies (
                group_id   INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
                implied_id INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
                PRIMARY KEY (group_id, implied_id)
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS user_permission_groups (
                user_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                group_id INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
                PRIMARY KEY (user_id, group_id)
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS module_access (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id     INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
                module_id    TEXT NOT NULL,
                can_read     INTEGER NOT NULL DEFAULT 0,
                can_create   INTEGER NOT NULL DEFAULT 0,
                can_write    INTEGER NOT NULL DEFAULT 0,
                can_delete   INTEGER NOT NULL DEFAULT 0,
                UNIQUE(group_id, module_id)
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS special_permissions (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id    INTEGER NOT NULL REFERENCES permission_groups(id) ON DELETE CASCADE,
                action_key  TEXT NOT NULL,
                granted     INTEGER NOT NULL DEFAULT 0,
                UNIQUE(group_id, action_key)
            )
        """);
        // Catalog of the named actions the Positions editor offers (see SpecialPermissions).
        // Data, not code: adding a row adds a checkbox, with no UI change.
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS permission_actions (
                action_key  TEXT PRIMARY KEY,
                label       TEXT NOT NULL,
                description TEXT,
                sort_order  INTEGER NOT NULL DEFAULT 1000
            )
        """);
    }

    /**
     * Creates all performance-critical indexes. Uses IF NOT EXISTS so it is
     * safe to call on every startup (no-ops if already present).
     *
     * Each statement is wrapped individually — Phase 4 tables (appointments,
     * memberships, etc.) are created by their own DAO.ensureTables() AFTER
     * DatabaseManager.initialize(), so their indexes will fail silently on
     * first run and succeed on subsequent startups once the tables exist.
     */
    private void createIndexes(Statement stmt) {
        // Helper: execute one index statement, log but never throw on failure
        java.util.function.Consumer<String> idx = sql -> {
            try { stmt.execute(sql); }
            catch (SQLException e) {
                // Expected for Phase 4 tables on first run — logged at FINE level only
                System.out.println("[DB] Index skipped (table not yet created): " + e.getMessage().split("\\[")[0].trim());
            }
        };

        // ── Core FK indexes ────────────────────────────────────────────────
        idx.accept("CREATE INDEX IF NOT EXISTS idx_users_role_id          ON users(role_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_products_category_id   ON products(category_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_products_supplier_id   ON products(supplier_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements(product_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sales_customer_id      ON sales(customer_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sales_served_by        ON sales(served_by)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id     ON sale_items(sale_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sale_items_product_id  ON sale_items(product_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_purchases_supplier_id  ON purchases(supplier_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase ON purchase_items(purchase_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_purchase_items_product ON purchase_items(product_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_employees_dept_id      ON employees(department_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_attendance_employee_id ON attendance(employee_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_leave_employee_id      ON leave_requests(employee_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_transactions_account   ON transactions(account_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_batches_product_id     ON batches(product_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_audit_log_user_id      ON audit_log(user_id)");

        // ── Date / status filter indexes (most-queried cols per DAO analysis) ──
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sales_created_at       ON sales(created_at)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sales_status           ON sales(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_products_status        ON products(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_expenses_date          ON expenses(expense_date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_transactions_date      ON transactions(transaction_date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_batches_expiry         ON batches(expiry_date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_batches_status         ON batches(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_attendance_date        ON attendance(date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_audit_log_created_at   ON audit_log(created_at)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_audit_log_module       ON audit_log(module)");

        // ── Phase 4 module indexes ─────────────────────────────────────────
        idx.accept("CREATE INDEX IF NOT EXISTS idx_appointments_date      ON appointments(appointment_date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_appointments_status    ON appointments(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_memberships_status     ON memberships(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_memberships_end_date   ON memberships(end_date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_job_cards_status       ON job_cards(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_laundry_status         ON laundry_orders(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_hotel_reservations_status ON hotel_reservations(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_hotel_reservations_checkin ON hotel_reservations(check_in_date)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_fuel_sales_created_at  ON fuel_sales(created_at)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_canteen_accounts_status ON canteen_accounts(status)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_canteen_txn_account    ON canteen_transactions(account_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_patient_encounters     ON patient_encounters(patient_id)");
        idx.accept("CREATE INDEX IF NOT EXISTS idx_restaurant_orders_status ON restaurant_orders(status)");

        // ── Composite indexes for common multi-col queries ─────────────────
        // FEFO batch dispensing: WHERE product_id=? AND status='active' ORDER BY expiry_date
        idx.accept("CREATE INDEX IF NOT EXISTS idx_batches_product_expiry ON batches(product_id, status, expiry_date)");
        // Sales dashboard: WHERE status='completed' AND strftime(created_at) GROUP BY ...
        idx.accept("CREATE INDEX IF NOT EXISTS idx_sales_status_date      ON sales(status, created_at)");
        // Payroll runs: WHERE employee_id=? AND run_id=?
        idx.accept("CREATE INDEX IF NOT EXISTS idx_payroll_emp_run        ON payroll_items(employee_id, run_id)");

        System.out.println("[DB] Indexes created/verified (56 indexes)");
    }

    private void seedDefaultData(Statement stmt) throws SQLException {
        // Seed default roles (if not exist)
        stmt.execute("""
            INSERT OR IGNORE INTO roles (name, permissions) VALUES
            ('Administrator', '{"all": true}'),
            ('Store Manager', '{"inventory": true, "sales": true, "purchases": true, "employees": true, "finance": true, "reports": true}'),
            ('Cashier', '{"sales": true, "inventory": "read"}'),
            ('Warehouse Staff', '{"inventory": true, "purchases": "read"}'),
            ('Accountant', '{"finance": true, "reports": true, "purchases": true}')
        """);

        // Seed default admin user (password: admin123)
        stmt.execute("""
            INSERT OR IGNORE INTO users (name, email, password_hash, role_id) VALUES
            ('Admin User', 'admin@kaziflow.co.ke',
             '$2a$10$uk20E1qUH8sbbyLLCInbKu93AMMJ7/1I.HtdosrpvJV.JEVSft8e2', 1)
        """);

        // Seed default categories
        stmt.execute("""
            INSERT OR IGNORE INTO categories (name) VALUES
            ('Building Materials'), ('Paints & Finishes'), ('Plumbing'),
            ('Tools & Equipment'), ('Electrical'), ('Safety Gear'),
            ('Hardware'), ('Timber & Wood'), ('Steel & Metal'), ('General')
        """);

        // Seed default departments
        stmt.execute("""
            INSERT OR IGNORE INTO departments (name) VALUES
            ('Management'), ('Sales'), ('Workshop'), ('Operations'), ('Finance')
        """);

        // Seed default chart of accounts
        stmt.execute("""
            INSERT OR IGNORE INTO accounts (code, name, account_type) VALUES
            ('1000', 'Current Assets', 'asset'),
            ('1100', 'Cash & Bank', 'asset'),
            ('1200', 'Accounts Receivable', 'asset'),
            ('1300', 'Inventory', 'asset'),
            ('1500', 'Fixed Assets', 'asset'),
            ('2000', 'Liabilities', 'liability'),
            ('2100', 'Accounts Payable', 'liability'),
            ('2200', 'Short-term Loans', 'liability'),
            ('3000', 'Equity', 'equity'),
            ('4000', 'Sales Revenue', 'revenue'),
            ('5000', 'Cost of Goods Sold', 'expense'),
            ('6000', 'Operating Expenses', 'expense'),
            ('6100', 'Payroll & Salaries', 'expense'),
            ('6200', 'Rent & Utilities', 'expense'),
            ('6300', 'Transport & Logistics', 'expense')
        """);

        // Seed default settings
        stmt.execute("""
            INSERT OR IGNORE INTO settings (key, value) VALUES
            ('business_name', 'Kamau Hardware & Supplies'),
            ('business_address', 'Tom Mboya Street, Nairobi CBD'),
            ('business_pin', 'P051234567A'),
            ('currency', 'KES'),
            ('vat_rate', '16'),
            ('timezone', 'Africa/Nairobi'),
            ('mpesa_consumer_key', ''),
            ('mpesa_consumer_secret', ''),
            ('mpesa_till_number', ''),
            ('mpesa_paybill', ''),
            ('mpesa_sandbox', 'true'),
            ('receipt_footer', 'Thank you for shopping with Kamau Hardware!'),
            ('low_stock_alert', 'true'),
            ('auto_backup', 'true'),
            ('backup_frequency', 'daily'),
            ('expiry_alert_days', '30'),
            ('batch_enabled', 'false')
        """);

        // Seed demo suppliers
        stmt.execute("""
            INSERT OR IGNORE INTO suppliers (code, name, email, phone, address, category, payment_terms, status, outstanding_balance) VALUES
            ('SUP-001', 'East Africa Cement Co.', 'sales@eacement.co.ke', '+254711000001', 'Industrial Area, Nairobi', 'Cement', 30, 'active', 450000),
            ('SUP-002', 'Crown Paints Kenya', 'trade@crownpaints.co.ke', '+254711000002', 'Thika Road, Nairobi', 'Paints & Finishes', 45, 'active', 128000),
            ('SUP-003', 'Devki Steel Mills', 'info@devkisteel.co.ke', '+254711000003', 'Athi River, Machakos', 'Steel & Metal', 30, 'active', 0),
            ('SUP-004', 'Kenya Plumbers Ltd', 'orders@kenyaplumbers.co.ke', '+254722000004', 'Ngong Road, Nairobi', 'Plumbing', 14, 'active', 75000),
            ('SUP-005', 'Orbit Hardware', 'info@orbithardware.co.ke', '+254733000005', 'Mombasa Road, Nairobi', 'Hardware', 30, 'active', 0)
        """);

        // Seed demo employees
        stmt.execute("""
            INSERT OR IGNORE INTO employees (employee_number, name, email, phone, department_id, position, employment_type, salary, hire_date, status) VALUES
            ('EMP-001', 'James Kamau', 'james.kamau@kaziflow.co.ke', '+254712345601', 1, 'General Manager', 'full-time', 120000, '2020-01-15', 'active'),
            ('EMP-002', 'Mary Wanjiku', 'mary.wanjiku@kaziflow.co.ke', '+254712345602', 2, 'Sales Manager', 'full-time', 85000, '2021-03-01', 'active'),
            ('EMP-003', 'Peter Otieno', 'peter.otieno@kaziflow.co.ke', '+254712345603', 2, 'Sales Associate', 'full-time', 45000, '2022-06-15', 'active'),
            ('EMP-004', 'Grace Muthoni', 'grace.muthoni@kaziflow.co.ke', '+254712345604', 5, 'Accountant', 'full-time', 70000, '2021-09-01', 'active'),
            ('EMP-005', 'David Kipchoge', 'david.kipchoge@kaziflow.co.ke', '+254712345605', 3, 'Workshop Supervisor', 'full-time', 65000, '2020-11-01', 'active'),
            ('EMP-006', 'Faith Achieng', 'faith.achieng@kaziflow.co.ke', '+254712345606', 4, 'Storekeeper', 'full-time', 40000, '2023-01-10', 'active')
        """);

        // Seed demo products
        stmt.execute("""
            INSERT OR IGNORE INTO products (sku, name, category_id, supplier_id, selling_price, cost_price, stock_quantity, min_stock_level, unit, status) VALUES
            ('CEM-001', 'Bamburi Cement 50kg', 1, 1, 780, 620, 450, 50, 'bags', 'active'),
            ('CEM-002', 'Savanna Cement 50kg', 1, 1, 760, 600, 280, 50, 'bags', 'active'),
            ('PAINT-001', 'Crown Paint Supercover 4L White', 2, 2, 2850, 2100, 85, 10, 'pcs', 'active'),
            ('PAINT-002', 'Crown Paint Supercover 4L Cream', 2, 2, 2850, 2100, 62, 10, 'pcs', 'active'),
            ('PAINT-003', 'Crown Silk Vinyl 20L White', 2, 2, 8500, 6200, 34, 5, 'pcs', 'active'),
            ('STEEL-001', '6mm Deformed Bar 12m', 6, 3, 680, 530, 320, 30, 'pcs', 'active'),
            ('STEEL-002', '8mm Deformed Bar 12m', 6, 3, 980, 760, 280, 20, 'pcs', 'active'),
            ('STEEL-003', '12mm Deformed Bar 12m', 6, 3, 1850, 1450, 150, 15, 'pcs', 'active'),
            ('PIPE-001', '1/2" PVC Pipe 6m', 3, 4, 320, 240, 180, 20, 'pcs', 'active'),
            ('PIPE-002', '1" PVC Pipe 6m', 3, 4, 480, 360, 120, 15, 'pcs', 'active'),
            ('PIPE-003', 'Water Tank 500L Roto', 3, 4, 12500, 9800, 12, 3, 'pcs', 'active'),
            ('HARDWARE-001', 'Wire Nails 4" 1kg', 7, 5, 180, 130, 5, 10, 'kg', 'active'),
            ('HARDWARE-002', 'Binding Wire 2kg Roll', 7, 5, 420, 310, 48, 10, 'rolls', 'active'),
            ('TOOLS-001', 'Wheelbarrow Heavy Duty', 4, 5, 4800, 3600, 8, 2, 'pcs', 'active'),
            ('TOOLS-002', 'Trowel Stainless 12"', 4, 5, 650, 480, 25, 5, 'pcs', 'active')
        """);

        // Seed demo transactions (revenue & expenses for reports)
        stmt.execute("""
            INSERT OR IGNORE INTO transactions (reference, description, account_id, transaction_type, category, amount, payment_method, transaction_date, created_by) VALUES
            ('TXN-001', 'Opening balance', 2, 'income', 'Capital', 500000, 'bank transfer', date('now', '-60 days'), 1),
            ('TXN-002', 'Cement sales - batch', 10, 'income', 'Sales Revenue', 234000, 'cash', date('now', '-30 days'), 1),
            ('TXN-003', 'Paint & hardware sales', 10, 'income', 'Sales Revenue', 189500, 'mpesa', date('now', '-25 days'), 1),
            ('TXN-004', 'Payroll October', 11, 'expense', 'Payroll', 425000, 'bank transfer', date('now', '-20 days'), 1),
            ('TXN-005', 'Rent - CBD October', 12, 'expense', 'Rent', 85000, 'bank transfer', date('now', '-18 days'), 1),
            ('TXN-006', 'Steel bar sales - contractor', 10, 'income', 'Sales Revenue', 312000, 'mpesa', date('now', '-15 days'), 1),
            ('TXN-007', 'Electricity bill', 12, 'expense', 'Utilities', 18500, 'mpesa', date('now', '-10 days'), 1),
            ('TXN-008', 'Hardware & tools sales', 10, 'income', 'Sales Revenue', 127500, 'cash', date('now', '-7 days'), 1),
            ('TXN-009', 'Supplier payment - Devki Steel', 11, 'expense', 'Purchases', 156000, 'bank transfer', date('now', '-5 days'), 1),
            ('TXN-010', 'Pipe & plumbing sales', 10, 'income', 'Sales Revenue', 89000, 'cash', date('now', '-2 days'), 1)
        """);

        // Seed demo expenses
        stmt.execute("""
            INSERT OR IGNORE INTO expenses (description, category, amount, payment_method, expense_date, created_by) VALUES
            ('Monthly rent - Tom Mboya Street', 'Rent', 85000, 'bank transfer', date('now', '-30 days'), 1),
            ('Staff payroll October', 'Payroll', 425000, 'bank transfer', date('now', '-20 days'), 1),
            ('KPLC electricity bill', 'Utilities', 18500, 'mpesa', date('now', '-10 days'), 1),
            ('Vehicle fuel - delivery truck', 'Transport', 12000, 'cash', date('now', '-8 days'), 1),
            ('Office supplies & stationery', 'Supplies', 5500, 'cash', date('now', '-5 days'), 1),
            ('Internet & phone bill', 'Utilities', 4200, 'mpesa', date('now', '-3 days'), 1),
            ('Nairobi Water & Sewerage', 'Utilities', 3800, 'mpesa', date('now', '-15 days'), 1),
            ('Fire extinguisher service', 'Maintenance', 8500, 'cash', date('now', '-25 days'), 1)
        """);
    }
}
