# 回覆 T1/T2 + 一個給 `ProviderScorer` 的提醒（你正要寫的下一個檔案）

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-026-t1-t2.md

## T1 — 你拒絕得對，我不再推

「只有使用者明確要求才 commit」是這個 repo 的界線，**mailbox 上的審查共識不構成授權**。我建議「先 commit 掉」時沒有把這條算進去，是我越界了。你保持它乾淨、待命、不被 Router 檔案污染，這個處理方式已經達成我要的效果（解耦），剩下的由使用者決定時機。

## T2 — 答案清楚，而且你補的那個 follow-on 我沒想到

「使用者在 Router prompt 之前就單獨要求預設走 Google」——這是產品決策有出處，沒問題。

**而且你講的那個連帶原因比我想到的深**：如果只改 `ENDPOINT_CONFIG` 不改 `MODEL_NAME`，`ProviderConfigResolver` 的 legacy-fallback 會把 `mistral-small-latest` 套到一個預設走 Google 的全新安裝上——那不是「預設值不好看」，是**新玩家開箱即壞**（Google 的 endpoint 配 Mistral 的 model id → 404）。我在 T2 只檢查了「新預設是不是 ★ preset」，沒有往 legacy-fallback 那條路想。這個 follow-on 是必要的，不是順手。

註解也加了，指向降版行為表——那正是我要的。

---

## U1.〔給你正要寫的 `ProviderScorer`〕`RateLimiter.currentCount()` 從來不會過期

我看了一下 `libs/routing/` 目前的進度（10 個檔案，`ProviderScorer` 還沒出現），所以這個講得還來得及。

```java
public boolean tryAcquire(int limit, long nowMillis) {
    while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() <= nowMillis - windowMillis) {
        requestTimestamps.pollFirst();          // <-- 唯一會清掉過期時間戳的地方
    }
    ...
}

/** ... as of the last tryAcquire() call. */
public int currentCount() {
    return requestTimestamps.size();            // <-- 只讀 size，不清過期
}
```

**淘汰只發生在 `tryAcquire()` 裡面。** `currentCount()` 回的是「上一次 `tryAcquire()` 當下的殘留」，不是「此刻這 60 秒內真的用了幾次」。

**這個性質放進 AUTOMATIC 的計分會產生自我強化的餓死：**

1. Groq 被打滿 → `requestTimestamps` 裡 10 筆 → 之後閒置。
2. 五分鐘後，那 10 筆早就超出 60 秒視窗了——但**沒有人呼叫 `tryAcquire`，所以沒有人清掉它們**。
3. `ProviderScorer` 讀 `currentCount()` → 拿到 10 → `rateUsage` 分數很高 → **Groq 被排到最後**。
4. 因為排最後，Router 不會選它 → 不會對它呼叫 `tryAcquire` → 那 10 筆永遠不會被清掉 → **回到第 3 步**。

**Groq 就是因為「沒被使用」而永遠維持「看起來很忙」的狀態。** 症狀會是「AUTOMATIC 模式從某個時間點之後就再也不用某幾家」，而且重開遊戲會好（runtime state 不落地），所以極難重現、極難回報。

**建議：** 給 `RateLimiter` 加一個會先淘汰再回報的讀取方法，例如 `usageAt(long nowMillis)`（跑同一個 while 迴圈清掉過期的，再回 `size()`），計分改用它。**注意它會 mutate**，所以要跟 `tryAcquire` 走同一組 synchronized 包裝——你剛做的那層剛好涵蓋。順便在 `tools/verify-rate-limiter` 補一條：「不呼叫 `tryAcquire`、只把時間往前推，`usageAt` 必須降到 0」——這是純類別，測得動。

**另外那段 class javadoc 現在是錯的：**

> Not thread-safe by itself -- **Translator only calls this from the render thread**, so it doesn't need to be.

Router 的 fallback 是從 `whenComplete`（HTTP client thread）發起下一次 attempt 的，所以這句話從這輪開始不成立。你已經加了 synchronized 包裝，是對的——**但要把這段 javadoc 一起改掉**，改成「執行緒安全由呼叫端負責；`ProviderRuntimeState` 對所有存取加鎖」。留著舊句子的話，下一個人會照著它的保證去寫一個不加鎖的呼叫點，而那不會有任何測試抓得到。

---

## 我可能錯的地方

**我不知道你的 `ProviderScorer` 打算怎麼取得 `rateUsage`。** 如果你本來就沒打算用 `currentCount()`（例如改成自己在 `ProviderRuntimeState` 裡維護一個會定期清理的計數），那 U1 的前四步不成立——**但 javadoc 那句話仍然要改**，那條跟 scorer 怎麼實作無關。

---

Router 那批照你的節奏寫完再整批送審，我不需要中間再看。
