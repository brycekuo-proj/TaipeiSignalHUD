# 07 — Risks and Open Questions

## Highest-risk technical unknowns

### 1. Phase synchronization

Official timing plans can describe cycle/phase structure, but the product still must know where the controller is in the cycle **right now**.

Open questions:
- how reliable is published offset for field synchronization?
- what clock reference is implied?
- how often do field controllers drift or switch plans differently from the published plan?
- can coordinated corridor anchors improve adjacent intersections?

This is the primary R&D question.

### 2. Detailed timing JSON schema

Large official JSON resources must be profiled and mapped.

Need to determine:
- keys for plan schedule
- cycle
- offset
- phase durations
- approach/movement representation
- holiday/weekend conditions
- special plans
- missing/null patterns

### 3. Dynamic and actuated signals

Not all signals are fixed periodic clocks.

Need to identify or infer:
- actuated intersections
- adaptive-control corridors
- pedestrian-triggered phases
- priority/preemption behavior

Policy: detected uncertainty must reduce confidence or suppress countdown.

### 4. Road graph / intersection matching

Dense Taipei roads create ambiguity:
- parallel roads
- frontage roads
- close paired intersections
- ramps
- alleys near arterials
- elevated / ground-level roads

Next-three logic is only as good as map matching.

### 5. Movement identity

Same intersection may have different:
- through phase
- left-turn arrow
- right-turn restrictions

MVP can prioritize straight-through movement, but must not silently show the wrong movement's countdown.

## Data risks

### Government data update cadence

Many Taipei datasets are updated irregularly. Data fetch date and source update date must be stored separately.

### VD freshness

The VD XML snapshot fetched on 2026-09-18 returned `ExchangeTime=2024/11/14T16:46:02`.

Therefore:
- current endpoint is not trusted as live
- do not make it an MVP dependency
- research replacement/current source before use

### Enforcement changes

Cameras and technology-enforcement sites change. The app data layer will eventually need periodic refresh and activation-date handling.

## Product/legal risks

### Over-trust

A single exact number can be interpreted as authoritative.

Mitigations:
- explicit estimate labeling
- confidence-driven ranges
- `--` when unreliable
- persistent statement that field signals take precedence

### Advice liability

Product must not output driving decisions such as accelerate, brake, or safe-to-pass.

### Third-party data terms

Google traffic is optional only. If used later, review Maps Platform storage/derived-data restrictions before architecture is finalized.

OSM use requires proper ODbL attribution/compliance.

## Privacy

Preferred design direction:
- current GPS processing primarily on device
- raw personal travel history should not be uploaded by default
- if collective learning is added later, design explicit consent, minimization and aggregation before collection

## Open research checklist

- [ ] Parse complete timing JSON schema
- [ ] Count intersections with usable detailed timing data
- [ ] Join timing index to signal-location dataset
- [ ] Determine major-road coverage by corridor
- [ ] Verify offset semantics against field observations
- [ ] Identify adaptive/dynamic signal corridors where possible
- [ ] Find a truly fresh VD / traffic source
- [ ] Define OSM extraction and attribution approach
- [ ] Establish safe Ground Truth collection procedure
- [ ] Decide confidence thresholds from real error distributions

## Development gate

Do not begin App implementation until the following are known:

1. detailed timing schema is understood
2. a meaningful percentage of Tier-A roads can be joined to road graph and timing plan
3. there is a plausible synchronization strategy
4. unsupported/dynamic intersections can be detected or safely downgraded
