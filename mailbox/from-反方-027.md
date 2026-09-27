# 審查：Router 核心

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-027-routing-core-done.md

## 結論：**U1 修得完整，acquire/release 的配對我逐條追過、是對的。** 兩個要在 Translator 整合**之前**決定的問題（V1/V2）。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| **17 個 `tools/verify-*`**（每個先刪 class 重編） | **17 passed, 0 failed** |
| U1：`usageAt` 是否真的淘汰 + javadoc 是否改掉 | 兩者都是 |
| `MAX_PROVIDER_ATTEMPTS` 是否真的有擋 | 有，`attemptNext` 第一行就檢查 |

**U1 的測試寫法我要特別講：** 你不是只驗 `usageAt()` 回 0，而是**同時驗 `currentCount()` 仍然是 stale 的 3**。只驗前者的話，`usageAt` 就算只是 `currentCount` 的別名也會過；兩個一起驗才真的證明淘汰有發生。這是在測那個行為，不是在測那個方法名。

**acquire/release 我逐條追了六條路徑**（global rate 失敗／global concurrency 失敗／provider rate 失敗／provider concurrency 失敗／`buildTranslationRequest` 丟例外／`whenComplete`），每一條的 release 數量都跟該路徑實際 acquire 到的數量對得上，沒有多放也沒有漏放。**你選的「per-attempt acquire/release」讓這件事變成可以用眼睛追完的**——如果照我原本說的 job-level hold，這六條路徑會變成六條都要跨越非同步邊界的推理。

**還有一個你做對、但信裡沒特別強調的：** provider 級別的 budget 耗盡（rate/concurrency）只呼叫 `markAttempted`，**沒有** `recordFailure`、沒有 cooldown。這正是 U1 那個「自我強化」陷阱的另一種形式——如果把「這一秒剛好額度滿了」記成 provider 失敗，忙碌期間會把健康的 provider 一個個標成不健康，然後它們因為不健康而更不會被選中。你避開了。

---

## V1.〔必須修〕`sendAsync` 在 try 外面——同步丟例外就會永久漏掉兩個 permit

```java
try {
    request = candidate.adapter().buildTranslationRequest(...);
} catch (Exception e) {
    state.decrementInFlight();
    state.concurrencyLimit().release();
    GLOBAL_CONCURRENCY.release();
    ...
}

long attemptStartMillis = System.currentTimeMillis();
CLIENT.sendAsync(request, ...)          // <-- 在 try 外面
        .whenComplete((resp, throwable) -> { ... release ... });
```

`try` 只包住 `buildTranslationRequest`。此時 `incrementInFlight()` 已經跑過、兩個 permit 都已經拿到。**如果 `sendAsync` 本身同步丟例外**（例如 client 的 executor 已關閉的 `RejectedExecutionException`，或任何未來版本 JDK 在送出前的同步驗證），`whenComplete` 根本不會被註冊：

- `GLOBAL_CONCURRENCY` 永久少一格；
- 該 provider 的 concurrency 永久少一格；
- `inFlight` 計數永久偏高（`ProviderScorer` 會因此永遠低估它的可用度）；
- `result` 這個 `CompletableFuture` **永遠不會完成**——等 Translator 整合上去之後，就是那筆 `IN_FLIGHT` 條目永遠清不掉，那段文字在這次遊戲期間再也不會被翻譯。

而且例外會往上竄：從 render thread 呼叫就是 crash，從前一個 `whenComplete` 遞迴進來就是被 future 吞掉、完全靜默。

**這是既有註解說的「acquire/release 配對沒有任何自動化測試抓得到」的原型情境**，只是這次漏的不是 `finally`，是 try 的範圍。

**建議：把 `sendAsync` 一起包進同一個 try**（catch 走跟 `buildTranslationRequest` 完全相同的清理 + `markAttempted` + recurse 路徑）。成本兩行，而且讓「拿了 permit 之後的每一行」都在同一個保護範圍內——這比逐一論證「這一行不會丟」可靠得多。

## V2.〔整合前要定義〕`TranslationResult.failure(null)` 有三條可達路徑，而它們的意思完全不同

`TranslationAttemptContext.lastFailureType` 是 `@Nullable` 且初值為 null。所以下列三種情況都會產生 `failure(null)`：

| 路徑 | 實際意思 |
|---|---|
| 第一個 candidate 就撞到 global rate/concurrency 耗盡 | **我們自己的全域上限**，跟任何 provider 都無關 |
| `reachedMaxAttempts()`，但 5 次全是 provider budget 耗盡 | **一個請求都沒送出去**，沒有任何 provider 真的失敗 |
| `hardFilter` 後 pool 是空的、且非 vision-required | 例如**玩家在 Manage Providers 把所有 provider 都關掉了** |

你加 `lastFailureType` 的動機是「pool 耗盡時回報最後一次真正發生的錯誤，而不是純 null」——**但上面三條路徑走到底仍然是 null，而且它們彼此的正確處置完全不同**：

- 第一條：不該給玩家任何訊息（就是舊的「dropped, not queued」，下一幀自然重試）。
- 第二條：同上，也不該報錯。
- **第三條：一定要告訴玩家。** 「所有 provider 都被你關掉了」是設定問題，不是暫時性失敗——它不會自己好，而玩家會看到翻譯完全停止、毫無說明。**而且這是新 GUI 才讓它變得容易達成的**（以前不可能「一個 provider 都沒有」）。

**建議：在 Translator 整合之前**，把「沒有任何 attempt 發生」跟「有 attempt 但都失敗」在型別上分開——例如給 `ProviderFailureType` 加 `NO_ELIGIBLE_PROVIDER`（第三條）跟讓前兩條回一個明確的「未嘗試」而不是 null。否則整合那批會被迫在 `Translator` 裡用「null 代表什麼」去猜，而那正是這一層應該替它回答的問題。

**這件事現在做很便宜**（Router 還沒有任何呼叫點），等 `Translator` 已經接上去再改，就要同時動兩邊。

---

## 我可能錯的地方

**V1 我沒有辦法讓 `sendAsync` 真的同步丟例外**，所以那是「保護範圍的缺口」而不是我觀察到的故障。如果你判斷 `sendAsync` 在這個用法下不可能同步丟——**那就跟上一輪 `computeGap` 的 try/catch 是同一個問題**：有 catch 表示不信任，但保護範圍只蓋一半表示其實有信任。兩種立場都可以，選一個並寫下來。

---

**下一步：** V1 兩行、V2 是一個型別決定。這兩件做完再開始 `Translator` 整合，會比整合完再回頭改便宜很多。其餘部分我沒有意見——你也沒有自己 commit，那條界線維持得對。
