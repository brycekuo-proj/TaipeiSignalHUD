# 12 — Current Project State — 2026-09-18

本文件是 TaipeiSignalHUD 在 2026-09-18 對話、資料盤點與 Phase 0 規劃的同步快照。若個別舊文件與本文件衝突，應優先依最新決策更新個別規格，而不是保留舊假設。

## 1. Projectization status

目前已具備進入正式專案化 Phase 0 的價值，但尚未進入 Android App 開發。

成立原因：
- 台北官方資料不只提供號誌座標，已確認存在大量 timing schedule / cycle / offset / phase/subphase 結構。
- 官方 ID join 幾乎完整，可用 `icid/deviceid` 直接串接索引、schedule 與 detailed plan。
- 主要工程問題已可拆解：道路方向、approach/movement、多層道路、高架/平面、匝道、phase sync、非連續號誌、執法設備方向過濾。
- UI 與產品邊界明確：不是導航，不提供駕駛建議，只顯示資訊。
- 核心價值來自資料整理、路網建模、同步算法、異常降級與實地驗證，不是單靠 prompt/UI 可複製。

進入 Phase 1 Android MVP 前仍需通過 Phase 0 Go/No-Go gate。

## 2. Product definition

TaipeiSignalHUD = 台北道路號誌預測資訊層 / Driving Information HUD。

主要顯示：
1. 當前道路與行進方向
2. 當前 GPS 時速
3. 前方三個「適用號誌資訊槽位」
4. 測速照相 / 區間測速提示
5. 科技執法提示

禁止輸出：可以過、趕得上、加速、減速、煞車、建議速度、安全通過等駕駛決策。

固定聲明：`號誌秒數為預估值，現場號誌優先。`

## 3. MVP geographic strategy

第一版鎖定台北市。主要道路優先高可信模式：官方 timing plan + road graph / directed link + approach direction + movement mapping + phase synchronization + continuity/anomaly detection。

小巷允許估計，但必須降級顯示：high confidence `12 秒`；estimate `約 10–13 秒`；wide estimate `約 7–15 秒`；insufficient `--`。若小巷估計實測無法優於 baseline，應直接顯示 `--`。

## 4. Direction / approach model

不能使用 `intersection -> one countdown`。

正確 identity 至少是：
`intersection_id + incoming_road_link + approach_direction + movement + timing_plan`

同一路口的北向、南向、東向、西向可能不同；直行與左轉也可能不同。MVP 預設可先以「沿目前道路繼續直行」作為 movement，除非已能可靠辨識其他 movement。

GPS direction 不可單獨決定號誌方向。應使用 recent GNSS heading + directed road-link map matching + previous matched link continuity + intersection approach mapping + official timing `direction/Direct`。

## 5. Forward-three semantics

### Surface road
三個槽位 = 沿當前有向道路拓樸往前的三個適用號誌路口，不是 GPS 半徑內最近三個號誌。

### Elevated / expressway mainline
至少 slot 1 = 下一個可到達出口匝道所對應的第一顆可靠出口端號誌，而不是高架正下方的平面道路號誌。

搜尋鏈：`mainline -> exit_ramp -> ramp_terminal_approach -> signal_movement`

slot 2/3 是否顯示後續出口匝道號誌，留待 Phase 0 驗證；若後續出口排序或 ramp-to-signal mapping 不夠可靠，應維持 `--`。顯示出口號誌不代表系統判定使用者會下該出口。

### Vehicle already on exit ramp
slot 1 = 當前匝道末端號誌；slot 2/3 = 接續平面道路上可可靠排序的後續號誌。

若匝道分叉且無法判斷使用者會進入哪一支線，該出口號誌顯示 `--`，不可任選附近號誌。

## 6. Multi-level road requirement

第一版即需考慮：建國南北高架道路、市民高架道路、基隆路高架道路、環東大道、環河南北快速道路 / 環河快速道路、新生高架道路、水源快速道路，以及 bridges / tunnels / ramps / grade-separated connectors。

Road identity：`road_link + carriageway_direction + structure_level + ramp_state`

Structure level：SURFACE / ELEVATED / TUNNEL / BRIDGE / RAMP / UNKNOWN。

