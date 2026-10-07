#!/usr/bin/env python3
"""How deep is each module TODAY? Reproducible structural measurements (no opinions).

  python3 tools/depth_audit.py            # table
  python3 tools/depth_audit.py --csv      # machine-readable

For each module it finds the screen(s) the module opens, then counts what indicates depth:
  tabs        TabPane tabs / Tab(...) the user can switch between
  charts      Line/Bar/Pie/Area charts (visual analytics)
  tables      TableView grids
  dialogs     Dialog / Alert-with-form flows (create / edit / action forms)
  filters     ComboBox / DatePicker / search TextFields (ways to slice the data)
  dao calls   distinct DAO classes the screen uses, and how many query methods they expose
  loc         non-blank lines of code in the screen(s)
"""
import glob, os, re, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "java", "com", "kaziflow")
def read(p): return open(p, encoding="utf-8", errors="ignore").read()

def metrics(src):
    code = "\n".join(l for l in src.splitlines() if l.strip() and not l.strip().startswith(("//", "*", "/*")))
    return {
        "loc": len(code.splitlines()),
        "tabs": len(re.findall(r"new Tab\(|\.getTabs\(\)\.add|new TabPane", code)) - len(re.findall(r"new TabPane", code)) if re.search(r"new Tab\(", code) else 0,
        "charts": len(re.findall(r"new (Line|Bar|Pie|Area|Scatter|StackedBar|StackedArea)Chart", code)),
        "tables": len(re.findall(r"new TableView", code)),
        "dialogs": len(re.findall(r"new Dialog<|new Alert\(", code)),
        "filters": len(re.findall(r"new ComboBox|new DatePicker|setPromptText\(\"[^\"]*[Ss]earch", code)),
    }

dao_methods = {}
for f in glob.glob(os.path.join(ROOT, "dao", "*DAO.java")):
    name = os.path.basename(f)[:-5]
    dao_methods[name] = len(re.findall(r"^\s*public\s+(?!static\s+\w+\s+getInstance)[\w<>\[\], ?]+\s+\w+\(", read(f), re.M))

rows = []
for mf in sorted(glob.glob(os.path.join(ROOT, "modules", "core", "*Module.java"))):
    msrc = read(mf)
    mid = re.search(r'getId\(\)\s*\{\s*return\s*"([^"]+)"', msrc)
    views = re.findall(r"new (\w+View)\(", msrc)
    if not mid: continue
    agg = {"loc": 0, "tabs": 0, "charts": 0, "tables": 0, "dialogs": 0, "filters": 0}
    daos, seen = set(), set()
    stack = list(views)
    while stack:
        v = stack.pop()
        if v in seen: continue
        seen.add(v)
        vf = os.path.join(ROOT, "views", v + ".java")
        if not os.path.exists(vf): continue
        s = read(vf)
        for k, n in metrics(s).items(): agg[k] += n
        daos |= set(re.findall(r"new (\w+DAO)\(", s))
        stack += [x for x in re.findall(r"new (\w+(?:View|Dialog))\(", s) if x != v and os.path.exists(os.path.join(ROOT, "views", x + ".java"))]
    rows.append((mid.group(1), sorted(seen), agg, sorted(daos), sum(dao_methods.get(d, 0) for d in daos)))

if "--csv" in sys.argv:
    print("module,loc,tabs,charts,tables,dialogs,filters,daos,dao_methods")
    for m, v, a, d, n in rows: print(f"{m},{a['loc']},{a['tabs']},{a['charts']},{a['tables']},{a['dialogs']},{a['filters']},{len(d)},{n}")
else:
    print(f"{'module':14}{'loc':>6}{'tabs':>6}{'charts':>8}{'tables':>8}{'dialogs':>9}{'filters':>9}{'daos':>6}{'dao methods':>13}   screens")
    for m, v, a, d, n in sorted(rows, key=lambda r: -r[2]["loc"]):
        print(f"{m:14}{a['loc']:>6}{a['tabs']:>6}{a['charts']:>8}{a['tables']:>8}{a['dialogs']:>9}{a['filters']:>9}{len(d):>6}{n:>13}   {', '.join(x.replace('View','') for x in v[:4])}")
    t = {k: sum(r[2][k] for r in rows) for k in rows[0][2]}
    print(f"{'TOTAL':14}{t['loc']:>6}{t['tabs']:>6}{t['charts']:>8}{t['tables']:>8}{t['dialogs']:>9}{t['filters']:>9}")
