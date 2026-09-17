# 01 — Taipei MVP Scope

## MVP city

臺北市。

## User-facing information

主畫面只需要：

1. 當前時速（km/h）
2. 當前道路名稱與行進方向（可小字）
3. 前方第 1 / 2 / 3 個適用路口：
   - 路口名稱
   - 距離
   - 預測燈色
   - 預估剩餘秒數
4. 前方測速設備
5. 前方科技執法

## MVP coverage strategy

### Tier A — 主要道路，高可信優先

條件：

- 可對應臺北官方號誌時制資料
- 可對應號誌位置 / 設備
- 道路方向清楚
- 可建立 phase / cycle / offset 模型
- 可判斷時制切換時段

顯示目標：

- 已同步：`🟢 12 秒`
- 有小幅漂移：`🟢 約 10–13 秒`
- 失步：`🟢 --`

### Tier B — 次幹道

官方資料存在時沿用 Tier A；不足時允許歷史模型補值，但必須降低 confidence。

### Tier C — 小巷 / 資料不完整道路

允許使用：

- 歷史通過 / 停走觀測
- 同一路口過去時段模型
- 相鄰協調號誌的相位關係
- GPS + IMU 停走事件

只能顯示約值或區間，例如：

- `🟢 約 8 秒`
- `🟢 約 6–12 秒`

無足夠資料時顯示 `--`。

## Three-intersection rule

「下一個三個路口」不是 GPS 半徑內最近三個號誌。

必須：

1. GPS map-match 到目前道路 segment
2. 取得 heading / travel direction
3. 沿道路 graph 向前 traverse
4. 排除：
   - 旁邊巷道
   - 平行道路
   - 反方向號誌
   - 不屬於目前 movement 的號誌
5. 取前三個有效 controlled intersections

## Signal identity

最小識別單位不應只有 intersection_id，至少需要：

`intersection_id + approach_direction + movement + plan/time_bucket`

movement 預留：

- through
- left
- right
- mixed / unknown

MVP 可以優先做 through；方向或 movement 無法確認時不顯示精確秒數。

## Enforcement scope

MVP 支援：

- 固定測速
- 區間測速（資料可判定時）
- 科技執法

資訊內容：

- 距離
- 限速（測速資料有提供時）
- 拍攝方向 / 適用方向
- 科技執取締項目（例如闖紅燈、不停讓行人、不依規定轉彎等）

## MVP acceptance idea

主要道路成功不是「每個路口都有數字」，而是：

- 正確找出目前道路
- 正確排出前三個適用路口
- 有把握的號誌才顯示數字
- 失步快速降級成區間或 `--`
- 測速 / 科技執法不抓到反方向設備

## No app implementation in Phase 0

本文件只定義需求。Phase 0 不寫 Android 程式。