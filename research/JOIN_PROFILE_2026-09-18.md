# Join Profile — 2026-09-18

## Summary

- Timing index: 2,809 rows / 2,809 unique `icid` / 2,809 unique device IDs.
- Detailed timing plans: 61,807 rows / 2,820 unique `icid`.
- Timing schedule table: 24,638 rows / 2,822 unique `icid`.
- Signal-location list: 2,710 rows.

## Exact ID joins

- Index → plan by `icid`: **2,808 / 2,809**.
- Index → table by `icid`: **2,809 / 2,809**.
- Index → plan by device ID: **2,808 / 2,809**.
- Index → table by device ID: **2,809 / 2,809**.
- Detailed plan IDs not present in timing-index CSV: 12.
- Schedule-table IDs not present in timing-index CSV: 13.
- Timing-index IDs without detailed plan: 1.
- Timing-index IDs without schedule table: 0.

## Coordinate reconciliation with the standalone signal-location dataset

- Timing-index rows with valid coordinates: 2,807.
- Signal-location rows with valid coordinates: 2,699.
- Exact 6-decimal coordinate matches: **2,620 / 2,809**.
- Exact coordinate + normalized-name matches: 2,499.
- 5-decimal coordinate matches (rough tolerance bucket): 2,620.

The standalone signal-location file has no official `icid`, so the timing index remains the preferred canonical bridge from `icid/deviceid` to coordinates. Name/coordinate reconciliation is secondary validation, not the primary key.

## Data quality notes

- Duplicate coordinate keys at 6 decimals in timing index: 2.
- Duplicate coordinate keys at 6 decimals in signal-location dataset: 2.
- Coordinate duplicates may represent paired/control-related signals or multiple controlled points and must not automatically be deduplicated.

## Conclusion

The official timing-index CSV provides a strong canonical bridge: almost all indexed intersections can be joined to the detailed timing JSON directly by official IDs. The remaining gap should be investigated before app development, but it does not block Phase 0 coverage analysis.
