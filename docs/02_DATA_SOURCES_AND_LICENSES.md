# 02 — Data Sources and Licenses

Snapshot date for this planning pass: **2026-09-18 (Asia/Taipei)**.

## 1. 臺北市路口號誌時制計畫 — Core

Dataset page:
https://data.taipei/dataset/detail?id=0d639f73-cbcc-42c3-aa53-20efac199701

Purpose:
- 官方號誌時制索引
- intersection / device 對應
- 路口名稱、座標、群組
- 個別時制 PDF / JSON 的入口
- 建立 cycle / phase / offset / plan 時段模型的核心先驗

Current local snapshot:
- `data/raw/taipei_signal_timing.csv`
- 約 2,809 筆資料列（不含 header，2026-09-18 實際下載快照）
- 原始 CSV 本身是索引，真正 phase 細節需要搭配 JSON / 個別時制資料。

Large JSON endpoints:
- https://tcgbusfs.blob.core.windows.net/dotapp/timing_plan_table.json
- https://tcgbusfs.blob.core.windows.net/dotapp/timing_plan.json

DataTaipei metadata states the dataset is public / free, updated irregularly.

## 2. 臺北市號誌位置 — Core

Dataset page:
https://data.taipei/dataset/detail?id=4a599738-6550-448a-9a78-03b26c67e249

Fields:
- 流水號
- 地點 / 路口名稱
- 行政區
- WGS 經度
- WGS 緯度

Current local snapshot:
- `data/raw/taipei_signal_locations.csv`
- 約 2,710 筆資料列
- 原始檔為 CP950 / tab-separated content despite `.csv` extension；後續 normalize 時需特別處理。

Use:
- GPS nearest-candidate filtering
- intersection index
- 與 timing plan / OSM road graph join

## 3. 臺北市固定測速照相地點表 — Core for enforcement

Dataset page:
https://data.taipei/dataset/detail?id=745b8808-061f-4f5b-9a62-da1590c049a9

Fields include:
- 功能
- 設置路段
- 設置地點
- 緯度 / 經度
- 拍攝方向
- 速限

Current local snapshot:
- `data/raw/taipei_speed_cameras.csv`
- 143 筆資料列

Use:
- 固定測速
- 區間測速（若資料記錄可識別）
- direction filter
- speed-limit display

## 4. 臺北市智慧管理科技執法設備資料表 — Core for enforcement

Dataset page:
https://data.taipei/dataset/detail?id=986fa73e-c470-4ebf-9f35-3a1c9d2a8788

Current metadata at planning time:
- file updated 2026-08-21
- coverage end 2026-08-19
- public / free

Fields include:
- 行政區
- 科技執法種類
- 取締項目
- 設置地點
- 緯度 / 經度
- 啟用日期

Current local snapshot:
- `data/raw/taipei_tech_enforcement.csv`
- 95 筆資料列

Example enforcement items include combinations such as:
- 闖紅燈
- 不停讓行人
- 不依規定轉彎
- 不依標誌標線號誌指示行駛

## 5. 臺北市車輛偵測器 VD — Auxiliary / verify freshness before use

Dataset page:
https://data.taipei/dataset/detail?id=e57afe7f-3c9e-4f31-9208-eed859a92600

Endpoint observed:
https://tcgbusfs.blob.core.windows.net/blobtisv/GetVDDATA.xml

Potential fields:
- DeviceID
- TimeInterval
- LaneNO
- Volume
- AvgSpeed
- AvgOccupancy

Important finding from 2026-09-18 snapshot:
- downloaded XML `ExchangeTime` showed **2024/11/14T16:46:02**.
- therefore this endpoint must **not** be assumed live/reliable until freshness is independently verified.

VD role if a fresh source is found:
- congestion / queue detection
- reject stop events caused by traffic jam
- anomaly detection

VD must not be treated as traffic-signal ground truth.

## 6. 臺北市交通號誌設備圖資 — Auxiliary

Dataset page:
https://data.taipei/dataset/detail?id=5818c0cf-6cd1-4ddf-84a8-9aff0196dd73

Fields include:
- geometry
- 號誌編號
- 號誌種類
- 號誌架設方式
- 使用狀態

Use:
- disambiguate multiple physical signal heads
- later movement / approach matching research

Official note says the utility/asset map is for reference and exact information should be confirmed with the responsible agency.

## 7. OpenStreetMap — Road topology / map matching

Planned role:
- road graph
- road class
- direction / one-way
- lanes / turn lanes where available
- traffic signal tagging

Do not copy Google Maps road geometry into the dataset.

OSM usage requires compliance with ODbL attribution / share-alike obligations where applicable. Before product release, document exact extraction and attribution implementation.

## 8. Google Traffic / Routes — Optional, not required for core MVP

Potential role:
- current congestion classification
- reject jam-induced stop observations

Not core because:
- paid API / billing dependency
- position/route query goes to Google
- must comply with Google Maps Platform terms on storage and derived data

MVP planning should remain viable without Google Traffic.

## 9. TCROS / SPaT — Future highest-priority real-time source

If a legal / supported interface becomes available for relevant Taipei intersections, SPaT should override prediction because it is closer to ground truth.

Not an MVP dependency.

## Source priority

1. Official Taipei signal timing / signal location
2. Official enforcement data
3. OSM road topology
4. Local GPS/GNSS + IMU observations
5. Fresh official VD / traffic data if verified
6. Optional Google traffic
7. Future SPaT / V2X

## Repository policy

Large or frequently changing raw snapshots stay under `data/raw/` and are ignored by Git.
Git stores:
- source manifests
- schemas
- data profiles
- normalization rules
- derived small metadata tables

This avoids turning GitHub into a stale mirror of government datasets.