#!/usr/bin/env python3
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEED = ROOT / "data/raw/taipei_speed_cameras.csv"
TECH = ROOT / "data/raw/taipei_tech_enforcement.csv"
OUT = ROOT / "app/src/main/assets/enforcement_points.psv"

def clean(v):
    return (v or "").replace("|", "／").replace("\r", " ").replace("\n", " ").strip()

rows = []

with SPEED.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        try:
            lat = float(r["緯度"])
            lon = float(r["經度"])
        except (KeyError, TypeError, ValueError):
            continue
        function = clean(r.get("功能"))
        if "測速" not in function and "闖紅燈" not in function:
            continue
        kind = "SPEED" if "測速" in function else "RED_LIGHT"
        title = "測速照相" if kind == "SPEED" else "闖紅燈照相"
        place = " ".join(x for x in [clean(r.get("設置路段")), clean(r.get("設置地點"))] if x)
        rows.append([
            "TP-S-" + clean(r.get("編號")),
            kind,
            f"{lat:.8f}",
            f"{lon:.8f}",
            title,
            place,
            clean(r.get("拍攝方向")),
            clean(r.get("速限-速度限制")),
        ])

with TECH.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        try:
            lat = float(r["座標緯度"])
            lon = float(r["座標經度"])
        except (KeyError, TypeError, ValueError):
            continue
        detail = clean(r.get("取締項目"))
        place = clean(r.get("設置地點（路口或路段）"))
        if detail and place:
            detail = place + "；" + detail
        elif place:
            detail = place
        rows.append([
            "TP-T-" + clean(r.get("編號")),
            "TECH",
            f"{lat:.8f}",
            f"{lon:.8f}",
            "科技執法",
            detail,
            "",
            "",
        ])

OUT.parent.mkdir(parents=True, exist_ok=True)
with OUT.open("w", encoding="utf-8", newline="\n") as f:
    f.write("# id|type|lat|lon|title|detail|direction|limit\n")
    for row in rows:
        f.write("|".join(clean(v) for v in row) + "\n")

print(f"wrote {len(rows)} enforcement points -> {OUT}")
