# 審查：Translator 整合

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-029-translator-integration-done.md

## 結論：**整合本身乾淨——我上一封說要盯的 `IN_FLIGHT`／future 配對，全部正確。** 一個必須修（W1），兩個小的。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 17 個 `tools/verify-*`（先刪 class 重編） | **17 passed, 0 failed** |
| R2（job identity 只解析一次） | **正確**：`jobKey` 在 540 行解析，591（add）／594（remove）／620（cache put）全部重用同一個值，註解也寫明「不是重新 `keyFor()`」 |
| `IN_FLIGHT`／`screenshotTranslating` 是否在所有路徑被清 | **正確**：`whenComplete` 的**第一行**就清，在任何分支之前——future 就算異常完成也會清掉 |
| `resetHttpErrorFlags()` 是否還有呼叫點 | 有，第 624 行，在翻譯成功之後 |

**`whenComplete` 第一行就清理**這個寫法值得講：它讓「future 完成了但 `IN_FLIGHT` 沒清」變成結構上不可能，而不是靠每個分支各自記得。這正是 V1 那類漏洞在整合層的對應版本，你自己處理掉了。

**你自己抓到 `verify-concurrency` 是舊架構的複製品**——而且抓到的理由對：它會**繼續綠燈**，同時檔頭在描述一個已經被刪掉的架構。綠燈的過時測試比紅燈的測試危險，因為沒有人會去看它。

---

## W1.〔必須修〕`NO_ELIGIBLE_PROVIDER` 跟一般錯誤共用 `hasShowOtherError`，而它是一個**永遠不可能被重設**的旗標

```java
case NO_ELIGIBLE_PROVIDER -> showMessage(..., () -> hasShowOtherError, () -> hasShowOtherError = true);
case SERVER, BAD_REQUEST, MALFORMED_RESPONSE, UNKNOWN -> showMessage(..., () -> hasShowOtherError, ...);
```

而 `resetHttpErrorFlags()`（729 行）**唯一的呼叫點是翻譯成功之後**（624 行）。

**問題在於這兩件事的生命週期完全不同：**

- 暫時性錯誤（500／timeout）**可以**被下一次成功清掉——這個設計對。
- `NO_ELIGIBLE_PROVIDER` 是**設定問題**：只要它成立，就**不可能有任何一次翻譯成功**。所以能重設旗標的那個事件，在這個狀態下**依定義永遠不會發生**。

**具體的死結：**

1. 玩家遇到一次暫時的 500 → `hasShowOtherError = true`。
2. 玩家之後在 Manage Providers 把所有 provider 都關掉。
3. `NO_ELIGIBLE_PROVIDER` 觸發 → **訊息被旗標擋掉**。
4. 因為沒有任何 provider，永遠不會有成功的翻譯 → `resetHttpErrorFlags()` 永遠不會被呼叫 → **旗標永遠是 true**。
5. 玩家在這整個 session 裡再也不會被告知翻譯為什麼停了,只能重開遊戲。

**這正是 V2 那整輪要解決的東西**——我們特地為「玩家把所有 provider 關掉」做了一個新型別跟一則新訊息,而它現在是整個 switch 裡**最容易被永久吞掉**的一則。

**建議：**
1. `NO_ELIGIBLE_PROVIDER` 用**自己的旗標**，不要跟暫時性錯誤共用。
2. 那個旗標的重設點不是「翻譯成功」，而是**「provider 設定被改過」**——也就是 `PendingTranslatorConfig.saveToConfig()`（或 provider pool 真正被重新讀取的地方）。那才是唯一能改變這個條件的事件。

## W2.〔小〕通用分支把 enum 名稱直接印給玩家看，而且是雙語字面字串

```java
case SERVER, BAD_REQUEST, MALFORMED_RESPONSE, UNKNOWN -> showMessage(
        "Translation failed! (" + failure + ")",
        "翻譯失敗! (" + failure + ")", ...
```

玩家會看到「翻譯失敗! (MALFORMED_RESPONSE)」。兩件事：

- **`MALFORMED_RESPONSE` 是開發者詞彙**，出現在玩家訊息裡。診斷用意我理解，但它對玩家不構成任何可行動的資訊——如果目的是方便回報，`LOGGER` 那邊記就夠了。
- **這是新增的雙語字面字串**，而 P1 才剛確立「基礎設施蓋好之後新增的訊息一律走 `Component.translatable`」。同一個 switch 裡的 `UNSUPPORTED_CAPABILITY` 分支你就是用 translatable 寫的——**不對稱就在同一個畫面內看得到**。

不擋這批（G1 本來就要重構這一整組），但**請把這一則加進 G1 的清單**，別讓它變成又一筆「答應要修時又新增的」。

## W3.〔一句話的誠實度〕`verify-concurrency` 讀的是編譯期常數，不是執行期的值

`GLOBAL_CONCURRENCY_LIMIT` 是 `public static final int = 8`，所以 javac 會把字面值 `8` **內聯**進測試的 bytecode——測試執行時**從來不會載入 `TranslationRouter`**（也不能，`Config.<clinit>` 需要 NeoForge）。

**這正是它能通過的原因**，而且因為 verify 腳本每次都重新編譯，防漂移的效果實際上是有的。但你信裡寫「讀真正的 `TranslationRouter.GLOBAL_CONCURRENCY_LIMIT`」——**準確講是「在編譯當下讀」**。建議在檔頭那段誠實揭露裡補一句，因為那個檔案的整個慣例就是把「這個檢查到底證明了什麼」講清楚。

---

## 我可能錯的地方

**W1 的第 1 步我假設玩家先遇到暫時性錯誤、之後才關掉所有 provider。** 如果順序反過來（先關光、再沒有其他錯誤），訊息會正常顯示一次。**但「顯示一次之後就再也不會顯示」本身仍然是問題**：玩家關掉遊戲隔天回來、或中途忘了，這個 session 內不會再有第二次提示，而條件還在。所以不管順序如何，重設點錯了這件事都成立。

---

**下一步：** W1 修掉再進 migration 那批。你信裡自己標的那個風險（現在上遊戲測，既有玩家會直接落在 AUTOMATIC）我同意，migration 沒接上之前不要開遊戲測——這個判斷是對的，不要因為想早點看到東西在跑就跳過。
