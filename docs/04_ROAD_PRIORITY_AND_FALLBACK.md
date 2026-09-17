# 04 — Road Priority and Fallback Strategy

## Principle

MVP 不追求所有道路同精度。

目標是：**主要道路先做到高可信、連續、有把握才顯示；小巷先提供保守估計。**

## Tier A — Major arterials

Initial planning list (not yet a complete official classification):

### East–west / major corridors
- 忠孝東路 / 忠孝西路
- 仁愛路
- 信義路
- 和平東路 / 和平西路
- 南京東路 / 南京西路
- 民權東路 / 民權西路
- 民生東路 / 民生西路
- 市民大道
- 八德路
- 長安東路 / 長安西路

### North–south / major corridors
- 中山南路 / 中山北路
- 新生南路 / 新生北路
- 建國南路 / 建國北路
- 復興南路 / 復興北路
- 敦化南路 / 敦化北路
- 光復南路 / 光復北路
- 基隆路
- 羅斯福路
- 承德路
- 重慶南路 / 重慶北路
- 環河北路

This list is a product-priority seed only. Before implementation it must be reconciled with official road class / OSM highway class and actual timing-plan coverage.

## Tier A-X — Elevated / expressway corridors

Multi-level roads are a first-class MVP mapping requirement because surface roads and elevated roads can overlap in XY coordinates.

Priority validation corridors:
- 建國南北高架道路
- 市民高架道路
- 基隆路高架道路
- 環東大道
- 環河南北快速道路 / 環河快速道路
- 新生高架道路
- 水源快速道路

On an elevated/expressway mainline, do **not** inherit surface-road signal countdowns. Current speed and applicable enforcement alerts may continue, while signal countdown remains empty / `--` until an exit ramp or downstream signalized surface approach is confidently matched.

See `docs/11_MULTI_LEVEL_EXPRESSWAY_MODEL.md`.

## Tier A requirements

For a corridor to be marked production-ready:

- road graph continuity is known
- direction can be mapped reliably
- next three controlled intersections can be ordered correctly
- official timing plan is available for a useful percentage of intersections
- signal plan can be synchronized or confidently downgraded
- enforcement devices can be direction-filtered

## Tier B — Secondary roads

Use the same official pipeline where possible, but tolerate:

- incomplete movement mapping
- wider prediction interval
- fewer than three countdown-capable intersections

## Tier C — Alleys / small streets

Fallback evidence:

1. official signal location, if any
2. historical observations by time bucket
3. stop/start events near stop line
4. adjacent coordinated-corridor relationship where justified
5. repeated local phase estimates

Do not extrapolate a major-road coordinated cycle into an alley unless data proves a stable relationship.

## Fallback output hierarchy

- exact/high-confidence: `12 秒`
- narrow estimate: `約 10–13 秒`
- wide estimate: `約 7–15 秒`
- insufficient: `--`

## Corridor continuity

A corridor may contain:

- intersections with no signal
- pedestrian-only signal
- left-turn-only phases
- close paired intersections
- offset coordinated groups
- dynamic signal control
- surface/elevated roads occupying nearly the same XY position
- entrance/exit ramps and grade-separated connectors
- bridges/tunnels whose nearest surface intersection is not applicable

Therefore road traversal must return a typed sequence, not simply every coordinate point.

Future data structure:

```text
RoadCorridor
- corridor_id
- canonical_name
- direction
- road_segments[]
- controlled_intersections[]
- priority_tier
- official_plan_coverage
- sync_coverage
```

## Major-road MVP target

Before public beta, measure:

- intersection identification precision
- ordering precision for next 3 intersections
- timing-plan coverage
- synchronized countdown coverage
- error distribution by road / time period

The goal is not 100% coverage. The goal is high trust on the corridors users use most.