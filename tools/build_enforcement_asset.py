#!/usr/bin/env python3
import csv
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/enforcement_points.psv"
HIGHWAY_APPEND = ROOT / "data/derived/highway_speed_cameras_npa.psv"

TAIPEI_SPEED = ROOT / "data/raw/taipei_speed_cameras.csv"
TAIPEI_TECH = ROOT / "data/raw/taipei_tech_enforcement.csv"

NTPC_SPEED = ROOT / "data/raw/new_taipei/ntpc_speed_cameras.csv"
NTPC_TECH = ROOT / "data/raw/new_taipei/ntpc_intersection_safety_detection.csv"
NTPC_SECTION = ROOT / "data/raw/new_taipei/ntpc_section_speed_enforcement.csv"

KEELUNG_SPEED = ROOT / "data/raw/keelung/keelung_fixed_speed_cameras.csv"
KEELUNG_TECH = ROOT / "data/raw/keelung/keelung_tech_enforcement.csv"
KEELUNG_SECTION = ROOT / "data/raw/keelung/keelung_section_speed_enforcement.csv"


def clean(v):
    return (v or "").replace("|", "／").replace("\r", " ").replace("\n", " ").strip()


def add(rows, ident, kind, lat, lon, title, detail="", direction="", limit=""):
    try:
        lat = float(lat)
        lon = float(lon)
    except (TypeError, ValueError):
        return
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        return
    rows.append([
        clean(ident),
        clean(kind),
        f"{lat:.8f}",
        f"{lon:.8f}",
        clean(title),
        clean(detail),
        clean(direction),
        clean(limit),
    ])


def bearing_deg(lat1, lon1, lat2, lon2):
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    y = math.sin(dl) * math.cos(p2)
    x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360.0) % 360.0


def floats(text):
    out = []
    for part in clean(text).replace(",", " ").split():
        try:
            out.append(float(part))
        except ValueError:
            pass
    return out


rows = []

# Taipei City fixed cameras.
with TAIPEI_SPEED.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        function = clean(r.get("功能"))
        if "測速" not in function and "闖紅燈" not in function:
            continue
        kind = "SPEED" if "測速" in function else "RED_LIGHT"
        title = "測速照相" if kind == "SPEED" else "闖紅燈照相"
        place = " ".join(x for x in [clean(r.get("設置路段")), clean(r.get("設置地點"))] if x)
        add(rows, "TP-S-" + clean(r.get("編號")), kind, r.get("緯度"), r.get("經度"),
            title, place, r.get("拍攝方向"), r.get("速限-速度限制"))

# Taipei City technology enforcement.
with TAIPEI_TECH.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        detail = clean(r.get("設置地點（路口或路段）"))
        item = clean(r.get("取締項目"))
        if detail and item:
            detail += "；" + item
        add(rows, "TP-T-" + clean(r.get("編號")), "TECH",
            r.get("座標緯度"), r.get("座標經度"),
            "科技執法", detail)

# New Taipei fixed speed cameras.
with NTPC_SPEED.open("r", encoding="utf-8-sig", newline="") as f:
    for r in csv.DictReader(f):
        detail = " ".join(x for x in [clean(r.get("regionname")), clean(r.get("address"))] if x)
        add(rows, "NTP-S-" + clean(r.get("seqno")), "SPEED",
            r.get("latitude"), r.get("longitude"),
            "測速照相", detail, r.get("direct"), r.get("limit"))

# New Taipei intersection safety / automated enforcement.
with NTPC_TECH.open("r", encoding="utf-8-sig", newline="") as f:
    for r in csv.DictReader(f):
        detail = " ".join(x for x in [clean(r.get("regionname")), clean(r.get("location"))] if x)
        item = clean(r.get("item"))
        if detail and item:
            detail += "；" + item
        add(rows, "NTP-T-" + clean(r.get("seqno")), "TECH",
            r.get("latitude"), r.get("longitude"),
            "科技執法", detail, r.get("direct"))

# New Taipei section-speed enforcement.
# One official row can contain several real entrances (mainline/ramp/both directions).
# Emit one SECTION point per start coordinate and derive its direction from its paired end.
with NTPC_SECTION.open("r", encoding="utf-8-sig", newline="") as f:
    for r in csv.DictReader(f):
        start_lats = floats(r.get("start latitude"))
        start_lons = floats(r.get("start longitude"))
        end_lats = floats(r.get("end latitude"))
        end_lons = floats(r.get("end longitude"))
        n = min(len(start_lats), len(start_lons), len(end_lats), len(end_lons))
        detail = " ".join(x for x in [clean(r.get("regionname")), clean(r.get("location"))] if x)
        item = clean(r.get("item"))
        if detail and item:
            detail += "；" + item
        for i in range(n):
            direction = f"BEARING:{bearing_deg(start_lats[i], start_lons[i], end_lats[i], end_lons[i]):.1f}"
            add(rows, f"NTP-Q-{clean(r.get('seqno'))}-{i + 1}", "SECTION",
                start_lats[i], start_lons[i], "區間測速",
                detail, direction, r.get("limit"))

# Keelung fixed enforcement cameras.
with KEELUNG_SPEED.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        item = clean(r.get('取締項目(以"、"分隔)'))
        if "測速" in item:
            kind, title = "SPEED", "測速照相"
        elif "闖紅燈" in item:
            kind, title = "RED_LIGHT", "闖紅燈照相"
        else:
            kind, title = "TECH", "科技執法"
        detail = clean(r.get("設置地點(路口或路段)"))
        if item and item not in title:
            detail = detail + ("；" if detail else "") + item
        add(rows, "KEE-S-" + clean(r.get("設備編號")), kind,
            r.get("座標緯度"), r.get("座標經度"),
            title, detail, r.get("拍攝方向"), r.get("速限"))

# Keelung technology enforcement.
with KEELUNG_TECH.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        detail = clean(r.get("設置地點(路口或路段)"))
        item = clean(r.get('取締項目(以"、"分隔)'))
        if detail and item:
            detail += "；" + item
        add(rows, "KEE-T-" + clean(r.get("設備編號")), "TECH",
            r.get("座標緯度"), r.get("座標經度"),
            "科技執法", detail, r.get("拍攝方向"), r.get("速限"))

# Keelung section-speed enforcement.
with KEELUNG_SECTION.open("r", encoding="big5", newline="") as f:
    for r in csv.DictReader(f):
        detail = clean(r.get("設置地點(路口或路段)"))
        add(rows, "KEE-Q-" + clean(r.get("設備編號")), "SECTION",
            r.get("座標緯度"), r.get("座標經度"),
            "區間測速", detail, r.get("拍攝方向"), r.get("速限"))

OUT.parent.mkdir(parents=True, exist_ok=True)
with OUT.open("w", encoding="utf-8", newline="\n") as f:
    f.write("# id|type|lat|lon|title|detail|direction|limit\n")
    for row in rows:
        f.write("|".join(clean(v) for v in row) + "\n")
    if HIGHWAY_APPEND.exists():
        for line in HIGHWAY_APPEND.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                f.write(line.rstrip("\n") + "\n")

extra = 0
if HIGHWAY_APPEND.exists():
    extra = sum(1 for line in HIGHWAY_APPEND.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#"))
print(f"wrote {len(rows) + extra} enforcement points -> {OUT}")
