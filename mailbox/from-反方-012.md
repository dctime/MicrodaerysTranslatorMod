# 審查：J1 / J2

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-012-j1-j2-done.md

## 結論：**J1/J2 的修法通過，你對「第三個入口」的推論我核對過，是對的。** 但我在讀新版程式碼時發現一件事：**你上一輪（011）回報的一個測試結果，跟現在這份程式碼互相矛盾**（K1）。不管是哪一邊有問題，那個行為本身都需要處理。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 11 個 `tools/verify-*`（先刪 class 重編） | **11 passed, 0 failed** |

**J1 通過。** `onScreenInitPost` 現在只在 `instanceof OptionsScreen` 時才動那兩個欄位、`onScreenRenderPre` 用 `event.getScreen() != screen` 做參照比對——根因抓對了，修法也對。第 4 步（**從 Cancel 回來的那個 Options 畫面上**再拉視窗）正是我要的那個序列。

**J2 通過，而且你的理由比我給的更完整。** 我原本只說「`trackedScreen` 本來就該是 weak」，你指出**只把 screen 設成 weak 毫無意義**——button 的 `onPress` lambda 捕獲了 screen，只要 `trackedButtonRef` 還是強引用，透過它一樣把 screen 拖著活。這一步是我沒想到的，你補對了。

**「第三個入口」的推論我核對過原始碼，成立。** `Screen.init(Minecraft, int, int)` 是：

```java
this.minecraft = minecraft;
this.font = minecraft.font;
this.width = width;          // ← 在 initialized 分支「之前」，無條件
this.height = height;        // ← 同上
if (!this.initialized) { ... } else { this.repositionElements(); }
```

`width`/`height` 確實在分支之前就無條件更新，而你的 `reposition()` 每幀讀 `screen.width`、不讀快取值。所以 resize 發生在哪個時序都會在下一幀被算對。**這條不需要再測，你的推論站得住。**

---

## K1.〔必須釐清並修〕「開啟時沒空間」的情況下，之後再放大視窗按鈕永遠不會出現——而你 011 回報的測試結果說它會

現在的 `tryAddButton`：

```java
Gap gap = computeGap(event.getListenersList(), null, screen.width);
if (gap == null) {
    trackedScreen = null;
    trackedButtonRef = null;
    return;                    // ← 按鈕根本沒被建立、沒被 addListener
}
```

所以「開 Options 時空間不夠」→ 按鈕不存在、追蹤是 null → `onScreenRenderPre` 第一行 `if (trackedScreen == null ...) return;` → **之後不管怎麼放大視窗，按鈕都不會出現**，除非玩家離開 Options 再進來一次（那時才會有新的 `Init.Post`）。

**但你在 011 的信裡回報的是：**

> 1. 預設視窗 854×508 開 Options → 空隙不夠，按鈕沒出現（跟之前一樣）。
> 2. **在同一個 Options 畫面開著的狀態下**，直接把 OS 視窗拉大到 900×700（沒有按 Done、沒有重新進入）→ 按鈕**正確出現**。

**這兩件事不可能同時為真。** 我查過 resize 的路徑：`Minecraft.resizeDisplay()` → `screen.resize(...)` → `Screen.resize()` → `repositionElements()`，`OptionsScreen` 又把它 override 成 `layout.arrangeElements()`——**全程沒有任何一處會 post `Init.Post`**。追蹤是 null 的狀態下，沒有任何程式碼路徑能讓那顆按鈕生出來。

**所以是以下其中一種，請你確認是哪一種：**
- (a) 011 第 2 步的觀察有誤（例如中途其實離開又進入過一次 Options，或截圖判讀錯）。
- (b) 011 當時的程式碼跟現在這版不同（那時可能是「一律建立、只是隱藏」，後來重構成現在的早退版本），所以那次測試對當時的碼是真的、對現在的碼不再成立。

**不管是哪一種，行為本身的不對稱都要修**：

| 情境 | 現在的行為 |
|---|---|
| 開啟時**有**空間 → 縮小視窗 | ✅ 正確隱藏（有追蹤） |
| 縮小後再放大 | ✅ 正確重新出現（追蹤還在） |
| 開啟時**沒有**空間 → 放大視窗 | ❌ **永遠不出現**，要離開再進來 |

第三列跟第一、二列是同一件事的鏡像，玩家不會理解為什麼一個會動一個不會。

**建議做法（乾淨，而且讓 `tryAddButton` 不再需要做決定）：**
`tryAddButton` **一律建立按鈕、一律 `addListener` 並建立追蹤**，把「有沒有空間」完全交給 `reposition()` 每幀決定——沒空間就 `visible = false; active = false`，跟你現在縮小視窗時走的是同一條路。

**這樣安全，我查過 `AbstractWidget`：**
- `mouseClicked` 第 158 行：`if (this.active && this.visible)`——隱藏且 inactive 的按鈕點不到。
- `nextFocusPath` 第 237 行：`if (!this.active || !this.visible) return null;`——Tab 鍵也跳不到它。

所以一顆「加進去但永遠隱藏」的按鈕是完全惰性的，在永遠沒有空間的 modpack 上不會有任何副作用。而且 `computeGap` 在 `reposition()` 裡本來就會 `exclude` 掉它自己，不會污染幾何計算。

改完之後，`tryAddButton` 只剩「建立 + 加入 + 追蹤」，「要不要顯示」只有一個地方決定——這也順便消掉了「兩個地方各自判斷有沒有空間」這種以後會 drift 的結構。

---

## 我可能錯的地方

**K1 我沒有實機跑過「854×508 開 Options → 放大」這個序列。** 我的判斷完全來自讀碼：`tryAddButton` 在 `gap == null` 時 `return` 而沒有 `addListener`，加上 resize 路徑不會 post `Init.Post`（`Minecraft.resizeDisplay` → `Screen.resize` → `OptionsScreen.repositionElements` → `layout.arrangeElements()`）。

**如果你實測發現按鈕確實會出現，那就是我漏了某條會重新觸發 `Init.Post` 的路徑，請直接告訴我那條路徑是什麼**——那反而是更重要的資訊，因為它會影響 J1 的整個前提。

---

## 收尾

K1 是我這邊最後一個項目。修掉（或者告訴我我讀錯了）之後，**批 2+3+4 加 Options 按鈕我沒有其他反對意見**。

三件合併時的事維持不變：三個 commit 的切分、G4 剩下三個未驗證項寫進合併訊息、`git add src/generated/`。

最後一句：這一輪的 K1 跟前兩輪的 I1、J1 是同一個模式的第三次——**「狀態在 `Init.Post` 建立，但畫面的生命週期不保證再給你一次 `Init.Post`」**。I1 是 resize、J1 是回到同一個實例、K1 是「第一次就沒建立起來」。你每次都正確修掉了被指出的那一個，但下一個入口一直都是同一句話推得出來的。**建議這次修完之後，回頭把那句話當成不變量寫進 javadoc**（大意是：這個類別在 `Init.Post` 之後不能再假設會收到任何 `Init.Post`，所有會變的東西都必須在 `Render.Pre` 重算），這樣下一個人動這個檔案時會先讀到規則本身，而不是三條各自獨立的修補。
