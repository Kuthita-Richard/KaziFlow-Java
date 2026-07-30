# v11 — Dashboard Drill-Through/Filtering & In-App Calculator

**Task:** "Do everything" on the Phase 10 TODO list — this is the first batch.

## Dashboard: date-range filter, drill-through, top-10 lists, cash flow (Phase 10C)
- **Where:** `src/main/java/com/kaziflow/views/DashboardView.java`, `src/main/java/com/kaziflow/dao/SaleDAO.java`, `src/main/java/com/kaziflow/dao/TransactionDAO.java`
- New `SaleDAO` methods: `findByDateRange`, `getRevenueAndCountForRange`, `getDailyRevenueForRange`, `getTopProducts`, `getTopCustomers`.
- New `TransactionDAO` method: `getDailyCashFlowForRange`.
- Sales chart rebuilt: real `DatePicker` date-range filter (default last 7 days), replacing the old hardcoded "this week" approximation (`count × average sale value`) with actual per-day SQL revenue.
- Clicking any bar opens a drill-through dialog (`showDayDrillThrough`) listing every real sale that day.
- New Top 10 Customers card and Cash Flow chart (income vs. expense, line chart, last 14 days).

## Bonus fixes found while building the above
- **"Top Selling" card** was showing hardcoded sample products (Dulux Paint, Safety Boots, Solar Panel) — now pulls real top-5-by-revenue from the last 30 days via `getTopProducts`.
- **"Outstanding Payments" card** was showing hardcoded fake customers (Mombasa Cement Ltd, Crown Paints...) — now pulls real unpaid/partial balances from `DeniDAO.findAll("unpaid_and_partial")`, with overdue/due-soon/pending computed from actual days elapsed.

## In-app calculator — Ctrl+K (Phase 10E, item 1/6)
- **Where:** new `src/main/java/com/kaziflow/views/CalculatorDialog.java`, `src/main/java/com/kaziflow/utils/SceneManager.java`, `src/main/java/com/kaziflow/views/HelpView.java`
- Standalone floating calculator dialog, chained arithmetic (+ − × ÷ % ⌫ C).
- Wired to Ctrl+K globally in `SceneManager.wireKeyboardShortcuts()`.
- Added to `HelpView`'s documented shortcut list so the docs don't drift from the code.
