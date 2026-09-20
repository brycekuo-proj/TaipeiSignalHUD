#!/usr/bin/env python3
"""Build special-road line-segment geometry for Taipei/New Taipei/Keelung.

Sources: OpenStreetMap contributors (ODbL 1.0).
The asset is used only for local road-level classification / false-signal suppression.
"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
import requests

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw" / "osm"
OUT = ROOT / "app" / "src" / "main" / "assets" / "special_roads.psv"

CITIES = {
    "TPE": {"name": "臺北市", "area_id": 3601293250, "raw": RAW_DIR / "taipei_special_roads_2026-09-20.json"},
    "NTPC": {"name": "新北市", "area_id": 3601527220, "raw": RAW_DIR / "new_taipei_special_roads_2026-09-20.json"},
    "KLC": {"name": "基隆市", "area_id": 3601296154, "raw": RAW_DIR / "keelung_special_roads_2026-09-20.json"},
}

ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
USER_AGENT = "TaipeiSignalHUD-SpecialRoads/0.1"


def clean(v):
    return str(v or "").replace("|", "／").replace("\r", " ").replace("\n", " ").strip()


def fetch(city, force=False):
    path = city["raw"]
    if path.exists() and not force:
        return json.loads(path.read_text(encoding="utf-8"))

    area = city["area_id"]
    major = "^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link)$"
    queries = [
        f'[out:json][timeout:180];way(area:{area})["highway"~"^(motorway|motorway_link|trunk|trunk_link)$"];out tags geom;',
        f'[out:json][timeout:180];way(area:{area})["highway"~"{major}"]["bridge"];out tags geom;',
        f'[out:json][timeout:180];(way(area:{area})["highway"~"{major}"]["tunnel"];way(area:{area})["highway"~"{major}"]["covered"="yes"];way(area:{area})["highway"~"{major}"]["layer"];);out tags geom;',
    ]

    merged = {}
    err = None
    for query in queries:
        batch = None
        for endpoint in ENDPOINTS:
            try:
                res = requests.post(endpoint, data={"data": query},
                                    headers={"User-Agent": USER_AGENT}, timeout=240)
                res.raise_for_status()
                batch = res.json()
                break
            except Exception as exc:
                err = exc
                time.sleep(2)
        if batch is None:
            raise RuntimeError(f"unable to download OSM special roads: {err}")
        for element in batch.get("elements", []):
            if element.get("type") == "way":
                merged[int(element["id"])] = element

    payload = {"elements": list(merged.values())}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                    encoding="utf-8")
    return payload


def boolish(v):
    return clean(v).lower() in {"yes", "true", "1", "viaduct"}


def classify(tags):
    highway = clean(tags.get("highway")).lower()
    layer_raw = clean(tags.get("layer"))
    try:
        layer = int(float(layer_raw)) if layer_raw else 0
    except ValueError:
        layer = 0

    tunnel = boolish(tags.get("tunnel")) or boolish(tags.get("covered"))
    bridge = boolish(tags.get("bridge")) or layer > 0
    ramp = highway.endswith("_link")

    if highway.startswith("motorway"):
        road_class = "HIGHWAY"
    elif highway.startswith("trunk"):
        road_class = "EXPRESSWAY"
    else:
        road_class = "URBAN"

    if ramp:
        structure = "RAMP"
    elif tunnel or layer < 0:
        structure = "TUNNEL"
    elif bridge:
        structure = "ELEVATED"
    elif road_class in {"HIGHWAY", "EXPRESSWAY"}:
        structure = "MAINLINE"
    else:
        structure = "COMPLEX"

    return road_class, structure, layer


def iter_segments(region, payload):
    seen = set()
    for e in payload.get("elements", []):
        if e.get("type") != "way":
            continue
        tags = e.get("tags", {})
        geom = e.get("geometry") or []
        if len(geom) < 2:
            continue
        road_class, structure, layer = classify(tags)
        name = clean(tags.get("name") or tags.get("name:zh"))
        ref = clean(tags.get("ref"))
        oneway = clean(tags.get("oneway"))
        maxspeed = clean(tags.get("maxspeed"))
        way_id = int(e["id"])
        for idx, (a, b) in enumerate(zip(geom, geom[1:])):
            try:
                lat1, lon1 = float(a["lat"]), float(a["lon"])
                lat2, lon2 = float(b["lat"]), float(b["lon"])
            except (KeyError, TypeError, ValueError):
                continue
            if abs(lat1-lat2) < 1e-9 and abs(lon1-lon2) < 1e-9:
                continue
            # Overpass union clauses can return the same way more than once.
            key = (way_id, idx)
            if key in seen:
                continue
            seen.add(key)
            yield [
                f"{region}-{way_id}-{idx}",
                region,
                road_class,
                structure,
                name,
                ref,
                str(layer),
                f"{lat1:.7f}",
                f"{lon1:.7f}",
                f"{lat2:.7f}",
                f"{lon2:.7f}",
                oneway,
                maxspeed,
            ]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    rows = []
    summary = {}
    for region, city in CITIES.items():
        payload = fetch(city, args.force)
        city_rows = list(iter_segments(region, payload))
        rows.extend(city_rows)
        ways = {r[0].rsplit("-", 1)[0] for r in city_rows}
        by_structure = {}
        for r in city_rows:
            by_structure[r[3]] = by_structure.get(r[3], 0) + 1
        summary[region] = {
            "ways": len(ways),
            "segments": len(city_rows),
            "structure_segments": by_structure,
        }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="\n") as f:
        f.write("# id|region|road_class|structure|name|ref|layer|lat1|lon1|lat2|lon2|oneway|maxspeed\n")
        f.write("# OSM © OpenStreetMap contributors, ODbL 1.0; snapshot 2026-09-20.\n")
        for row in rows:
            f.write("|".join(clean(x) for x in row) + "\n")

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"wrote {len(rows)} segments -> {OUT}")


if __name__ == "__main__":
    main()
