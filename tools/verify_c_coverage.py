#!/usr/bin/env python3
"""Sanity checks for C-grade signal coverage assets."""
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"

rows = []
ids = set()
regions = Counter()
with (ASSETS / "intersections_c.psv").open(encoding="utf-8") as f:
    for line_no, line in enumerate(f, 1):
        if not line.strip() or line.startswith("#"):
            continue
        p = line.rstrip("\n").split("|")
        assert len(p) >= 7, f"line {line_no}: expected 7 fields"
        icid, name, lon, lat, region, grade, source = p[:7]
        assert icid.startswith("C-"), f"line {line_no}: non-C id {icid}"
        assert grade == "C", f"line {line_no}: wrong grade {grade}"
        assert source, f"line {line_no}: missing source"
        assert icid not in ids, f"duplicate id {icid}"
        ids.add(icid)
        lon, lat = float(lon), float(lat)
        assert 119.0 <= lon <= 123.0 and 21.5 <= lat <= 26.5, f"bad coord {icid}"
        assert name.strip(), f"empty name {icid}"
        regions[region] += 1
        rows.append(icid)

assert regions["NTPC"] >= 2000, f"unexpected NTPC coverage: {regions['NTPC']}"
assert regions["KLC"] >= 200, f"unexpected KLC coverage: {regions['KLC']}"

plan_ids = set()
for filename in ("signal_plans.psv", "signal_schedule.psv"):
    with (ASSETS / filename).open(encoding="utf-8") as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                plan_ids.add(line.split("|", 1)[0].strip())

collision = ids & plan_ids
assert not collision, f"C-grade IDs leaked into timing data: {sorted(collision)[:10]}"

print(f"PASS: {len(rows)} C-grade anchors; NTPC={regions['NTPC']}, KLC={regions['KLC']}")
print("PASS: no C-grade ID appears in timing plan/schedule assets")
