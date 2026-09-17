# 06 — Test and Validation Plan

## Why 10–20 intersections is insufficient

A small set can only prove the pipeline runs. It cannot prove road matching, timing-plan coverage, time-of-day switching, movement mapping, non-continuous behavior, or generalization across Taipei.

## Validation layers

### Layer 1 — Offline data coverage

Target: all Taipei timing-plan records that can be parsed.

Measure:
- total indexed intersections/devices
- coordinates valid / invalid
- duplicate names / duplicate coordinates
- intersections joinable to signal-location dataset
- intersections joinable to road graph
- availability of detailed timing JSON
- timing plans by weekday/time bucket

### Layer 2 — Corridor simulation

Target: at least 500–1,000 intersections / approaches in offline route traversal tests.

Measure:
- current road matching
- next 3 intersection ordering
- opposite-direction rejection
- side-street rejection
- close-pair intersection handling
- elevated-vs-surface road discrimination
- entrance/exit ramp transition accuracy
- false surface-signal attachment while on grade-separated mainline

### Layer 3 — Physical ground truth

Initial target:
- 100+ distinct intersections
- then 300–500 distinct intersections
- multiple passes, not one pass each

Coverage dimensions:
- morning peak
- midday
- evening peak
- late night
- weekday
- weekend
- major arterials
- elevated / expressway corridors
- entry and exit ramps
- secondary roads
- selected alleys
- fixed-plan signals
- suspected variable/dynamic signals

## Test unit

Not just `intersection`.

Use:

`intersection × approach_direction × movement × time_bucket × day_type`

One physical intersection can therefore yield many test cases.

## Ground truth fields

Record where safely obtainable:
- timestamp
- intersection ID/name
- direction
- movement
- observed current state
- observed state transition times
- observed cycle length
- estimated queue position
- special conditions

Ground truth collection must not require unsafe phone interaction while driving.

## Core metrics

### Road matching
- current road accuracy
- approach-direction accuracy
- structure-level accuracy (surface/elevated/tunnel/bridge/ramp)
- ramp transition accuracy
- next-3 ordering precision
- false surface-signal rate while on elevated/expressway mainline

### Timing
- state accuracy
- transition-time absolute error
- remaining-time absolute error
- 50th / 90th / 95th percentile error

### Reliability
- false-confidence rate: system shows exact value when it should have downgraded
- unsynced detection latency
- re-lock time after plan change / discontinuity

### Enforcement
- correct direction filtering
- false-positive camera alert rate
- missed camera / enforcement rate

## Major-road acceptance gates (draft)

Before App implementation is considered mature enough for public beta, planning target:

- next-3 road ordering precision >= 98% on selected Tier-A corridors
- exact/narrow countdown only when empirical error distribution supports it
- 95th-percentile large errors trigger automatic downgrade policy
- false-confidence rate kept lower than raw coverage pressure

No fixed ±1 second requirement is assumed.

## Model comparison

Compare at minimum:

A. Official timing plan only
B. Official plan + time/offset
C. Official plan + local phase correction
D. C + historical observations
E. D + verified traffic/VD anomaly features

The product only keeps complexity that measurably improves results.

## Alley strategy test

For Tier-C streets measure separately:
- coverage
- median interval width
- state accuracy
- whether history-based estimate beats a naive baseline

If it does not outperform baseline sufficiently, show `--` instead of an estimate.

## Deliverables before development

- data coverage report
- corridor priority report
- timing JSON schema map
- first offline intersection join table
- open-risk list

Phase 0 ends when these artifacts are sufficient to justify or reject App development.