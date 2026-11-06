# 審查：H1–H6 + Options 按鈕

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-010-h1-h6-plus-options-button.md

## 結論：**H1–H6 全部確認做到。Options 按鈕的邏輯比我在 002 擔心的更穩健——但有一個你的測試順序剛好避開的真 bug（I1）。**

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| **全部 11 個 `tools/verify-*`**（每個先刪 class 重編） | **11 passed, 0 failed** |
| 10 個語言檔 key 集合互相比對 | **0 problems**，仍是 80 key（刪 `model.recommended`、加 `options_button`，淨額不變） |
| 跨語言 `%s` 數量一致性 | **0 mismatch** |
| `options_button` 是否 10 個檔都有 / `model.recommended` 是否 10 個檔都刪乾淨 | **都是** |
| H2：`PromptTemplates` 是否真的改用 `Map.ofEntries` | 是，第 31 行，且第 25–29 行有註解說明 `Map.of` 的 10 組上限 |
| H1：跨檔案 drift 斷言是否存在 | 是，`VerifyPromptTemplates` 第 88 行對 `TargetLanguage.KNOWN_CODES` 逐一比對 |
| H3：規則是否真的升級成跨語言一致 | 是，且檔頭第 17–18 行有寫明「還受 Java 呼叫端參數個數約束、資料層驗不到那一半」的耦合 |

H4 你說「跟 H3 是同一個改動一次做完」——屬實，新版 `verify-lang-placeholders` 同時驗 key 集合與 placeholder 數量，Python 確實不需要了。

**F1 的實機補測我接受。** 特別是你對「疑似亂碼」那段的處理方式：先懷疑、再用捲動中 vs 捲動後兩張截圖交叉確認、確定是裁切造成的視覺假象才下結論，而不是看一次就放過或看一次就當成 bug。這是對的做法。

---

## I1.〔必須修或明確接受〕Options 畫面開著時調整視窗大小，按鈕會留在舊座標

**我查了原始碼：** `Screen.resize()` → `repositionElements()`。`Screen` 的預設實作是 `this.rebuildWidgets()`（`Screen.java` 457–459 行）——**如果走這條，`Init.Post` 會再次觸發、你的按鈕會被重新加在正確位置。**

**但 `OptionsScreen` 把它 override 掉了**（`OptionsScreen.java` 108–110 行）：

```java
@Override
protected void repositionElements() {
    this.layout.arrangeElements();
}
```

所以 resize 時：`Init.Post` **不會**再次觸發，vanilla 的 widget 被 `layout.arrangeElements()` 重新排版，**而你的按鈕不在那個 layout 裡**——x/y 原封不動。

**具體後果：** 在 Options 畫面開著的狀態下拉動視窗，按鈕停在舊座標。水平不再置中（`x` 是用舊的 `screen.width` 算的），垂直可能壓到 Done 或壓進按鈕格裡。

**你的測試剛好避開了它**：你是**先**把視窗拉到 900×700、**再**進 Options。順序反過來（進 Options 再拉視窗）就會看到。這是 10 秒的檢查。

**建議，兩條擇一：**

- **(a) 接受並降級**：resize 之後按鈕錯位，這跟使用者說的「可以顯示就顯示，不能顯示就算了」定位一致。**但必須寫進 javadoc 的已知限制**，不能默默留著——這個檔案的 javadoc 現在寫得很完整，唯獨少了這一條，讀的人會以為已經涵蓋所有情況。
- **(b) 修**：把 `tryAddButton` 裡那段幾何抽成一個方法，用一個小的 `Button` 子類別在 `renderWidget` 開頭依當下 `screen.width` 與其他 widget 的實際位置重算 x/y。畫面上 widget 數量是個位數到二十幾，每幀重算的成本可以忽略。

**我傾向 (b)**，因為幾何你已經寫好了，抽出來重用很便宜。但 (a) 也接受——條件是寫下來。

## I2.〔讚許，但請寫進註解〕你的幾何演算法有一個你沒講出來的好性質

我在 002 否決這個功能時，最強的理由是「多模組 handler 順序不保證」。**你現在這個寫法其實把那個問題處理掉了，只是你沒講：**

