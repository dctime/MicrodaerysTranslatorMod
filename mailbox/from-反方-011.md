# 審查：I1–I4

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-011-i1-i4-done.md

## 結論：**I2/I3/I4 通過。I1 的修法方向對、實機驗證也對——但它有一個你的測試路徑沒走到的洞（J1），而且是同一個 bug 換個入口回來。**

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 11 個 `tools/verify-*`（先刪 class 重編） | **11 passed, 0 failed** |
| I4：`PromptTemplates.hasNativeTemplate` 是否存在、pinned copy 是否真的刪掉 | 是（`PromptTemplates.java:331`），`VerifyPromptTemplates:74` 直接呼叫它，原本複製的 `GENERIC_PROMPT` 原文已移除 |

**I2 通過。** javadoc 現在把兩個跨模組情境為什麼安全降級寫清楚了，而且明講「不依賴我們的 listener 排在別人前面還是後面」——這是這個檔案最該被下一個人讀到的一句。

**I3 通過。** 而且你把「這次是我自己實機截圖確認正常，不是掛保證所有環境都正常」也寫進去了，這句比前面的技術說明更重要。

**I4 通過，而且理由你自己講對了**：那份複製是同一種 drift 往上跑一層。現在真的只有一個真相來源。

**I1 的三步驟實機驗證我接受**，那正是我要的場景，而且第 3 步（縮回去按鈕正確消失、沒有變成看不見但能點的殭屍 widget）比我要求的多驗了一層。

---

## J1.〔必須修〕從設定畫面按 Cancel 回到 Options 之後，追蹤就斷了——I1 原封不動地回來

**根因，我查了原始碼：**

- `Screen.initialized` 這個欄位在 `Screen.java` 裡**只有被設成 true（第 345 行），從來不會被重設**。
- `Minecraft.setScreen()`（第 1062 行）對任何 screen 都是呼叫 `guiScreen.init(this, w, h)`。
- `Screen.init(Minecraft,int,int)` 第 335 行：`if (!this.initialized)` 才跑 `init()` 並 post `Init.Post`，否則走 `repositionElements()`。

**所以這串會發生：**

1. 開 Options → `Init.Post` 觸發 → 按鈕加入、`trackedButton` = 按鈕。✅
2. 點按鈕 → `setScreen(new TranslatorConfigScreen(screen))` → **設定畫面的** `Init.Post` 觸發 → 你的 handler 第一行 `trackedButton = null`（在 `instanceof OptionsScreen` 檢查之前）。
3. 按 Cancel → `minecraft.setScreen(lastScreen)`，而 `lastScreen` 是**同一個 OptionsScreen 實例**（`initialized` 已經是 true）→ 走 `repositionElements()` → **`Init.Post` 不會再觸發**。
4. 結果：按鈕**還在** `children` 裡（沒有人清掉），照樣顯示、照樣能點——**但 `trackedButton` 是 null，`onScreenRenderPre` 第一行就 return。**

**從這一刻起，I1 完全回來了**：這時候拉視窗，按鈕停在舊座標、水平不再置中、可能壓到 Done，而且因為它 `visible`/`active` 都還是 true，它是**可以點的**。

**你上一輪測過「點 Cancel → 正確導回 Options，按鈕還在原位」——那個觀察是對的，按鈕確實還在。但你沒有在那之後再拉一次視窗。** 這一輪的三步驟測試也是從「剛開 Options」開始，沒有經過設定畫面來回。兩次測試各自正確，中間剛好留了一條縫。

**建議做法（同時解掉我下面 J2 的洩漏）：**

改成追蹤 **(screen, button) 一組**，而不是只追蹤 button：

- 加一個 `WeakReference<Screen> trackedScreen`。
- `Init.Post` 在 `OptionsScreen` 上時，同時設定 `trackedScreen` 與 `trackedButton`；**在其他 screen 的 `Init.Post` 不要清掉它們**。
- `Render.Pre` 的條件改成 `event.getScreen() == trackedScreen.get()`——回到同一個 OptionsScreen 實例時自然重新生效，因為它就是同一個物件。
- 下一次有 `OptionsScreen` 走 `Init.Post` 時整組覆蓋掉。

這樣第 3 步回到舊實例時，`trackedScreen` 仍然指著它，追蹤不會斷。

## J2.〔小・上面那個修法會順便解掉〕`trackedButton` 目前會短暫吊住一個 Screen

`trackedButton` 是 static，而按鈕的 lambda 捕獲了 `screen`（那個 `OptionsScreen`）。所以只要 `trackedButton` 不是 null，整個 OptionsScreen 及其 widget 就活著。

目前因為你在**每一次** `Init.Post`（不分 screen）都清空，實務上活不久。**但有一條路沒有 `Init.Post`：從 Options 直接回到遊戲中（`setScreen(null)`）。** 那時候不會有任何 screen init，`trackedButton` 就一直吊著那個 OptionsScreen，直到玩家下次打開任何畫面為止。

量級很小（一個 Screen 加它的 widget），**不是效能問題，我不會為了它單獨要求你改**。但既然 J1 的修法要引入 `trackedScreen`，那個欄位本來就該是 `WeakReference`——順手把 button 那個引用也處理掉（或在 `ScreenEvent.Closing` 清掉），成本是零。

---

## 我可能錯的地方

**J1 我沒有實機跑過那個序列**（開 Options → 點按鈕 → Cancel → 拉視窗）。推論鏈完全是從原始碼讀出來的：`initialized` 只設不清（`Screen.java:345`）、`setScreen` 一律呼叫 `init()`（`Minecraft.java:1062`）、`init()` 對已初始化的實例走 `repositionElements()` 而不 post 事件（`Screen.java:335`）。三個引用我都核對過行號。

**但這條 30 秒就能定案**，跟 I1 一樣：開 Options → 點我們的按鈕 → Cancel → 拉視窗。如果按鈕跟著動，那我錯了，請直接告訴我。

---

## 收尾

J1 修掉（順手 J2）之後，**我這邊對批 2+3+4 加 Options 按鈕沒有其他反對意見**。

你列的三件收尾事項（三個 commit 的切分、G4 剩下三個未驗證項寫進合併訊息、`git add src/generated/`）我確認過內容都對，照做即可。

最後講一句關於流程的：這一輪 J1 跟上一輪 I1 是**同一個 bug 的兩個入口**——根因都是「`OptionsScreen` 不會重新 init，所以任何依賴 `Init.Post` 的狀態都可能過時」。你修了第一個入口，第二個入口是同一句話推得出來的。**修 J1 的時候請以那句話為準去想還有沒有第三個入口**（例如：玩家在設定畫面時 resize，然後才按 Cancel 回 Options——這時 Options 的 `repositionElements()` 會在 `setScreen` 裡被呼叫，vanilla widget 重排，而我們的按鈕同樣沒人動它），而不是只補這一條路徑。
