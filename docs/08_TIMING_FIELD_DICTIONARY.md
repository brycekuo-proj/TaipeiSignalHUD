# 08 — Timing Field Dictionary

本文件整理臺北市號誌時制 JSON 與交通控制通訊協定的欄位語義。Phase 0 僅做資料理解，不實作 App。

## `segmenttype`

`timing_plan_table.json` 中觀察到值 1–7。

依交通控制器/通訊協定規格，1–7 為一般日型別；實務上可對應星期一至星期日。臺北樣本 `I0SSA` 的資料也呈現 1–5 共用平日時制、6–7 共用假日時制，與官方三合一報表 Monday–Sunday 欄位一致。

Phase 0 採用工作映射：

- 1 = Monday
- 2 = Tuesday
- 3 = Wednesday
- 4 = Thursday
- 5 = Friday
- 6 = Saturday
- 7 = Sunday

注意：特殊日/動態策略不可直接套用此映射。協定中更高值可表示特殊日型別，動態運轉也可能沒有一般 segment schedule。

## `subsegment`

一天內不同啟用時段的計畫表。

欄位：
- `subsegmentid`: 時段序號
- `time`: 啟用時間，HHMM，例如 `0700`, `1630`
- `planid（SeqNo）`: 對應的 timing plan ID

用途：根據星期與目前時間找出應用中的計畫。

## `planid（SeqNo）`

同一控制器內的時制計畫 ID / sequence number。

Join key 必須至少包含：

`deviceid + planid（SeqNo）`

不要假設單獨 `planid` 全市唯一。

## `direction`

交通控制協定 `Direct` / 基準方向值：

- 0 = 北向 N
- 1 = 東北向 NE
- 2 = 東向 E
- 3 = 東南向 SE
- 4 = 南向 S
- 5 = 西南向 SW
- 6 = 西向 W
- 7 = 西北向 NW

目前臺北 `timing_plan.json` 快照實際觀察到 0 / 2 / 4 / 6，亦即四個主要方位。

這個方向是時制資料的基準方向，不應直接等同於使用者 approach movement；後續仍需和道路 heading / movement 做映射。

## `cycletime`

號誌週期秒數。

目前資料：
- 全部為 integer
- 5 筆為 0，需要列為特殊/異常資料，不直接拿來倒數
- 常見值包括 60 / 75 / 90 / 100 / 120 / 150 / 200 秒

## `offset`

號誌時差 / offset，單位秒。

用途：協調號誌在共同時間基準上的相位偏移。

重要：公開 offset 不等於保證現場控制器此刻完全同步。正式使用前需要實地驗證：

- offset 的時間基準
- 控制器 clock drift
- plan switch 時點
- 動態控制是否覆蓋 published offset

因此 MVP 仍需要 `phase_correction_seconds` 與 continuity/confidence 機制。

## `phaseorder`

1-byte phase/timing pattern ID。

目前資料常見：
- `00`
- `B0`
- `81`
- `0A`
- `F0`
- `08`
- 等

此欄位不是可直接以字面推斷的方向碼；需依官方 phase-type coding / controller definition 解碼。

**Phase 0 規則：未知 code 不猜。**

## `subplan`

單一 timing plan 內的各 subphase 參數。

觀察欄位：

- `subphaseid`
- `mingreen`
- `maxgreen`
- `green`
- `yellow`
- `allred`
- `pedgreenflash`
- `pedred`

### `green`

目前計畫下的綠燈秒數。

### `mingreen` / `maxgreen`

控制器容許的最小 / 最大綠燈時間；在感應式或動態控制情境中特別重要。

若 `mingreen != maxgreen` 或 max range 非常寬，不代表現場一定會固定跑 `green` 秒，需納入 dynamic/actuated risk。

### `yellow`

黃燈秒數。

### `allred`

全紅清道秒數。

### `pedgreenflash` / `pedred`

行人號誌相關時制參數；不應混入車輛號誌剩餘秒數而不做 movement distinction。

## `InfoTime`

資料發布 / 產製時間。

2026-09-18 下載快照中，兩份 detailed timing JSON 的 `InfoTime` 為 2026-09-17，顯示其不是多年未更新的靜態快照。

## Signal status bit semantics — future reference

交通控制通訊協定以 bit 表示燈態，可包含：

- red
- yellow
- circular green
- left green
- through green
- right green
- pedestrian green
- pedestrian red

若未來取得 SPaT / real-time controller data，應優先使用正式狀態位，而不是自行猜 movement。

## Open decoding tasks

- [ ] 建立 `phaseorder` code → movement/state schema
- [ ] 判斷哪些路口/plan 為固定時制、感應時制或動態時制
- [ ] 驗證 `offset` 的現場 clock reference
- [ ] 驗證特殊日 segment 規則
- [ ] 建立 TTCX PDF 與 JSON 的自動欄位對照

## Reference

- 交通部 / 交通控制通訊協定相關公開規格
- 臺北市交通管制工程處號誌時制三合一報表
- 臺北市資料大平臺號誌時制資料集
