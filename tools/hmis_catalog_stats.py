#!/usr/bin/env python3
"""Reproducible statistics over docs/reference/hmis_menu_catalog.tsv (the HMIS sidebar, 405 entries).
Usage: python3 tools/hmis_catalog_stats.py"""
import collections, csv, os, re

path = os.path.join(os.path.dirname(__file__), "..", "docs", "reference", "hmis_menu_catalog.tsv")
rows = [r for r in csv.reader((l for l in open(path, encoding="utf-8") if not l.startswith("#")), delimiter="\t")][1:]
N = len(rows)
KIND = {"M": "Register a master record", "L": "List / view", "T": "Transaction / data entry", "W": "Workflow action",
        "R": "Report", "S": "Setting / configuration", "Q": "Live work queue"}
DOMAIN = {
    "Clinical (patient-facing)": ["Appointments", "Patients", "Lab", "Radiology", "Nursing Station", "Pharmacy", "Procedures", "Physiotherapy", "Mortuary"],
    "Billing & insurance": ["Price List", "Insurance Payments", "Donations", "General Receipts", "Charges", "Patient Invoices", "Insurance Credit Notes & Deductions", "Collections Reports", "Ministry Of Health Reports"],
    "Configuration (Administration)": ["Administration", "Navigation Style"],
    "HR, payroll & leave": ["Register Payroll Items", "Payroll Transactions", "View Payroll Items", "View Payroll Transactions", "Payroll Reports", "Leave Management", "Performance Management", "Payslips, Leave Requests & Self Evaluation"],
    "Accounting & banking": ["Chart of Accounts", "Opening Balances", "General Journal", "Banking", "Petty Cash", "Bank Loans", "Accounting Reports"],
    "Master data (general)": ["Taxes", "Terms Of Payment", "Payment Methods", "Suppliers", "Departments", "Cost Centres", "Items", "Assets", "Vehicles & Mileage"],
    "Procurement & stores": ["Money Out", "Requisitions", "Stores", "Inventory Reports"],
    "Messaging & management": ["Send SMS", "Management Reports"],
}
dom_of = {m: d for d, ms in DOMAIN.items() for m in ms}
assert all(r[0] in dom_of for r in rows), [r[0] for r in rows if r[0] not in dom_of]

def pct(n, d=N): return f"{100*n/d:4.1f}%"
print(f"{N} entries in {len(set(r[0] for r in rows))} top-level modules\n")

print("By kind of entry")
kc = collections.Counter(r[1] for r in rows)
for k, n in kc.most_common(): print(f"  {KIND[k]:28} {n:4}  {pct(n)}")

print("\nBy domain (and whether it depends on being a hospital)")
dc = collections.Counter(dom_of[r[0]] for r in rows)
for d, n in dc.most_common(): print(f"  {d:34} {n:4}  {pct(n)}")
general = N - dc["Clinical (patient-facing)"]
print(f"  -> everything except 'Clinical' ({general} entries, {pct(general)}) is not specific to a hospital")

print("\nMaster-data pairs (Register X + View X): the pure-CRUD part")
m = kc["M"]
print(f"  {m} 'register' entries; most are immediately followed by their own 'view' list")
crud = kc["M"] + kc["L"]
print(f"  master + list entries together: {crud} ({pct(crud)}); plus 'Q' queues are lists too")

print("\nState-transition vocabulary (entries whose NAME is about a lifecycle step)")
words = {"Confirm": r"confirm", "Reverse / Reversal": r"revers", "Finalize": r"finali", "Dispatch": r"dispatch", "Batch": r"batch",
         "Close": r"\bclose", "Cancel": r"cancel", "Reconcile": r"reconcil", "'Yet To Be ...'": r"yet to be", "Unbalanced": r"unbalanced",
         "Approval": r"approv", "Posted": r"posted"}
lifecycle = set()
for label, rx in words.items():
    hits = [i for i, r in enumerate(rows) if re.search(rx, r[2], re.I)]
    lifecycle.update(hits)
    print(f"  {label:20} {len(hits):3}")
print(f"  -> {len(lifecycle)} distinct entries ({pct(len(lifecycle))}) exist because records move through generic workflow states")
adm = [i for i, r in enumerate(rows) if re.search(r"discharg|admit|admission", r[2], re.I)]
print(f"  (not counted: {len(adm)} admit/discharge entries — a hospital-specific state, reported separately)")
yet = [r for r in rows if re.search(r"yet to be", r[2], re.I)]
print(f"  of which {len(yet)} are 'Yet To Be ...' reports: a status filter over a list, nothing more")

print("\nThe same report shapes repeated across modules")
for label, rx in [("Stock report", r"^stock report|stock report \("), ("Stock movement", r"stock movement"), ("Reorder", r"re-?order"),
                  ("Stock take", r"stock take"), ("Batch expiry", r"expiry"), ("Prices list", r"prices?$")]:
    hits = [r for r in rows if re.search(rx, r[2], re.I)]
    print(f"  {label:16} {len(hits):2} entries in {len(set(r[0] for r in hits))} modules: {', '.join(sorted(set(r[0] for r in hits)))}")

print("\nPayer / branch variants (pricing keyed by who pays and where)")
for label, rx in [("insurance / scheme", r"insurance|scheme|nhif"), ("corporate / company", r"corporate|company"), ("branch", r"branch"), ("copay / allowance / discount", r"copay|allowance|discount|withholding")]:
    hits = [r for r in rows if re.search(rx, r[2], re.I)]
    print(f"  {label:30} {len(hits):3}")

print("\nSettings (kind S): what the HMIS lets an administrator configure")
for r in rows:
    if r[1] == "S": print(f"  [{r[0]}] {r[2]}")

print("\nFraud / control reports at the cash desk")
for r in rows:
    if re.search(r"cancel|edited|refund|activity logs|per shift|close shift|deposit", r[2], re.I): print(f"  [{r[0]}] {r[2]}")
