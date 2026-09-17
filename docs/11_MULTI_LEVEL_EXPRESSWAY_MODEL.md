# 11 — Multi-level / Expressway Road Model

## Why this is a first-class requirement

Taipei contains multiple elevated / expressway corridors that overlap or run very close to surface roads. A nearest-road or nearest-intersection algorithm can therefore map a vehicle on an elevated road to the surface street below and incorrectly display three surface traffic signals.

Phase 0 must treat vertical road structure as part of road identity, not as a later edge case.

## Priority corridors

At minimum validate:

- 建國南北高架道路
- 市民高架道路
- 基隆路高架道路
- 環東大道
- 環河南北快速道路 / 環河快速道路

Also include the same problem class in Taipei coverage research:

- 新生高架道路
- 水源快速道路
- other bridges, viaducts, tunnels, ramps and grade-separated connectors that overlap surface streets

## Core product rule

A road is not identified only by XY position.

Canonical state must include:

`road_link + carriageway_direction + structure_level + ramp_state`

Possible `structure_level` values:

- `SURFACE`
- `ELEVATED`
- `TUNNEL`
- `BRIDGE`
- `RAMP`
- `UNKNOWN`

## Signal applicability

Mainline elevated / expressway links normally must not inherit traffic signals from the surface street below.

When current matched state is an elevated / expressway mainline:

- do not display surface-road signal countdowns merely because coordinates overlap
- continue to display current speed
- continue to display applicable speed-camera / technology-enforcement alerts tied to the current carriageway
- signal countdown can remain empty / `--` until the vehicle is confidently matched to an exit ramp or a signalized downstream surface approach

## Exit-ramp rule without navigation intent

TaipeiSignalHUD is not a navigation app, so it does not know in advance whether the driver intends to exit.

Therefore:

1. stay on elevated-mainline state while the trajectory remains consistent with the mainline
2. do not pre-emptively switch to surface-road signals at an approaching interchange
3. switch only when map matching has strong evidence that the vehicle entered the exit ramp / connector
4. after the ramp is linked to a signalized surface approach, compute the next 1–3 applicable signals

This favors avoiding false information over showing an early but uncertain countdown.

## Map-matching evidence

Do not use GPS altitude alone. Phone altitude can be noisy and elevated structures may differ from surface roads by only a small vertical distance.

Use a weighted state model combining:

- previous matched road link
- graph connectivity / legal continuation
- heading and heading change
- speed profile
- distance to candidate links
- ramp entrance / exit geometry
- road class
- OSM `layer`, `bridge`, `tunnel`, `oneway`, `junction`, `link` attributes where available
- official MOTC / Taipei road-link references where available
- optional GNSS altitude / barometer only as secondary evidence

## Hysteresis / anti-flapping

The matcher must not switch repeatedly between elevated and surface links because their XY geometries overlap.

Use continuity hysteresis:

- retain the current level unless an alternative candidate has substantially stronger evidence
- require graph-consistent transition through a ramp/connector where the road graph provides one
- treat impossible direct jumps between stacked roads as invalid

## Data fields

Add to normalized road-link model:

- `road_link_id`
- `corridor_id`
- `carriageway_direction`
- `road_class`
- `structure_level`
- `layer`
- `bridge`
- `tunnel`
- `is_ramp`
- `ramp_role` — entrance / exit / connector / unknown
- `parent_structure_id`
- `signal_applicability` — mainline_none / downstream / normal / unknown
- `source_refs[]`

## Testing requirements

Offline and field tests must include:

- same XY corridor: elevated vehicle vs surface vehicle
- entering an elevated road
- remaining on elevated mainline through a surface intersection below
- exiting to surface road
- entering from one elevated corridor to another connector
- parallel ramp/mainline ambiguity
- GPS drift while stopped/slow on elevated structure
- surface road directly below/adjacent to elevated road
- enforcement device direction and level filtering

## Acceptance behavior

The worst failure is not a missing countdown; it is confidently showing the surface signal while the user is on a grade-separated road.

Therefore uncertain vertical-level matching must downgrade signal output to `--` rather than borrow signals from a nearby surface road.
