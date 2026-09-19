# C-grade signal coverage

C grade is the safe fallback coverage layer for cities where authoritative signal timing is not available to this project.

## Runtime invariant

A C-grade anchor may provide signal/intersection geometry, display road names when present in the source, region/source provenance, and forward-corridor candidate ordering.

A C-grade anchor must never provide RED/YELLOW/GREEN state, remaining seconds, or an inferred timing plan.

The Android HUD therefore renders C-grade rows as `--`. The MCP receiver also blocks timing estimation for `coverage_grade=C`, providing an independent server-side guard.

## Current snapshot

Generated 2026-09-20 from OpenStreetMap traffic-signal nodes via Overpass:

- New Taipei (`NTPC`): 4,632 raw signal nodes -> 2,545 C-grade anchors
- Keelung (`KLC`): 457 raw signal nodes -> 265 C-grade anchors
- Total: 2,810 anchors

Nearby signal nodes are clustered within 45 m. Road names from parent OSM highway ways are used to produce an intersection-style display name when possible.

OSM data © OpenStreetMap contributors, ODbL 1.0.

## Files

- `app/src/main/assets/intersections_c.psv` - generated runtime asset
- `tools/build_c_coverage_osm.py` - downloader/generator
- `tools/verify_c_coverage.py` - safety/integrity checks
- `data/manifests/c_grade_osm_snapshot_2026-09-20.csv` - source snapshot metadata

Raw Overpass JSON is kept under `data/raw/osm/` and remains ignored by Git because raw datasets are reproducible.

## Refresh

Run `python3 tools/build_c_coverage_osm.py --force`, then `python3 tools/verify_c_coverage.py`.

Promotion from C to B/A must happen through a separate verified dataset. Do not add timing rows directly to C IDs.
