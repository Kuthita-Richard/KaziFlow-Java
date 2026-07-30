# v10 — Critical Bug Fixes

**Task:** Fix verified bugs found by inspecting the actual v9 code (not the conversation transcript's self-reported status).

## 1. POS numpad had no click handlers
- **Where:** `src/main/java/com/kaziflow/views/SalesView.java`
- **Problem:** The numeric keypad on the POS screen was built and styled but no button had `.setOnAction()` wired up. Clicking any key did nothing. No `cashTenderedField` or `currentTotal` tracking existed.
- **Fix:** Added `cashTenderedField`, `changeLabel`, `currentTotal`. Wired every numpad button to `onNumpadKey()`. Added a Cash Tendered / Change row to the totals panel, shown only when "Cash" is the selected payment method. `completeSale()` now reads the real tendered amount for cash sales, blocks the sale if insufficient, and records real change instead of hardcoding `amountPaid = total, change = 0`.

## 2. First-run profile selection was dead code
- **Where:** `src/main/java/com/kaziflow/App.java`
- **Problem:** `if (isFirstRun()) { showLogin(); } else { showLogin(); }` — both branches called the same method. `ProfileSelector` existed and worked but was never reachable from normal startup.
- **Fix:** First-run branch now calls `sm.showProfileSelector()`.

## 3. Found while fixing #2: profile selection skipped login entirely
- **Where:** `src/main/java/com/kaziflow/utils/SceneManager.java`
- **Problem:** `showProfileSelector()`'s completion callback pointed straight at `showMainApp()`. A first-time user would land inside the app with no session set in `SessionManager`, breaking anything calling `getCurrentUser()`.
- **Fix:** Completion callback now routes to `showLogin()` instead. Flow is now: pick profile → log in → app.
