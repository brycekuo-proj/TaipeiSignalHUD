# Data Directory

## Policy

`data/raw/`
- 本機原始快照
- 不進 Git
- 可重新由官方來源下載
- 不手動修改

`data/derived/`
- 小型可重建 summary / profile / mapping table
- 可進 Git

`data/manifests/`
- 資料來源、URL、格式、授權、更新狀態、使用角色
- 進 Git

## Raw files currently fetched locally

- `taipei_signal_timing.csv`
- `taipei_signal_locations.csv`
- `taipei_speed_cameras.csv`
- `taipei_tech_enforcement.csv`
- `taipei_vd_live.xml`
- `timing_plan_table.json`
- `timing_plan.json`

## Encoding notes

- timing CSV: UTF-8-SIG
- signal-location file: CP950 and tab-separated content despite `.csv`
- speed camera CSV: CP950
- technology enforcement CSV: CP950
- timing JSON: UTF-8

## Normalization rules (planned)

Normalize to UTF-8 and explicit schemas, preserving raw original files.

Never silently coerce invalid coordinates or unknown enum values. Invalid or unknown fields must be recorded in a data-quality report.