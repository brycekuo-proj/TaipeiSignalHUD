# 09 — Normalized Data Model

本文件定義未來資料層契約；Phase 0 不實作 Android / server。

## `intersections`

一個可辨識的號誌控制路口。

Fields:
- `intersection_id` PK — 優先沿用官方 `icid`
- `device_id`
- `canonical_name`
- `district`
- `lat`
- `lon`
- `agency_code`
- `source_updated_at`
- `data_quality_flags[]`

## `signal_plan_schedule`

描述某一天型別、某時間開始使用哪一個 plan。

PK:
`device_id + segment_type + start_time`

Fields:
- `device_id`
- `intersection_id`
- `segment_type`
- `weekday` nullable
- `start_time_hhmm`
- `plan_id`
- `source_info_time`

## `signal_plans`

PK:
`device_id + plan_id + direction`

Fields:
- `device_id`
- `intersection_id`
- `plan_id`
- `direction_code`
- `direction_compass`
- `cycle_seconds`
- `offset_seconds`
- `phase_order_code`
- `source_info_time`
- `dynamic_risk_class`

## `signal_subphases`

PK:
`device_id + plan_id + direction + subphase_id`

Fields:
- `subphase_id`
- `green_seconds`
- `min_green_seconds`
- `max_green_seconds`
- `yellow_seconds`
- `all_red_seconds`
- `ped_green_flash_seconds`
- `ped_red_seconds`
- `movement_mapping` nullable until decoded

## `road_corridors`

Product-level road corridor entity.

Fields:
- `corridor_id` PK
- `canonical_name`
- `priority_tier` A/B/C/A-X
- `osm_relation_or_segment_refs[]`
- `direction`
- `official_plan_coverage`
- `sync_coverage`

## `road_links`

Directed road-segment identity used for map matching. This table is required to distinguish stacked/parallel roads.

Fields:
- `road_link_id` PK
- `corridor_id`
- `from_node_id`
- `to_node_id`
- `carriageway_direction`
- `road_class`
- `structure_level` — surface/elevated/tunnel/bridge/ramp/unknown
- `layer` nullable
- `bridge` boolean/nullable
- `tunnel` boolean/nullable
- `is_ramp`
- `ramp_role` — entrance/exit/connector/unknown
- `parent_structure_id` nullable
- `signal_applicability` — normal/mainline_none/downstream/unknown
- `source_refs[]`

A vehicle's current road state is `road_link_id + carriageway_direction + structure_level + ramp_state`, not just nearest XY geometry.

## `ramp_signal_targets`

Maps an elevated/expressway exit to the first reliable signal movement that controls traffic leaving that ramp.

Fields:
- `ramp_target_id` PK
- `mainline_road_link_id`
- `exit_ramp_road_link_id`
- `exit_name`
- `exit_sequence_direction`
- `ramp_terminal_approach_id`
- `intersection_id`
- `signal_direction_code`
- `movement`
- `mapping_confidence`
- `fork_ambiguity` boolean
- `source_refs[]`

The mapping chain is:

`mainline -> exit_ramp -> ramp_terminal_approach -> signal_movement`

If an exit forks before the applicable signal and the active branch cannot be determined, the target must remain unresolved rather than selecting a nearby signal by distance.

## `intersection_approaches`

Connect road graph to signal model.

Fields:
- `approach_id` PK
- `intersection_id`
- `corridor_id`
- `incoming_road_link_id`
- `structure_level`
- `incoming_bearing`
- `movement` — through/left/right/mixed/unknown
- `signal_direction_code`
- `mapping_confidence`

## `enforcement_sites`

Unified speed-camera / technology-enforcement model.

Fields:
- `enforcement_id` PK
- `type` — speed_camera / section_speed / tech_enforcement
- `name_or_location`
- `lat`
- `lon`
- `direction_text`
- `direction_bearing` nullable
- `speed_limit` nullable
- `enforcement_items[]`
- `activation_date` nullable
- `source_dataset`
- `source_updated_at`

## `signal_anchors`

Future local observations used only for synchronization research.

Fields:
- `anchor_id`
- `intersection_id`
- `approach_id`
- `timestamp`
- `anchor_type`
- `estimated_transition`
- `queue_delay_estimate`
- `observation_confidence`
- `privacy_scope`

## `signal_sync_state`

Runtime/future state contract, not persistent government truth.

Fields:
- `intersection_id`
- `approach_id`
- `active_plan_id`
- `phase_correction_seconds`
- `last_anchor_at`
- `continuity_state`
- `confidence`
- `updated_at`

## `signal_estimate`

Future HUD output contract.

Fields:
- `intersection_id`
- `intersection_name`
- `slot_role` — surface_next / next_exit_signal / current_ramp_terminal / downstream_surface
- `exit_name` nullable
- `source_road_link_id`
- `approach_direction`
- `movement`
- `distance_m`
- `light_state`
- `remaining_min_s`
- `remaining_max_s`
- `confidence`
- `continuity_state`
- `source_class`
- `generated_at`

## Join hierarchy

Official data join preference:

1. exact `icid`
2. exact `deviceid`
3. coordinate/name reconciliation only for records missing exact IDs

Do not use fuzzy names as the primary canonical identity when official IDs exist.

## Raw data preservation

All normalization must preserve original raw snapshots separately. Derived schemas must be reproducible from source + transformation version.
