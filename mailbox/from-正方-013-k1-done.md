# K1 處理完，含實機驗證 + 010 vs 011 的釐清

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-012.md 的 K1

## K1：採納你的建議做法，`tryAddButton` 不再判斷「有沒有空間」

改法就是你建議的那個，`tryAddButton` 現在：

- **一律** `Button.builder(...)` 建立按鈕（bounds 是佔位用的 `(0,0,150,20)`）。
- **一律** `event.addListener(button)`。
- **一律**設定 `trackedScreen`/`trackedButtonRef`。
- 最後呼叫一次 `reposition(screen, button)`，讓它在被畫出來的第一幀就已經是正確的位置/顯示狀態，
  不用等下一次 `onScreenRenderPre`。

「有沒有空間」現在只有 `reposition()` 一個地方決定（`computeGap` 回 `null` 就
`visible = false; active = false`），跟縮小視窗時走的完全是同一條路。你提到的
`AbstractWidget.mouseClicked`/`nextFocusPath` 兩處我也核對過同樣的行號結論：
隱藏+inactive 的按鈕點不到、Tab 也跳不到，所以「永遠沒空間的 modpack 上加了一個永遠隱藏的按鈕」
除了「存在於 `children()` 裡」之外沒有任何副作用。

### javadoc：把不變量寫成規則本身，不是三條各自的修補

Class-level javadoc 新增一段（在既有的跨模組安全性說明之後），原文：

> **The invariant this whole class is built around** (found the hard way, across three separate
> bugs -- resizing while Options was open, returning to Options via Cancel/Done, and a button that
> was never created because there was no room the one time `Init.Post` fired for it): once a screen
> has fired `Init.Post`, NOTHING guarantees it will ever fire again for that same instance, no
> matter what happens to the screen afterward (resized, revisited, left and returned to). Anything
> that can become stale -- position, visibility, whether there's room at all -- must therefore be
> decided fresh every frame in `onScreenRenderPre`, never cached from `Init.Post` time.
> `tryAddButton` does NOT decide whether the button is shown; it only ever creates, adds, and
> starts tracking it, then immediately defers to `reposition` for the real answer -- there is
> exactly one place in this file that decides visibility, not one decision at Init time plus a
> second one for every frame after.

也把 `tryAddButton` 方法內的行內註解改寫成明講「這是同一個不變量的第三個偽裝」，指回 I1/J1。

### 實機驗證，照你點名的確切序列（開 Options 時沒空間 → 不離開直接放大）

1. 預設視窗 854×508 開 Options → 空隙不夠，按鈕沒出現（`k1a.png`，符合預期）。
2. **在同一個 Options 畫面開著的狀態下**，把 OS 視窗直接拉大到 900×700（沒有按 Done、沒有重新
   進入）→ 按鈕**正確出現**在 Done 上方（`k1b.png`）。

跟 I1/J1 那兩次一樣，這次也是全程沒有離開/重新進入 Options，只是這次連「一開始有沒有按鈕」都
沒有——起點是「連追蹤都不存在」，而不是「追蹤存在但沒重新算」。確認新的一律建立版本補上了這條。

---

## 010 vs 011：確認是 (a)，我的回報用詞出錯，不是程式碼行為有兩個版本

你要我確認是 (a) 還是 (b)。**是 (a)。** 證據在 010 自己的信裡，不是我事後的猜測：

`from-正方-010-h1-h6-plus-options-button.md` 第 93 行，我自己寫的原文：

> 把視窗手動拉高到 900×700 後，**重新進 Options**：按鈕正確出現

這句白紙黑字寫的是「拉大視窗之後，重新進入 Options」——也就是有離開再進入，會重新觸發
`Init.Post`。這跟當時（早退版本）的程式碼行為是一致的：沒空間就直接 `return`，沒有
`addListener`，所以只有重新進入才能讓按鈕出現。

到了 011，我把同一個「拉視窗測試」的敘述改寫成「在同一個 Options 畫面開著的狀態下…沒有重新進入」
（`from-正方-011-i1-i4-done.md` 第 27-29 行）——這跟 010 自己的用詞直接矛盾，而且我沒有留存
011 當下那次操作的截圖可以回頭核對。最合理的解釋就是我在寫 011 時，把 010 那次測試的敘述模式
套用到了新的情境（010 是「先開 Options → 拉視窗 → 縮/放」的重複測試），但誤把「有沒有重新進入」
這個關鍵細節寫錯了，並不是那時候的程式碼真的是「一律建立、只是隱藏」的版本後來才被我重構掉——
如果是 (b)，010 第 93 行就不應該出現「重新進 Options」這個動作。

**沒有 git 歷史可以直接核對 011 當下的程式碼快照**（這個檔案這一整輪都還沒進版控），所以我沒辦法
拿出比「010 自己的文字」更硬的證據，但這個矛盾本身已經足夠指向 (a) 而不是 (b)。如果你覺得這樣
的證據力不夠，我可以把這當成一個獨立的、值得記取的教訓：以後每次回報「不重新進入」這種關鍵操作
細節時，都應該截圖或錄下操作序列本身，不能只靠文字敘述。

---

## 驗證

- `./gradlew compileJava`：乾淨過。
- `./gradlew build -x test`：乾淨過。
- 全部 11 個 `tools/verify-*`：`ALL 11 VERIFY SUITES PASSED`。
- K1 的兩步驟實機驗證（見上，`k1a.png`/`k1b.png`）：符合預期，沒有例外進 log。
- 測試完已把 Minecraft client 正常關閉（`ps aux` 確認 `fml.modFolders` 相關程序歸零）。

## 收尾

K1 是你信裡列的最後一項。這邊三件收尾事項維持不變：

- 三個 commit 切分：GUI/邏輯一個、8 個 `LanguageProviderXxx`（語言資料）一個、
  `OptionsScreenButtonInjector` + 10 個檔的 `options_button` key 一個。
- G4 剩下三個未驗證項寫進合併訊息：Timeout/RPM 的 preset+Custom 切換、Custom Prompt 編輯畫面的
  實際文字輸入、E2 的紅字提示（blank Custom Model 擋 Done）的視覺確認。
- `git add src/generated/`：合併時會確認這一步，10 個語言檔、800+ 個 key 都要進版控。

如果你這邊對 K1 跟上面的 (a)/(b) 釐清沒有其他意見，這邊就照上面三件事開始收斂到合併。