不能只靠 GPS altitude；需以 road graph continuity、heading、speed、ramp geometry、road class、OSM layer/bridge/tunnel/oneway/link、官方 road-link references 等共同判定。

## 7. Signal continuity model

號誌不可假設固定連續。至少需要 FIXED_SYNCED / VARIABLE_TRACKED / UNSYNCED / FLASHING / ANOMALY / NO_SIGNAL。

高可信顯示精確/單值倒數；中可信顯示區間；失步/異常顯示 `--`。核心原則：錯誤的高可信秒數比缺值更差。

## 8. Phase synchronization

固定時制基礎：`phase = (current_time - offset) mod cycle`。

正式系統仍需驗證 offset reference semantics、current active plan、controller clock drift、dynamic/actuated behavior、temporary plan changes。

Anchor model 規劃使用重複 GPS 停走、queue release、crossing observations 等證據修正 phase offset，但需避免把 queue delay 當成真正 green onset。

## 9. Official data collected locally

Raw files留在本機 `data/raw/`，不進 Git。已取得 Taipei signal timing index CSV、signal locations CSV、fixed/section speed-camera CSV、technology-enforcement CSV、VD XML snapshot、`timing_plan_table.json`、`timing_plan.json`。

## 10. Data profile snapshot

- timing index: 2,809 rows / 2,809 unique `icid`
- signal locations: 2,710 rows
- fixed/section speed-camera: 143 records
- technology-enforcement: 95 records
- `timing_plan_table.json`: 24,638 records / 2,822 unique `icid`
- `timing_plan.json`: 61,807 records / 2,820 unique `icid`
- non-zero offset: 40,125 / 61,807 records
- only 5 records observed with zero cycle; investigate/special-case
- timing JSON `InfoTime` snapshot: 2026-09-17

Observed detailed-plan fields: `planid（SeqNo）`, `phaseorder`, `direction`, `cycletime`, `offset`, `subplan`。
Observed subphase fields: `subphaseid`, `mingreen`, `maxgreen`, `green`, `yellow`, `allred`, `pedgreenflash`, `pedred`。

## 11. Join profile

- timing index `icid` -> detailed plan: 2,808 / 2,809
- timing index `icid` -> schedule table: 2,809 / 2,809
- timing index device -> detailed plan: 2,808 / 2,809
- timing index device -> schedule table: 2,809 / 2,809

Standalone signal-location list lacks canonical `icid`, so canonical join should use timing index as bridge；name/coordinate matching is secondary validation only。

## 12. Major-road preliminary coverage

Name-based heuristic found 384 unique timing-index intersections matching at least one seeded major-road name；all 384 also had detailed timing plans。

This is NOT a final coverage percentage。Final major-road coverage must use directed road graph / OSM / official road-link reconciliation。

## 13. Enforcement data

Fixed speed-camera dataset includes location, coordinates, shooting direction and speed limit。Technology-enforcement dataset includes location, coordinates, enforcement type/items and activation information。

Future filter must use current directed road link + carriageway direction + structure level, not merely distance radius。

## 14. VD status

Downloaded VD XML endpoint returned `ExchangeTime=2024/11/14T16:46:02` during the 2026-09-18 snapshot，因此目前不可作為 live MVP dependency，需找到並驗證新的官方即時來源。

## 15. Core Phase 0 gates

1. Decode phaseorder / movement semantics reliably enough for applicable directions.
2. Build topology-aware road-link matching for major roads.
3. Validate surface vs elevated vs ramp discrimination.
4. Validate next-three surface intersection ordering.
5. Validate next-exit ramp-terminal signal mapping on expressways；再決定是否擴充到後續第 2/3 個出口。
6. Validate published cycle/offset against field timing.
7. Define re-lock / downgrade behavior for dynamic and discontinuous signals.
8. Normalize speed-camera and technology-enforcement direction/level filtering.
9. Decide whether alley estimation is actually better than `--`.

## 16. Current decision

Status: **PROJECTIZED — PHASE 0 VALIDATION**

Not yet approved for Phase 1 Android MVP。

目前決定繼續專案化研究；決定性風險已從「有沒有資料」轉成「道路層級身份與現場號誌相位能不能可靠同步」。
