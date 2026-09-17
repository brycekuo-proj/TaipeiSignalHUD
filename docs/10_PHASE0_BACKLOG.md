# 10 — Phase 0 Backlog

Phase 0 只做研究、資料整理與可行性驗證。**不開發 Android App / APK / overlay。**

## P0.1 — Decode timing fields

- [x] 盤點 detailed timing JSON schema
- [x] 確認 `cycletime`, `offset`, `subplan`
- [x] 建立 `direction` 工作映射
- [x] 建立 `segmenttype` Monday–Sunday 工作映射
- [ ] 完成 `phaseorder` code 解碼
- [ ] 標記 fixed / actuated / dynamic 可能性

Deliverable:
- `docs/08_TIMING_FIELD_DICTIONARY.md`

## P0.2 — Official-data join

- [ ] timing index ↔ timing_plan by `icid/deviceid`
- [ ] timing index ↔ timing_plan_table
- [ ] signal locations ↔ timing index
- [ ] 統計 unmatched / duplicate / invalid coordinate

Deliverables:
- `data/derived/join_profile.json`
- `research/JOIN_PROFILE_2026-09-18.md`

## P0.3 — Major-road coverage

- [ ] 用官方路口名稱先做 name-based heuristic
- [ ] 建立 Tier-A corridor coverage
- [ ] 後續用 OSM road graph 修正名稱法的誤差

Deliverables:
- `data/derived/major_road_name_coverage.csv`
- `research/MAJOR_ROAD_COVERAGE_2026-09-18.md`

## P0.4 — Road topology plan

- [ ] 決定 OSM extract 範圍與格式
- [ ] 定義 map-matching 需要的道路欄位
- [ ] 定義 next-3 traversal 規則
- [ ] 定義 elevated/ground/frontage/parallel-road ambiguity cases
- [ ] 文件化 ODbL attribution/compliance

## P0.5 — Offset / phase synchronization research

- [ ] 確認 offset 的時間基準
- [ ] 選擇主要幹道 sample corridors
- [ ] 建立不需危險操作的 Ground Truth 流程
- [ ] 比較 published offset vs field transitions
- [ ] 設計 phase-correction / anchor model

這是正式 App 是否值得做的最大技術 gate。

## P0.6 — Dynamic/non-continuous signal classification

- [ ] 找可公開辨識的動態/感應號誌清單
- [ ] 以 min/max green / timing pattern 建立 risk heuristic
- [ ] 設計 plan switch / anomaly / flashing downgrade 規則

## P0.7 — Enforcement normalization

- [ ] 固定測速標準化
- [ ] 區間測速辨識
- [ ] 科技執法標準化
- [ ] direction normalization
- [ ] activation-date filtering

## P0.8 — Traffic auxiliary-source review

- [x] 發現目前 VD XML snapshot 過舊，不可直接視為 live
- [ ] 尋找新的官方即時交通/VD來源
- [ ] 評估是否真的需要 Google Traffic

## P0.9 — Go / No-Go report

App 開發前必須回答：

1. Tier-A 主要道路有多少路口具 usable timing plan？
2. next-3 road matching 是否有可行資料基礎？
3. published offset 能否在主要道路保持可接受同步？
4. 動態/失步號誌是否能可靠降級成區間或 `--`？
5. 測速/科技執法資料是否能方向過濾？
6. 估計小巷是否真的優於不顯示？

只有在這些問題有正面證據後，才進 Phase 1 Android MVP。
