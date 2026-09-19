#!/usr/bin/env python3
"""Build grade-C traffic-signal coverage for New Taipei and Keelung from OSM.

Grade C is geometry/coverage only. It must never be interpreted as a timing plan.
OSM data © OpenStreetMap contributors, ODbL 1.0.
"""
from __future__ import annotations
import argparse, json, math, time
from collections import Counter, defaultdict
from pathlib import Path
import requests

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw" / "osm"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "intersections_c.psv"
CITIES = {
    "NTPC": {"name": "新北市", "area_id": 3601527220,
             "raw": RAW_DIR / "new_taipei_traffic_signals_2026-09-20.json"},
    "KLC": {"name": "基隆市", "area_id": 3601296154,
            "raw": RAW_DIR / "keelung_traffic_signals_2026-09-20.json"},
}
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
USER_AGENT = "TaipeiSignalHUD-C-Coverage/0.1"
CLUSTER_RADIUS_M = 45.0

def fetch_city(city, force):
    path = city["raw"]
    if path.exists() and not force:
        return json.loads(path.read_text(encoding="utf-8"))
    query = f"""[out:json][timeout:180];
node["highway"="traffic_signals"](area:{city['area_id']})->.signals;
way(bn.signals)["highway"]->.roads;
(.signals;.roads;);
out body;"""
    last_error = None
    for endpoint in ENDPOINTS:
        try:
            response = requests.get(endpoint, params={"data": query},
                                    headers={"User-Agent": USER_AGENT}, timeout=240)
            response.raise_for_status()
            payload = response.json()
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                            encoding="utf-8")
            return payload
        except Exception as exc:
            last_error = exc
            time.sleep(1)
    raise RuntimeError(f"Unable to download OSM traffic signals: {last_error}")

def haversine_m(a, b):
    r = 6371000.0
    p1, p2 = math.radians(a["lat"]), math.radians(b["lat"])
    dp = math.radians(b["lat"] - a["lat"])
    dl = math.radians(b["lon"] - a["lon"])
    h = math.sin(dp / 2.0) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2.0) ** 2
    return r * 2.0 * math.atan2(math.sqrt(h), math.sqrt(max(0.0, 1.0 - h)))

class UnionFind:
    def __init__(self, ids):
        self.parent = {x: x for x in ids}
        self.rank = {x: 0 for x in ids}
    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])
        return self.parent[x]
    def union(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra == rb:
            return
        if self.rank[ra] < self.rank[rb]:
            ra, rb = rb, ra
        self.parent[rb] = ra
        if self.rank[ra] == self.rank[rb]:
            self.rank[ra] += 1

def clean_name(tags):
    name = (tags.get("name") or tags.get("name:zh") or "").strip()
    return " ".join(name.replace("\u3000", " ").split())

def build_city(region, city, payload):
    nodes, ways = {}, []
    for e in payload.get("elements", []):
        if e.get("type") == "node" and e.get("tags", {}).get("highway") == "traffic_signals":
            nodes[int(e["id"])] = {"id": int(e["id"]), "lat": float(e["lat"]),
                                   "lon": float(e["lon"]), "tags": e.get("tags", {})}
        elif e.get("type") == "way":
            ways.append(e)

    names_by_node = defaultdict(Counter)
    signal_ids = set(nodes)
    for way in ways:
        name = clean_name(way.get("tags", {}))
        if not name:
            continue
        for node_id in way.get("nodes", []):
            if node_id in signal_ids:
                names_by_node[node_id][name] += 1

    cell_deg = 0.0005
    buckets = defaultdict(list)
    uf = UnionFind(nodes.keys())
    for node_id, node in nodes.items():
        gx, gy = int(math.floor(node["lon"] / cell_deg)), int(math.floor(node["lat"] / cell_deg))
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for other_id in buckets.get((gx + dx, gy + dy), []):
                    if haversine_m(node, nodes[other_id]) <= CLUSTER_RADIUS_M:
                        uf.union(node_id, other_id)
        buckets[(gx, gy)].append(node_id)

    groups = defaultdict(list)
    for node_id in nodes:
        groups[uf.find(node_id)].append(node_id)

    rows = []
    for members in groups.values():
        lat = sum(nodes[n]["lat"] for n in members) / len(members)
        lon = sum(nodes[n]["lon"] for n in members) / len(members)
        all_names = Counter()
        for n in members:
            all_names.update(names_by_node.get(n, {}))
        unique = []
        for name, _ in all_names.most_common():
            if name not in unique:
                unique.append(name)
        if len(unique) >= 2:
            display = f"{unique[0]}　{unique[1]}"
        elif len(unique) == 1:
            display = f"{unique[0]}　前方號誌"
        else:
            display = f"{city['name']}　前方號誌"
        rows.append({
            "id": f"C-{region}-OSM-{min(members)}", "name": display,
            "lon": lon, "lat": lat, "region": region, "grade": "C", "source": "OSM",
        })
    rows.sort(key=lambda x: (x["lat"], x["lon"], x["id"]))
    return rows

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    all_rows, summary = [], {}
    for region, city in CITIES.items():
        payload = fetch_city(city, args.force)
        rows = build_city(region, city, payload)
        all_rows.extend(rows)
        raw_nodes = sum(1 for e in payload.get("elements", [])
                        if e.get("type") == "node"
                        and e.get("tags", {}).get("highway") == "traffic_signals")
        summary[region] = {"raw_signal_nodes": raw_nodes, "c_anchors": len(rows)}

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# id|name|longitude|latitude|region|grade|source",
        "# Grade C = coverage only. Never use these rows for signal-state/countdown estimation.",
        "# Data: © OpenStreetMap contributors, ODbL 1.0; snapshot 2026-09-20.",
    ]
    for row in all_rows:
        lines.append("|".join([
            row["id"], row["name"], f"{row['lon']:.7f}", f"{row['lat']:.7f}",
            row["region"], row["grade"], row["source"],
        ]))
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"wrote {len(all_rows)} C-grade anchors -> {OUTPUT}")

if __name__ == "__main__":
    main()
