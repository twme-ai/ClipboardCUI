# ClipboardCUI

Shows where your WorldEdit clipboard will be pasted — including after `//rotate` and `//flip` — as a
WorldEditCUI multi-region. It is drawn by the [WorldEditCUI](https://github.com/EngineHub/WorldEditCUI)
client mod, or by the [WorldEditDisplay](https://github.com/TWME-TW/WorldEditDisplay) server plugin for
players without the mod.

[English](#english) | [繁體中文](#繁體中文)

---

## English

### How it works

ClipboardCUI computes the clipboard's paste footprint the same way WorldEdit pastes it
(`placement + floor(transform(point − origin))`) and sends it on the `worldedit:cui` channel as a
multi-region with a fixed UUID. It never touches WorldEdit's own selection.

| Clipboard state | Preview |
|---|---|
| No transform, any 90° rotation, any flip | Cuboid — exactly the blocks `//paste` changes |
| Rotation that is not a multiple of 90° | Polyhedron through the 8 transformed corners (approximation) |
| FAWE `//schem load -r` (random rotation) | Cuboid covering all four Y rotations |
| FAWE multiple clipboards | Cuboid enclosing all of them |

- The paste position follows `//placement` and the `-o` style origin, using WorldEdit's own
  `getPlacementPosition`.
- Players with neither WorldEditCUI nor WorldEditDisplay receive nothing (`hasCUISupport()` gate).
- The preview is re-sent after the client's CUI handshake (world change, respawn) and cleared with
  `+s|clear|<uuid>` only, so other plugins' multi-regions are left alone.
- Non-cuboid clipboard regions (polygon, cylinder, …) are shown as their bounding box.

### Packets and update rate

The CUI messages are small: 5 plugin messages per cuboid update, 16 per polyhedron update. The real
cost is on WorldEditDisplay, which respawns every display entity when a region moves (about 36
entities, ~260 packets for a cuboid preview with the default style). ClipboardCUI therefore:

- only sends when the footprint changes (block position, rotation, clipboard, world);
- **settle** mode (default) waits until the paste position has stood still for 6 ticks;
  **follow** mode updates while moving but at most once every 5 ticks;
- caps each player at 40 CUI messages per second.

Measured in the end-to-end test (Paper 1.21.11, 2 s sprint): settle sends 0 updates while
walking and 1 after stopping; follow sends 7–8 updates.

### Requirements

- Paper (or Folia) 1.21.4+, Java 21 — tested on Paper 1.21.11
- WorldEdit 7.3+ or FastAsyncWorldEdit 2.x — tested with WorldEdit 7.4.5 and FAWE 2.16.1
- For players: WorldEditCUI, or WorldEditDisplay on the server. Use a WorldEditDisplay release newer
  than 2.6.0 for the best result: older releases ignore transparent colours and draw an automatic
  grid (≈300 entities instead of ≈36), and postpone rendering while updates keep coming.

### Commands and permissions

| Command | Description |
|---|---|
| `/clipboardcui on\|off\|toggle` | Turn your preview on or off (saved on the player) |
| `/clipboardcui mode <follow\|settle>` | Choose how the preview follows you |
| `/clipboardcui status` | Show your current settings |
| `/clipboardcui reload` | Reload `config.yml` and messages (`clipboardcui.reload`, op) |

Aliases: `/clipcui`, `/clippreview`. `clipboardcui.use` (default: everyone) is needed to see the
preview and use the command.

### Configuration

See [`config.yml`](src/main/resources/config.yml): default on/off and mode, poll interval, throttle
limits and colours (`edge`, `grid`, `point1`, `point2` in `#RRGGBB[AA]`; alpha `00` hides a part,
grid spacing below 1 hides the grid).

### Building and testing

```bash
mvn package                      # target/ClipboardCUI-<version>.jar, runs unit tests
integration/run-e2e.sh           # Paper + WorldEdit + WorldEditDisplay 2.6.0 + mineflayer bots
CLIPBOARDCUI_E2E_EDITOR=fawe CLIPBOARDCUI_E2E_WED_JAR=/path/to/WorldEditDisplay.jar integration/run-e2e.sh
```

The unit tests check the footprint against WorldEdit's own `AffineTransform`, block by block, for
every axis-aligned rotation and flip.

---

## 繁體中文

### 運作方式

ClipboardCUI 用與 WorldEdit 貼上相同的算法（`貼上位置 + floor(變換(點 − 原點))`）計算剪貼簿的
貼上範圍，再以固定 UUID 的 WorldEditCUI 多選區訊息從 `worldedit:cui` 頻道送出，不會動到
WorldEdit 本身的選區。

| 剪貼簿狀態 | 預覽 |
|---|---|
| 無變換、任意 90° 旋轉、任意翻轉 | 長方體，與 `//paste` 實際改動的方塊完全一致 |
| 非 90° 倍數的旋轉 | 經 8 個變換後角點的多面體（近似） |
| FAWE `//schem load -r`（隨機旋轉） | 涵蓋 4 種 Y 軸旋轉的長方體 |
| FAWE 多剪貼簿 | 包住所有剪貼簿的長方體 |

- 貼上位置依 `//placement` 設定，直接使用 WorldEdit 的 `getPlacementPosition`。
- 沒有 WorldEditCUI、伺服器也沒有 WorldEditDisplay 的玩家不會收到任何訊息（以 `hasCUISupport()` 判斷）。
- 客戶端重新握手（換世界、重生）後會重送；清除時只送 `+s|clear|<uuid>`，不會清掉其他插件的多選區。
- 非長方體的剪貼簿區域（多邊形、圓柱等）以外框顯示。

### 封包數量與更新頻率

CUI 訊息本身很小：長方體每次 5 則、多面體 16 則。真正的成本在 WorldEditDisplay：選區每移動一次，
它會重生所有顯示實體（預設樣式的長方體預覽約 36 個實體、約 260 個封包）。因此 ClipboardCUI：

- 只在範圍改變時送出（方塊座標、旋轉、剪貼簿、世界）；
- **settle** 模式（預設）等貼上位置靜止 6 tick 才更新；**follow** 模式移動中也更新，但最多每 5 tick 一次；
- 每位玩家每秒最多 40 則 CUI 訊息。

端到端測試實測（Paper 1.21.11，衝刺 2 秒）：settle 移動中 0 次、停下後 1 次；follow 7–8 次。

### 需求

- Paper（或 Folia）1.21.4+、Java 21；已在 Paper 1.21.11 測試
- WorldEdit 7.3+ 或 FastAsyncWorldEdit 2.x；已在 WorldEdit 7.4.5、FAWE 2.16.1 測試
- 玩家端：WorldEditCUI，或伺服器安裝 WorldEditDisplay。建議使用 2.6.0 之後的 WorldEditDisplay：
  舊版不會略過透明顏色、會畫自動網格（約 300 個實體，而不是約 36 個），而且更新不斷時會一直延後渲染。

### 指令與權限

| 指令 | 說明 |
|---|---|
| `/clipboardcui on\|off\|toggle` | 開關自己的預覽（存在玩家資料中） |
| `/clipboardcui mode <follow\|settle>` | 選擇預覽跟隨方式 |
| `/clipboardcui status` | 顯示目前設定 |
| `/clipboardcui reload` | 重新載入 `config.yml` 與訊息（`clipboardcui.reload`，預設 OP） |

別名：`/clipcui`、`/clippreview`。看到預覽與使用指令需要 `clipboardcui.use`（預設所有人）。

### 設定

見 [`config.yml`](src/main/resources/config.yml)：預設開關與模式、輪詢間隔、節流上限、顏色
（`edge`、`grid`、`point1`、`point2`，格式 `#RRGGBB[AA]`；alpha `00` 表示隱藏，網格間距小於 1 表示不畫網格）。

### 建置與測試

```bash
mvn package                      # 產出 target/ClipboardCUI-<version>.jar，並執行單元測試
integration/run-e2e.sh           # Paper + WorldEdit + WorldEditDisplay 2.6.0 + mineflayer 機器人
CLIPBOARDCUI_E2E_EDITOR=fawe CLIPBOARDCUI_E2E_WED_JAR=/path/to/WorldEditDisplay.jar integration/run-e2e.sh
```

單元測試會用 WorldEdit 自己的 `AffineTransform` 逐方塊比對所有軸對齊旋轉與翻轉的貼上範圍。

## License

Apache License 2.0 — see [LICENSE](LICENSE).