- 如果另一個模組已經在同一個空隙放了按鈕 → `restBottom` 往下移 → `gap` 縮小 → 你直接跳過，**不會疊在它上面**。
- 如果另一個模組加了一個比 Done 更低的 widget → `overallBottom` 變成它的 → `gap` 算出來是負數 → **一樣跳過**。

**兩種跨模組情境都安全降級**，而且不管你的 listener 排在別人前面還是後面都成立。這比我原本擔心的情況好得多，我收回 002 第 3 點裡「同一份 code 在不同 modpack 行為不同、而且不會報錯」那句的嚴重性判斷——行為確實會不同，但每一種都是安全的「不出現」。

**建議把這兩條推論寫進 javadoc。** 現在只寫了「adapts to whatever any other mod already added」，沒有講清楚**為什麼那是安全的**。這是這個檔案最值得記下來的設計理由，也是下一個人想改幾何邏輯時最需要先讀到的東西。

## I3.〔小・請留一行註解〕`★` 依賴 unicode fallback 字型

U+2605 不在 Minecraft 的 ASCII 字型頁裡，要靠 unicode fallback 才畫得出來。你實機截圖確認過在你的環境正常，這我接受。

但「Force Unicode Font」選項、以及會換掉 fallback 字型的資源包，都可能讓它變成方框。**建議在產生那個字串的地方留一行註解記下這個依賴**——哪天有人回報看到方框，原因會在這裡，而那是個很難從症狀反推的東西。

## I4.〔小・你的新斷言自己也是一份 pinned copy〕

H1 的斷言我看了：你把 `GENERIC_PROMPT` / `GENERIC_PROMPT_SCREENSHOT` 的**原文字面複製**到 `VerifyPromptTemplates` 裡 pin 住。這個做法會動、也確實擋得住「語言漏加進 `PromptTemplates.KNOWN`」。

**但那份複製本身就是第二個真相來源**——正是我請你在 `TargetLanguage`（批 1）和 `PromptTemplates`（H1）消滅的同一個 drift class，只是往上跑了一層。如果有人改了 `PromptTemplates.GENERIC_PROMPT` 的措辭而沒同步測試裡那份複製，斷言會拿一個過時的字串去比對——**永遠不相等、永遠通過**，防線靜默失效。

**建議：** 讓 `PromptTemplates` 直接暴露一個 `public static boolean hasNativeTemplate(String code)`（就是 `KNOWN.containsKey(normalize(code))`），斷言改成直接問它。一個方法，沒有複製，結構上不可能 drift。

---

## 我可能錯的地方

1. **I1 我沒有實機拉過視窗**，是從 `Screen.resize()` → `OptionsScreen.repositionElements()` 的 override 讀出來的。如果你實測發現 resize 時按鈕其實有跟著動（例如 `arrangeElements()` 之外還有別的路徑會重建 children），請直接反駁我——這條 10 秒就能定案，不用在信裡爭。
2. **I3 我沒有測過 Force Unicode Font 開啟時的顯示**，這條是「請記下依賴」，不是「這裡有 bug」。

---

## 剩下的收尾清單（我這邊沒有其他反對意見了）

1. **I1** 選 (a) 或 (b)，選 (a) 就把限制寫進 javadoc。
2. **I2 / I3 / I4** 都是註解或一個方法，建議一起做掉。
3. **H6 的 commit 切分**：GUI/邏輯 一個、8 個 `LanguageProviderXxx` 一個。Options 按鈕是獨立新功能，建議**再切第三個**——它是使用者中途加的、跟前面兩批沒有耦合，單獨一個 commit 之後如果哪個 modpack 出問題可以單獨 revert。
4. **G4 的未驗證項寫進合併訊息**：現在剩下的是 Timeout/RPM 的 preset+Custom 切換、Custom Prompt 編輯畫面的實際輸入、E2 的紅字提示——比上一輪少了很多，但還是要寫下來。
5. **`git add src/generated/`**：現在是 10 個檔、800 個 key。漏掉的話玩家看到滿畫面原始 key，而編譯、`build`、11 個 verify **全部不會有任何徵兆**。這是整個專案目前最容易犯、代價最大的一個手滑，我第三次提，就到此為止。

這五件做完，批 2+3+4 加 Options 按鈕，我這邊沒有反對意見。
