# 審查：AB1 修正——**沒有第四個，而且這次的修法讓下一次實測變成可判別的實驗**

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-037-ab1-fixed.md

## 結論：**AB1 (a)+(b) 都到位。我沒有第四個。可以請使用者重測了。**

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 18 個 `tools/verify-*`（先刪 class 重編） | **18 passed, 0 failed** |
| AB1(a) | `ProviderCandidate:51` 已改成 `BaseUrlUtil.isValid(settings.customBaseUrl())` |
| AB1(b) 的新順序 | 確認：peek 兩個 rate → **build request** → commit 兩個 rate → `incrementInFlight` → DIAG → `sendAsync` |
| build 失敗的 catch | **只釋放兩個 semaphore**，正確——那時兩個 rate 都還沒 commit，沒有「釋放不存在的東西」 |

---

## 為什麼我說沒有第四個（這是結論，不是「我想不到」）

新順序之後，**兩個 rate commit 之後只剩下一個可能失敗的關卡：`sendAsync` 本身**。

而它**不能再往前搬**——送出請求正是那個額度存在的目的。所以這條規則在這個方法裡已經套用到極限了。

殘留的那個（`sendAsync` 同步丟例外 → 兩格 rate 被燒掉、semaphore 有釋放）就是 V1 那個情境，而它跟 AB1 的差別是**它不會形成穩態迴圈**：`sendAsync` 會確定性丟例外的實際情況（client 的 executor 已關閉）意味著遊戲正在關閉，不是一個會每幀重複 60 秒的狀態。AB1 那個則是玩家設定壞了就會一直重演。

所以：**同一個模式的三次現身（原本的 → AA1 → AB1）已經收斂**，剩下的殘留是不可歸約且不會自我維持的。這是我能給的最強保證，不是「暫時沒想到」。

其他我追過、確認不屬於這個模式的：`incrementInFlight`（可歸還，四條路徑都有 decrement）、cooldown（會過期）、`IN_FLIGHT`／`screenshotTranslating`（`whenComplete` 第一行就清）、`context.recordFailure`（只覆寫欄位）。`markAttempted` 是唯一另一個 job 範圍的不可歸還資源，那就是 AB2，你決定記錄不修，我同意。

---

## 一個你可能沒注意到的副作用：這次的 log 搬移，讓下一次實測從「過/不過」變成「可判別」

你把 `[DIAG] translation request` 搬到**兩個 commit 之後、`sendAsync` 之前**，理由寫的是語意（「這個請求真的要送出去了」）。**但它還有一個診斷上的效果，值得寫進給使用者的說明：**

**從現在起，`[DIAG] translation request` 的出現次數 == 被消耗掉的 rate 額度數。** 兩者在程式碼上是相鄰的，中間沒有任何可能失敗的分支。

所以下一次實測的 log 會直接回答一個上次無法回答的問題：

- 上次：`grep -c "[DIAG] translation request"` = 2，而重試 2200+ 次——**當時無法從 log 分辨**「額度被靜默燒掉」還是「根本沒走到那一步」，你是靠讀 code 推出來的。
- 這次：如果又出現鎖死症狀、而請求數仍然很低，**「額度被沒送出的請求燒掉」這個原因就直接被排除了**——因為燒額度現在必然伴隨那行 log。

**建議請使用者回報時，除了整份 log，特別記下三個數字**：`[DIAG] translation request` 的總數、任何「請求過快 / RPM」訊息出現的時間點、以及大致的重試總量。這三個數字擺在一起，下一輪就不用再從 2487 行裡重建時間線。

---

**下一步：請使用者重跑同一個 FTB/JEI 場景。** 兩個非入侵性 bug 都修完、都有 regression test 撐著，而且這次的 log 具備上次沒有的判別力——現在是花掉那次實機測試最划算的時機。
