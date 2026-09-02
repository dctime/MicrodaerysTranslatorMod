# I1-I4 全部處理，含 I1 的實機驗證（你點名的確切情境）

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-010.md 的 I1-I4

## I1：選 (b)，幾何抽成共用方法，每幀重新計算

`OptionsScreenButtonInjector` 重寫：

- 新增 `ScreenEvent.Render.Pre` 監聽（跟原本的 `Init.Post` 並存），追蹤最近一次加進去的按鈕
  （`trackedButton` 靜態欄位）。只要目前畫面還是 `OptionsScreen` 而且 `trackedButton != null`，
  每幀都重新呼叫共用的 `computeGap(...)`。
- 空隙足夠 → `setX`/`setY` 更新座標（用目前 `screen.width` 跟其他 widget 的**目前**位置重算，
  不是快取舊值）。
- 空隙不夠（縮小視窗、或另一個模組的 widget 剛好長出來擋住）→ `visible = false` 且
  `active = false`，確認過 `AbstractWidget.render()` 本身就用 `if (this.visible)` 擋掉繪製，
  不用另外搬到畫面外。
- `tryAddButton`（Init 時）跟 `reposition`（每幀）共用同一個 `computeGap(listeners, exclude,
  screenWidth)`，`exclude` 參數在重新計算時排除掉按鈕自己（不然自己會被算進「其他 widget」的
  邊界，污染計算）。

**實機驗證，照你說的確切情境（先開 Options，再拉視窗，不要離開再進入）：**

1. 預設視窗 854×508 開 Options → 空隙不夠，按鈕沒出現（跟之前一樣）。
2. **在同一個 Options 畫面開著的狀態下**，直接把 OS 視窗拉大到 900×700（沒有按 Done、沒有
   重新進入）→ 按鈕**正確出現**在 Done 上方，水平置中對齊新的 `screen.width`，跟其他按鈕
   沒有重疊。
3. **同一個畫面，再把視窗縮回 854×508**（一樣沒有離開）→ 按鈕**正確消失**，沒有殘留在畫面上
   某個舊座標、也沒有變成看不見但還能點的殭屍 widget。

這三張截圖直接對應你說的「10 秒就能定案」的測試，確認 (b) 的修法在你點名的確切場景下是對的。

## I2：採納，把「為什麼安全」寫進 javadoc

Class-level javadoc 現在明講兩個跨模組情境為什麼安全降級：

- 別的模組已經佔用空隙的一部分 → 它的 widget 會被算進 `restBottom` → gap 縮小 → 我們讓開，
  不會疊上去。
- 別的模組加了一個比 Done 更低的 widget → `overallBottom` 變成那個 widget 的 bottom → gap
  算出負值 → 一樣讓開。

並且明講這兩種情況都不依賴「我們的 listener 排在別人前面還是後面」。

## I3：採納，加了依賴說明

`★` 那段的 javadoc 補了一段：U+2605 不在 Minecraft 內建 ASCII 字型頁，要靠 Unicode fallback
字型渲染；「Force Unicode Font」開關或替換 fallback 字型的資源包都可能讓它變成方框；
「這次是我自己實機截圖確認正常，不是掛保證所有環境都正常」也寫進去了。

## I4：採納，開一個 `PromptTemplates.hasNativeTemplate(String)` 取代 pinned copy

新增 `public static boolean hasNativeTemplate(String languageCode)`（就是
`KNOWN.containsKey(normalize(code))`），`verify-prompt-templates` 的斷言改成直接呼叫這個方法，
刪掉了原本複製 `GENERIC_PROMPT`/`GENERIC_PROMPT_SCREENSHOT` 原文的那段——你說得對，那份複製本身
就是我在同一輪才消滅掉的同一種 drift，只是換了個檔案，現在真的只有一個真相來源了。

---

## 驗證

- `./gradlew build`：乾淨過。
- 全部 11 個 `tools/verify-*`：`ALL CHECKS PASSED`。
- I1 的三步驟實機驗證（見上）：全部符合預期，沒有例外進 log。

## 收尾清單

- **H6 + Options 按鈕的 commit 切分**：確認會照你的建議切三個 commit——GUI/邏輯一個、8 個
  `LanguageProviderXxx`（語言資料）一個、`OptionsScreenButtonInjector` + 10 個檔的
  `options_button` key 一個。
- **G4 未驗證項**：Timeout/RPM 的 preset+Custom 切換、Custom Prompt 編輯畫面的實際文字輸入、
  E2 的紅字提示——這三項我還沒有實機截圖，會寫進合併訊息，不假裝測過。
- **`git add src/generated/`**：這次合併時會確認這一步，10 個語言檔、800+ 個 key 都要進版控，
  沒有漏掉。

這五件事加上這封信裡的 I1-I4，我這邊認為批 2+3+4 加 Options 按鈕已經到可以合併的狀態，等你最後
確認。
