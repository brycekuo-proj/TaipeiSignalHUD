# Major Road Name-Coverage Heuristic — 2026-09-18

## Scope

This is a Phase-0 heuristic based on whether the official timing-index intersection name contains a seeded major-road name. It is **not** a road-topology result and can both undercount and overcount. OSM graph matching must replace this method before implementation.

| Corridor | Name-matched intersections | With detailed plan | Plan coverage |
|---|---:|---:|---:|
| 忠孝東路/忠孝西路 | 4 | 4 | 100.0% |
| 仁愛路 | 29 | 29 | 100.0% |
| 信義路 | 36 | 36 | 100.0% |
| 和平東路/和平西路 | 2 | 2 | 100.0% |
| 南京東路/南京西路 | 14 | 14 | 100.0% |
| 民權東路/民權西路 | 11 | 11 | 100.0% |
| 民生東路/民生西路 | 13 | 13 | 100.0% |
| 市民大道 | 68 | 68 | 100.0% |
| 八德路 | 61 | 61 | 100.0% |
| 長安東路/長安西路 | 12 | 12 | 100.0% |
| 中山南路/中山北路 | 7 | 7 | 100.0% |
| 新生南路/新生北路 | 1 | 1 | 100.0% |
| 建國南路/建國北路 | 0 | 0 | 0% |
| 復興南路/復興北路 | 19 | 19 | 100.0% |
| 敦化南路/敦化北路 | 15 | 15 | 100.0% |
| 光復南路/光復北路 | 32 | 32 | 100.0% |
| 基隆路 | 38 | 38 | 100.0% |
| 羅斯福路 | 2 | 2 | 100.0% |
| 承德路 | 54 | 54 | 100.0% |
| 重慶南路/重慶北路 | 1 | 1 | 100.0% |
| 環河北路 | 1 | 1 | 100.0% |

Unique timing-index intersections matching at least one seeded Tier-A corridor name: **384**.
Of these, **384** have a detailed timing plan.

## Interpretation

- A low count does not mean a road lacks signals; official intersection names may be written using the cross street or local naming conventions.
- A high count may include side-street signals whose name happens to contain the corridor name.
- The important early signal is that nearly every name-matched official `icid` also has detailed timing data.
- Final coverage must be computed after OSM road-graph reconciliation and approach-direction mapping.
