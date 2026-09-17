# 03 — Signal Model and Confidence

## Goal

輸出「前方適用號誌目前預測燈態 + 剩餘時間」，但不假裝所有路口都能永遠精確同步。

## Core identity

每個模型至少用：

`intersection_id + approach_direction + movement + active_plan`

其中：
- `intersection_id`: 路口
- `approach_direction`: 東/西/南/北或實際 bearing bucket
- `movement`: through / left / right / mixed / unknown
- `active_plan`: 平日/假日 + 時段 + 特殊 plan

## Fixed-plan baseline

若官方資料可得到：

- cycle length `C`
- offset `O`
- phase boundaries
- plan activation window

則基礎 phase 可寫為：

`phase = (t - O + correction) mod C`

剩餘秒數由目前 phase 到下一 transition 計算。

## The real hard problem: synchronization

官方 plan 是先驗，不代表控制器此刻一定與資料完全同步。

必須另維護：

- `phase_correction_seconds`
- `last_anchor_time`
- `sync_age`
- `sync_confidence`
- `continuity_state`

## Anchor observations

可作為 phase anchor 的事件：

- 停止線附近排隊車流開始釋放
- 使用者由停止轉為移動後通過停止線
- 多次觀測聚合後的 green-onset estimate
- 未來合法 SPaT 事件

單一使用者起步不能直接當成綠燈亮起，因為前方可能有排隊。Anchor 必須聚合、估計 queue discharge delay。

## Continuity states

### `FIXED_SYNCED`

官方固定時制，且最近 anchor / 行為觀測與模型吻合。

UI 可顯示單一倒數：

`🟢 12 秒`

### `VARIABLE_TRACKED`

時制可能變動，但最近仍有可用 anchor，模型能維持追蹤。

UI 顯示約值或窄區間：

`🟢 約 10–13 秒`

### `UNSYNCED`

知道路口與可能時制，但目前 phase 無法可靠定位。

UI：`🟢 --` 或僅顯示燈態（若燈態本身可靠）。

### `ANOMALY`

觀測與模型明顯失配，例如：
- phase 延長
- phase 跳過
- plan 臨時切換
- GPS/road matching 不一致

UI 立即降級，不繼續硬倒數。

### `FLASHING`

閃黃 / 閃紅等非一般循環狀態。

不提供普通 countdown。

### `NO_SIGNAL`

目前道路前方沒有適用交通號誌。

## Non-continuous signal handling

演算法不能假設：

`green → yellow → red → green` 永遠固定循環。

要能偵測：
- 尖峰/離峰 plan change
- 平日/假日 plan change
- 特殊日期
- 行人專用 / 早開時相
- 感應式號誌
- 動態式號誌
- 大眾運輸優先
- 緊急車輛優先
- 人工控制
- 閃光
- 維修 / 故障

## Confidence model

建議內部 confidence 0–1，由下列訊號組合：

### Positive
- 官方 timing plan match
- GPS map-match confidence 高
- direction match
- plan activation window 明確
- recent anchor
- recent observations residual 小
- adjacent coordinated signals consistent

### Negative
- anchor 過舊
- GPS accuracy 差
- 路口密集造成 road ambiguity
- dynamic-signal flag
- traffic anomaly / queue spillback
- observation residual 連續偏大
- official data age 過久

## UI mapping

建議：

- confidence >= 0.90：`12 秒`
- 0.75–0.90：`約 10–13 秒`
- 0.55–0.75：只顯示寬區間，或依測試結果直接 `--`
- < 0.55：`--`

門檻不是定案值，需用實車 Ground Truth 校正。

## Main-road vs alley behavior

主要道路：
- 追求 official-plan + sync model
- 優先 exact/narrow countdown

小巷：
- historical inference allowed
- confidence 上限應保守
- 不把純估計顯示成精確值

## Output model (future implementation contract)

```text
SignalEstimate
- intersection_id
- intersection_name
- approach_direction
- movement
- distance_m
- light_state: GREEN | YELLOW | RED | UNKNOWN | FLASHING
- remaining_min_s
- remaining_max_s
- confidence
- continuity_state
- source_class: OFFICIAL_SYNCED | OFFICIAL_INFERRED | HISTORICAL | SPAT
- generated_at
```

Phase 0 只定義資料契約，不實作 App。