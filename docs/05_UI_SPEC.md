# 05 — UI Specification

## Product surface

極簡 Driving HUD，不做地圖主畫面。

## Required visible information

### Header
- current road name
- travel direction
- current GPS speed (largest value after signal countdowns)

### Three forward intersections
Exactly three slots, ordered along the current road direction:

1. next controlled intersection
2. second controlled intersection
3. third controlled intersection

Each slot can show:
- index 1 / 2 / 3
- intersection name
- distance (m)
- light state
- remaining seconds or range

Examples:

```text
忠孝東路四段 · 東向

52 km/h

1  敦化南路   210 m    🟢 18 秒
2  延吉街     480 m    🟢 41 秒
3  光復南路   760 m    🟢  9 秒

📷 測速 320 m · 限速 50
⚠ 科技執法 680 m
```

## Road-mode slot semantics

### Surface road

The three slots mean the next three applicable signalized intersections along the current directed road graph.

### Elevated / expressway mainline

At minimum, slot 1 must represent the **next reachable exit ramp's terminal/first controlling signal**, not any surface signal directly below the elevated road.

Slots 2 and 3 may represent later reachable exit-ramp signals only if Phase 0 proves that ordering and ramp-to-signal mapping are reliable. Otherwise they remain `--` rather than inventing surface intersections.

Example:

```text
市民高架 · 東向

67 km/h

1  下一出口：重慶北路   1.4 km   🟢 22 秒
2  --
3  --
```

Showing an exit signal does not imply that the system predicts or recommends taking that exit.

### Already on an exit ramp

- slot 1 = current ramp-terminal signal
- slot 2/3 = subsequent applicable signals on the connected surface road, when topology is reliable
- if the ramp forks and the active branch is unresolved, show `--` instead of choosing a nearby signal

## No-driving-advice rule

UI 禁止顯示：
- 可以過
- 趕得上
- 加速
- 減速
- 收油
- 煞車
- 建議停車
- 建議通過

Only facts / estimates.

## Prediction display states

High confidence:
- `🟢 12 秒`

Medium confidence:
- `🟢 約 10–13 秒`

Low confidence / unsynced:
- `🟢 --`

Unknown state:
- `⚪ --`

Flashing / non-normal operation:
- dedicated flashing indicator; no normal countdown

## Enforcement display

Speed camera:
- icon
- distance
- speed limit
- direction-filtered only

Technology enforcement:
- icon
- distance
- optional short enforcement type

Detailed enforcement items should not crowd the driving screen; they can be available in a non-driving detail view later.

## Visual hierarchy

1. Current speed
2. Next signal countdown
3. 2nd / 3rd signal countdown
4. Enforcement alerts
5. Road/direction / confidence metadata

## Always-visible disclaimer

Short form:

`號誌秒數為預估值，現場號誌優先。`

## Interaction

MVP driving view should require no taps while moving.

Phase 0 does not implement UI code; this file is the implementation contract only.