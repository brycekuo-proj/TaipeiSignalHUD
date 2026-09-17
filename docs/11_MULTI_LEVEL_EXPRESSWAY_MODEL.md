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

Mainline elevated / expressway links must not inherit arbitrary traffic signals from the surface street below. However, the HUD should still expose the signal that is operationally relevant to the next reachable exit ramp.

When current matched state is an elevated / expressway mainline:

- never attach surface-road signals merely because their XY coordinates overlap the mainline
- continue to display current speed
- continue to display applicable speed-camera / technology-enforcement alerts tied to the current carriageway
- find the **next downstream reachable exit ramp** on the current carriageway
- follow that ramp topology to its first signalized surface/terminal approach
- display that signal as `NEXT_EXIT_SIGNAL` when the ramp-to-signal mapping is reliable
- if the exit ramp has no signal, continue to the first downstream signal that directly controls traffic leaving that ramp
- if the ramp or signal mapping is uncertain, display `--` rather than borrow a nearby surface-road signal

## Exit-ramp signal rule without navigation intent

TaipeiSignalHUD is not a navigation app and does not need to assume that the driver intends to exit. Showing the next exit's signal is informational only.

Therefore:

1. keep the vehicle road state on the elevated/expressway mainline while the trajectory remains consistent with the mainline
2. identify the next reachable exit ramp downstream
3. resolve `mainline -> exit_ramp -> ramp_terminal_approach -> signal_movement`
4. show that terminal signal countdown explicitly as **下一出口號誌 / next-exit signal**
5. only after Phase 0 proves stable ordering/mapping may the HUD precompute later second/third exit signals; otherwise leave those slots `--`
6. do not switch the vehicle itself to the surface-road model until map matching confirms entry into the exit ramp
7. once the vehicle actually enters the ramp, promote that ramp signal to the normal primary signal slot and compute subsequent surface signals

This preserves the correct road-level state while still giving useful signal information before the driver reaches the exit.

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
- next-exit ramp-terminal signal mapping while remaining on mainline
- later-exit ordering only as an optional validated extension
- parallel ramp/mainline ambiguity
- GPS drift while stopped/slow on elevated structure
- surface road directly below/adjacent to elevated road
- enforcement device direction and level filtering

## Acceptance behavior

The worst failure is not a missing countdown; it is confidently showing the surface signal while the user is on a grade-separated road.

Therefore uncertain vertical-level matching must downgrade signal output to `--` rather than borrow signals from a nearby surface road.
