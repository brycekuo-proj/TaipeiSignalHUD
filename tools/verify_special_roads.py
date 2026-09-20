#!/usr/bin/env python3
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/special_roads.psv"

rows = []
for n, line in enumerate(ASSET.read_text(encoding="utf-8").splitlines(), 1):
    if not line or line.startswith("#"):
        continue
    p = line.split("|")
    assert len(p) == 13, (n, len(p))
    float(p[7]); float(p[8]); float(p[9]); float(p[10])
    assert p[1] in {"TPE", "NTPC", "KLC"}, p
    assert p[2] in {"HIGHWAY", "EXPRESSWAY", "URBAN"}, p
    assert p[3] in {"MAINLINE", "ELEVATED", "TUNNEL", "RAMP", "COMPLEX"}, p
    rows.append(p)

assert len(rows) > 15000
assert all(sum(1 for r in rows if r[1] == region) > 1000
           for region in ("TPE", "NTPC", "KLC"))

names = "\n".join(r[4] for r in rows if r[4])
refs = Counter((r[1], r[5]) for r in rows if r[5])

for token in ("水源快速道路", "市民大道", "建國", "環東", "新生"):
    assert token in names, token
for token in ("新北環河", "環河快速"):
    assert token in names, token
assert "東岸高架" in names

for key in [
    ("TPE", "1"), ("TPE", "3"), ("TPE", "3甲"), ("TPE", "5"),
    ("NTPC", "1"), ("NTPC", "3"), ("NTPC", "64"), ("NTPC", "65"),
    ("KLC", "1"), ("KLC", "3"), ("KLC", "62"), ("KLC", "62甲"),
]:
    assert refs[key] > 0, key

print("segments", len(rows))
print("regions", dict(Counter(r[1] for r in rows)))
print("classes", dict(Counter(r[2] for r in rows)))
print("structures", dict(Counter(r[3] for r in rows)))
print("PASS: special-road coverage sanity checks")
