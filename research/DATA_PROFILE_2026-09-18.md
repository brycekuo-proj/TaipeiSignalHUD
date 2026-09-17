# Data Profile — 2026-09-18

## Key result

The detailed Taipei timing resources are not merely static intersection coordinates. The current snapshots contain schedule and phase-plan structure with `InfoTime` dated 2026-09-17. This materially improves the feasibility of a Taipei-first signal countdown model.

## Counts

- Signal timing CSV index: 2,809 records
- Signal locations: 2,710 records
- Fixed/section speed-camera dataset: 143 records
- Technology-enforcement dataset: 95 records
- `timing_plan_table.json`: 24,638 records / 2,822 unique `icid`
- `timing_plan.json`: 61,807 records / 2,820 unique `icid` / 2,820 unique `deviceid`

## timing_plan_table.json observed schema

Top-level list of records with:
- `Agency_name`
- `AgencyCodes`
- `icid`
- `icname`
- `deviceid`
- `segmenttype`
- `subsegment`
- `InfoTime`

`subsegment` is a list containing at least:
- `subsegmentid`
- `time` (e.g. `0700`, `1630`)
- `planid（SeqNo）`

This is directly useful for selecting an active plan by time/day. Official traffic-control specifications define segment types 1–7 as regular day types and allow them to represent Monday through Sunday. The Taipei sample PDF/data alignment confirms the working mapping 1=Monday through 7=Sunday: sample `I0SSA` uses the same schedules for 1–5 and a different shared schedule for 6–7.

## timing_plan.json observed schema

Records include:
- `icid`
- `icname`
- `deviceid`
- `planid（SeqNo）`
- `phaseorder`
- `direction`
- `cycletime`
- `offset`
- `subplan`
- `InfoTime`

`subplan` entries observed with:
- `subphaseid`
- `mingreen`
- `maxgreen`
- `yellow`
- `allred`
- `pedgreenflash`
- `pedred`
- `green`

This is the critical structure needed for countdown modelling.

`direction` follows the traffic-control `Direct` convention: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW. The current Taipei snapshot only contains 0/2/4/6.

## Cycle profile

All 61,807 records have integer `cycletime` and `offset`. Five records currently have zero cycle and must be treated as special/invalid until understood.

Most common cycle lengths observed include 200, 150, 100, 120, 60, 90 and 75 seconds. The median across records is 120 seconds.

40,125 / 61,807 plan records have non-zero offsets.

## Official PDF cross-check

A sample TTCX timing-plan PDF (`S0SSA10`) shows columns for Monday through Sunday, activation time, timing plan, cycle, offset, direction, phase order and phase parameters. This supports the interpretation that the JSON pair is intended to reconstruct the three-in-one timing report, but enum semantics still need a formal mapping file.

## Important anomaly: VD freshness

The VD XML downloaded on 2026-09-18 reports `ExchangeTime=2024/11/14T16:46:02`. The endpoint therefore fails freshness validation and is not accepted as a live MVP dependency.

## Next research tasks

1. Decode `phaseorder` symbols (`00`, `B0`, `81`, `0A`, etc.) against the official phase-type coding.
2. Join timing JSON `icid`/`deviceid` to timing CSV coordinates and signal locations.
3. Calculate coverage for the Tier-A major-road list.
4. Identify fixed vs dynamic/actuated signals where possible.
5. Field-check offset interpretation at a sample of corridors before any App work.
