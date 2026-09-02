# 審查：AA1 修正 + 回答「還有沒有第三個」

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-036-aa1-fixed.md

## 結論：**AA1 修法正確。你問還有沒有第三個——有，我找到兩個，其中一個（AB1）跟 AA1 是同一個模式的第三次。**

---

## 我獨立跑的驗證

`./gradlew compileJava` 乾淨；18 個 `tools/verify-*` 先刪 class 重編，**18 passed, 0 failed**。peek-then-commit 的四條失敗路徑（peek global 失敗／peek provider 失敗／commit global 失敗／commit provider 失敗）release 配對我逐條追過，都正確。規則也寫進 javadoc 了。

**一個很小的措辭：** 你在 commit 段的註解寫「either commit failing here is treated the same as the corresponding peek failing, just one attempt later than ideal, **never a leak**」。semaphore 確實沒漏——**但 `state.tryAcquireRate` 在 global 已 commit 之後失敗的那條路徑，global 那一格是真的被燒掉了**（只是 race 窗口是微秒級、要有並發 attempt 才會發生，量級上完全可接受）。「never a leak」講的是 permit，讀起來會像「什麼都沒被消耗」。改成「never a permit leak（rate 那一格在這個罕見的 race 下仍會被消耗一次）」會更精確。

---

## AB1.〔同一個模式的第三次〕`hasCredentials()` 只檢查「非空白」，adapter 卻對「非合法」丟例外——中間那段落差就是燒額度的地方

現在的順序是：**兩個 rate 都 commit 完** → `incrementInFlight()` → `try { imageForAttempt / buildTranslationRequest / sendAsync }`。

而 `ProviderCandidate.hasCredentials()` 對 CUSTOM 的判斷是：

```java
return settings.customBaseUrl() != null && !settings.customBaseUrl().isBlank();
```

**只檢查非空白。** 但 `OpenAiCompatibleAdapter.resolveSpec` 對「非空白但不合法」的 base URL（`not a url`、`htp://foo`、中間有空格）一樣會丟——那正是 M3 第三層防線存在的理由，而 M3 那層的 javadoc 自己寫著它是為了 **「a hand-edited or pre-this-fix TOML value could bypass the GUI check entirely」**。

**所以這條路徑是可達的**（手改過的 TOML、或 M3 修正之前存下來的值），而後果是：

1. Custom Provider `enabled`、base URL 非空白但不合法 → `hasCredentials()` 回 true → 通過 `hardFilter`。
2. `attemptCandidate`：GC、PC 拿到 → peek 兩個 rate 都有額度 → **commit 兩個 rate**。
3. `buildTranslationRequest` 丟例外 → catch 釋放兩個 semaphore、`decrementInFlight` → **但兩格 rate 已經燒掉，還不回去**。
4. tooltip 每幀重試 → 全域 10/分鐘的額度在一秒內見底 → `hasGlobalRateBudget` 失敗 → **整個 job 立刻終止、其他 provider 一個都不試**。

**這跟 AA1、跟你最早修的那個，是同一個 bug 的第三次現身**：一個注定失敗的關卡排在不可歸還資源的**後面**。

**建議（(a) 是針對性的，(b) 是結構性的，我建議兩個都做）：**

- **(a) 讓 `hasCredentials()` 對 CUSTOM 改用 `BaseUrlUtil.isValid(...)`，不要只用 `!isBlank()`。** 這個方法的 javadoc 自己寫的是「would sending a request to this provider be doomed on its face」——**一個語法上不可能組成 URI 的 base URL，正是 doomed on its face**，只是現在的判斷式沒有涵蓋它。改完之後這種 candidate 在 `hardFilter` 就被濾掉，連 semaphore 都不會碰到。一行。
- **(b) 把 request 的建構移到兩個 rate commit 之前。** `imageForAttempt`/`buildTranslationRequest` 都是純運算、沒有網路，**它們是「還可能失敗的關卡」**，所以照你剛寫進 javadoc 的那條規則，它們就該排在任何不可歸還資源被消耗之前。這樣就算未來有別的原因讓 build 丟例外（新 adapter、新 provider 型態），也不會再燒到額度。

**(b) 才是讓這個模式不會有第四次的那一步**——(a) 只堵住今天已知的那個入口。

## AB2.〔設計問題，不是 bug〕`markAttempted` 是 job 範圍內的不可歸還資源，而 PRIORITY 模式下它會擋住閒置的 provider

`MAX_PROVIDER_ATTEMPTS = 5`，但 pool 有 11 個 provider。而 concurrency/rate 的 budget skip **也會呼叫 `markAttempted`**——你在 027 的設計註記 2 說明過理由（不排除的話 ranking 會一直把同一個 candidate 排第一，無限遞迴），那個理由成立。

**但這讓「這一秒剛好忙」消耗掉一個永久的 job attempt 名額。** 分模式看：

- **AUTOMATIC**：`ProviderScorer` 把 rate usage / in-flight 算進分數，所以排在前 5 的本來就是最閒的 5 個 → **影響很小**。
- **PRIORITY**：排序只看玩家設定的 priority 值，**完全不看當下負載**。所以如果前 5 順位的 provider 剛好都打滿自己的 concurrency（預設每家 2），這個 job 會把 5 次 attempt 全部燒在 budget skip 上、**一個請求都沒送**，而第 6–11 個閒置的 provider 從頭到尾沒被碰過。
- **ROUND_ROBIN**：游標會前進，所以下一個 job 會從不同起點開始，比 PRIORITY 好，但單一 job 內同樣可能 5 次都撞上忙的。

**這不是鎖死**（負載降下來就恢復），但它是「明明有容量卻碰不到」的停滯，而且**使用者那個 FTB/JEI 突發場景正是會製造它的情境**——同一瞬間幾十個 tooltip 一起要翻譯。

**建議（不急，但請在完成報告裡至少記下來）：** 讓 budget skip 不計入 `MAX_PROVIDER_ATTEMPTS`（仍然放進 `attemptedProviders` 以保證終止，但計數用另一個只算「真的送出過請求」的數字）。終止性不受影響——`attemptedProviders` 最多長到 pool 大小就自然停了。

---

## 我可能錯的地方

**AB1 我沒有實機重現**（要手改 TOML 塞一個不合法的 base URL 並啟用 Custom Provider）。推論鏈四個環節我都讀到了：`hasCredentials()` 只檢查 `isBlank`、`resolveSpec` 對不合法值丟、build 在兩個 rate commit 之後、catch 只釋放 semaphore。

**AB2 是我從程式碼推的行為，不是觀察到的故障**，而且它自我恢復。如果你評估「AUTOMATIC 是預設模式、PRIORITY 是進階使用者才會選」而決定先不動，我接受——條件是寫進完成報告，別讓它變成之後某個 PRIORITY 使用者回報「有時候會有一陣子不翻譯」時沒人想得起來的東西。

---

**下一步：** AB1 的 (a)+(b) 修完再請使用者重測。AB2 你決定要修還是先記錄。**這是我目前想得到的全部**——`incrementInFlight`（可歸還、四條路徑都有 decrement）、cooldown（會過期）、`IN_FLIGHT`／`screenshotTranslating`（`whenComplete` 第一行就清）我都追過，沒有第四個同型問題。
