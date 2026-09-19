# TaipeiSignalHUD

TaipeiSignalHUD 是一個「台北市道路號誌資訊儀表」規劃專案。

目前已進入 **A37 道路測試 APK** 階段：保留既有資料研究與演算法設計，同時加入 Android GPS 前方路口候選與 Overlay 實測。

> v0.0.7-roadtest 新增新生高架匝道模式：高速/高架狀態仍持續詢問 MCP；MCP 依南北向與新生高架出口序列，優先回傳下一出口端號誌，而不是橋下平面號誌。實測確認仍固定慢 2 秒，因此 MCP timing calibration 加入 +2 秒 phase adjustment；校正存在資料表中，可再由單一路口覆寫。

## 產品目標

駕駛行進中只顯示必要資訊：

- 當前 GPS 時速
- 平面道路：沿目前有向道路前方第 1 / 2 / 3 個適用號誌的燈色與剩餘秒數（預估）
- 高架/快速道路：至少顯示下一個可到達出口匝道的出口端第一顆可靠號誌；後續第 2/3 個出口是否顯示由 Phase 0 驗證決定
- 固定測速 / 區間測速提示
- 科技執法提示

產品只提供資訊，不輸出「可以過、加速、減速、煞車」等駕駛建議；現場號誌永遠優先。

## MVP 原則

1. **台北市優先**，不追求第一版全台覆蓋。
2. **主要道路優先高可信度**：官方號誌時制 + 路口/方向 + 同步模型。
3. **小巷允許估計**：資料不足時以歷史模型 / GPS 觀測估計，UI 必須標示為約值或區間。
4. **不確定就不顯示秒數**：顯示 `--`，不硬猜。
5. **平面道路的前三個號誌沿目前有向道路拓樸向前搜尋**，不是半徑內最近三個號誌。
6. **高架/快速道路至少第一槽顯示下一個可到達出口匝道的出口端號誌**，不可誤抓正下方平面道路號誌；第 2/3 槽需經驗證後再決定。
7. **號誌不假設永遠連續**：需處理時制切換、動態號誌、跳相、延長、閃光、臨時控制與資料失步。

## 目前已取得的原始資料（本機，不進 Git）

- 臺北市路口號誌時制索引 CSV
- 臺北市號誌位置 CSV
- 臺北市固定測速/區間測速 CSV
- 臺北市科技執法 CSV
- 臺北市 VD XML 快照
- 臺北市號誌時制 JSON（兩份大型快照，已下載；raw 不進 Git）

詳細來源與授權見 `docs/02_DATA_SOURCES_AND_LICENSES.md`。

## 文件索引

- `docs/00_PROJECT_SCOPE.md` — 專案定位與邊界
- `docs/01_MVP_SCOPE_TAIPEI.md` — 台北 MVP 範圍
- `docs/02_DATA_SOURCES_AND_LICENSES.md` — 資料來源與授權
- `docs/03_SIGNAL_MODEL_AND_CONFIDENCE.md` — 號誌模型、同步與可信度
- `docs/04_ROAD_PRIORITY_AND_FALLBACK.md` — 主要道路優先與小巷估計策略
- `docs/05_UI_SPEC.md` — 極簡 HUD UI 規格
- `docs/06_TEST_AND_VALIDATION_PLAN.md` — 測試與驗證
- `docs/07_RISKS_AND_OPEN_QUESTIONS.md` — 風險與待驗證問題
- `docs/08_TIMING_FIELD_DICTIONARY.md` — 時制欄位與方向/星期語義
- `docs/09_NORMALIZED_DATA_MODEL.md` — 標準化資料模型
- `docs/10_PHASE0_BACKLOG.md` — Phase 0 研究/資料待辦（不含 App 開發）
- `docs/11_MULTI_LEVEL_EXPRESSWAY_MODEL.md` — 高架/快速道路、平面道路、匝道與多層路網辨識
- `docs/12_CURRENT_STATE_2026-09-18.md` — 目前全部決策、資料結果與 Phase 0 gate 的同步快照
- `research/DATA_PROFILE_2026-09-18.md` — 時制資料剖析
- `research/JOIN_PROFILE_2026-09-18.md` — 官方資料 join 覆蓋分析
- `research/MAJOR_ROAD_COVERAGE_2026-09-18.md` — 主要道路路名 heuristic 覆蓋分析
- `data/README.md` — 資料目錄規範
- `data/manifests/source_inventory.csv` — 資料來源清單
- `data/manifests/main_roads_taipei.csv` — MVP 主要道路初始優先表
- `data/manifests/raw_snapshot_2026-09-18.csv` — 本機 raw 資料快照檔名、大小、SHA-256 與修改時間（raw 本體不進 Git）

## 專案狀態

**PROJECTIZED — Phase 0 Validation + Android Road-Test Prototype**

已開始 Android 實車驗證。v0.0.2-roadtest 會載入台北號誌位置與時制資料，以 GPS/heading 選出前方候選路口，並以單顆可變色圓燈 Overlay 顯示估算燈色與倒數；不可靠的複雜時相或多層道路狀態維持 UNKNOWN / `--`。