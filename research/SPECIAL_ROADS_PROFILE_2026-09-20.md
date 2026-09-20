# Taipei / New Taipei / Keelung Special-Road Profile — 2026-09-20

## Goal

Prevent surface-road signal/countdown and intersection-enforcement false matches while driving on elevated roads, expressways, freeways, tunnels/vehicular underpasses, ramps, and overlapping complex roads.

## Official structure references

National Freeway Bureau references establish the major grade-separated network affecting the three-city area: National Freeways 1, 3, 3A and 5, including the Xizhi-Wugu elevated road and northern freeway tunnels.

Sources:
- https://www.freeway.gov.tw/Publish.aspx?cnid=1906&p=4617
- https://www.freeway.gov.tw/Publish.aspx?cnid=1906&p=4622
- https://www.freeway.gov.tw/Publish.aspx?cnid=1290

Taipei official material explicitly identifies major grade-separated roads including 建國南北高架道路、新生北路高架道路、市民大道高架道路、環東大道高架段、水源快速道路 and vehicular underpasses/tunnels. Taipei open data also publishes tunnel and underpass inventories with entrance coordinates.

Sources:
- https://dot.gov.taipei/cp.aspx?n=180956B21422E411
- https://data.taipei/dataset/detail?id=03e017d7-674d-4665-b874-5a6a020668e4
- https://data.taipei/dataset/detail?id=c493d546-420c-49f2-b992-af058252f293

New Taipei coverage focuses on 台64、台65、新北環河快速道路 and National Freeway 1/3/5 interfaces, plus bridge/tunnel/ramp transitions. Keelung focuses on National Freeway 1/3, 台62/台62甲, 東岸高架橋, port-side elevated connectors and tunnels.

Keelung source:
- https://www.klcg.gov.tw/tw/klcg1/3331-111408.html

## Implemented geometry model

Generated asset: app/src/main/assets/special_roads.psv
Builder: tools/build_special_roads_osm.py
Source geometry: OpenStreetMap contributors, ODbL 1.0, snapshot 2026-09-20.

Included OSM road features: motorway/motorway_link, trunk/trunk_link, major-road bridges, major-road tunnels/covered roads, and major roads with explicit layer metadata.

Normalized classes: HIGHWAY, EXPRESSWAY, URBAN.
Normalized structures: MAINLINE, ELEVATED, TUNNEL, RAMP, COMPLEX.

Current asset totals:
- Taipei: 6,052 line segments
- New Taipei: 12,846 line segments
- Keelung: 2,793 line segments
- Total: 21,691
- ELEVATED: 9,077
- RAMP: 8,857
- MAINLINE: 2,643
- TUNNEL: 1,114

## Runtime behavior

SpecialRoadStore uses spatial grid indexing, point-to-line distance, travel-bearing compatibility, one-way handling, road-class priority, corridor hysteresis, and short GNSS-loss retention.

On a special mainline/elevated/tunnel, surface signals are suppressed and fresh MCP signal snapshots are blocked/cleared. On a recognized ramp, suppression is released and only the nearest forward signal is acquired initially, implementing the required mainline-to-exit behavior.

Fixed-speed and section-speed enforcement remain enabled on special roads. Surface intersection-style technology-enforcement/red-light alerts are suppressed on the mainline and released again on a ramp.

The old speed-only detector remains only as fallback when no geometry context is active.

## Known limitation

XY GPS alone cannot perfectly distinguish roads stacked directly above one another. The implementation therefore favors ramp-transition continuity, repeated matching, heading consistency and conservative suppression. SignalLogger road-level observations can further improve these overlap cases.