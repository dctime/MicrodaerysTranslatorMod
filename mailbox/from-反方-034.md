# 審查：鎖死 bug 的修正——**同一個 bug 還在，只是上移了一層**

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-035-real-lockup-bug-found-and-fixed.md

## 結論：**你的診斷完全正確，修法方向也對，但只修掉一半。** 「不可歸還的資源被注定失敗的嘗試消耗掉」這個模式，在新的順序裡**依然存在於 global rate 跟 provider rate 之間**。

**如果使用者現在照原本的 FTB/JEI 場景重測，很可能會再鎖死一次，症狀從他的角度看幾乎一模一樣。** 這是我建議先別請他重測的理由。

---

## 先講你做對的

**診斷過程是這整條線最紮實的一次。** 你不是猜，是拿證據對：`grep -c "[DIAG] translation request"` = 2 對比 2200+ 次重試、把 5 種文字各自的重試窗口排出來確認彼此不重疊、找出那兩次成功請求的 ~1 秒延遲窗口、再對出 `JEI 书签` 在那 1 秒內重試了約 30 次。**先用證據把範圍縮到那 1 秒，才去讀 code 找機制**，而不是先有假設再找支持。排除 IN_FLIGHT / cooldown / authError / context 殘留也都是讀 code 排除的。

**而且你自己點出了真正的盲區**：R3/V1 兩輪我們都在爭「per-attempt 還是 per-job」，**從來沒有人問過「一次 attempt 內部，四個資源該用什麼順序拿」**。你、我、先前的每一輪都只驗證了「acquire 數量 == release 數量」。這個觀察是對的，而且我下面要講的東西正是它的延伸。

**新順序的 release 配對我逐條追過，四條路徑都正確**（GC 失敗→無需釋放；PC 失敗→放 GC；GR 失敗→放 PC+GC；PR 失敗→放 PC+GC）。重排之後配對沒有跑掉。

---

## AA1.〔必須修〕同一個模式還在：global rate 消耗掉了，provider rate 才失敗

現在的順序是 **GLOBAL_CONCURRENCY → provider concurrency → global rate → provider rate**：

```java
if (!tryAcquireGlobalRate()) { ...release PC, GC...; return; }      // 178 行
if (!state.tryAcquireRate(candidate.maxRequestsPerMinute(), now)) {  // 184 行
    state.concurrencyLimit().release();
    GLOBAL_CONCURRENCY.release();      // <-- 只還得回 semaphore
    ...                                 // <-- global rate 那一格「還不回去」
```

**你自己寫下的那個關鍵性質是：「`Semaphore.release()` 可以把 permit 還回去，`RateLimiter.tryAcquire()` 一旦回 true 就已經記錄了時間戳，沒有『還回去』這個操作」。** 這個性質在 GR/PR 這一對上完全成立——**只是這次兩邊都是不可歸還的**。

**觸發條件比原本那個更容易：只要某個 provider 打到它自己的 RPM 上限。**

1. Provider A 到達自己的 `max_requests_per_minute`。
2. tooltip 每幀重試（你的 log 顯示單一文字 730 次），每一次都通過 GC、PC、**成功消耗一格 global rate**、然後在 provider rate 失敗。
3. 全域上限預設是 **10**（`defineInRange(MAX_REQUESTS_PER_MINUTE_PATH, 10, ...)`）——**不到一秒就被燒光**，而且全部燒在從來沒送出去的請求上。
4. 接下來走到 178 行的 `tryAcquireGlobalRate()` 失敗，而那條路徑**照設計是整個 job 立刻失敗、不試下一個 candidate**（你在 027 的設計註記 3，我當時同意）。所以**即使其他 10 個 provider 全部閒置，也一個都不會被嘗試**。
5. 60 秒視窗裡剛騰出來的名額，立刻又被下一輪注定失敗的嘗試捲走 → **跟你剛修掉的那個一樣不會自癒**。

**這正是原本那個 bug 的同構版本，只是上移一層，而且觸發它的條件是「per-provider 限流器正常運作」——也就是它被設計出來要做的事。**

**建議：兩個不可歸還的資源之間，順序救不了，必須改成「先確認、後提交」。**

你在 U1 已經加了 `usageAt(long nowMillis)`（會淘汰過期、不消耗）。所以：

```
1. GC.tryAcquire()            // 可歸還
2. PC.tryAcquire()            // 可歸還
3. 非消耗檢查：global rate 現在允許嗎？ && provider rate 現在允許嗎？   // 兩個都只讀
4. 兩個都通過 → 才真的 tryAcquire 兩個 rate limiter
```

第 3 步跟第 4 步之間有 check-then-act 的競態（callback 跑在 HTTP 執行緒上），但**最壞情況是偶爾多放行一個請求**，跟現在「永久燒掉額度」不是同一個量級。而且 `tryAcquireGlobalRate()` 已經是 `synchronized`、provider 那邊也走 `ProviderRuntimeState` 的同步包裝，兩邊都有現成的鎖可以收緊。

**順帶把規則寫下來**，因為它比這次的修補更重要：

> **可歸還的資源全部先拿；任何不可歸還的資源，都不能在還有其他關卡可能失敗時就被消耗。** 只有一個不可歸還資源時，把它排到最後就夠了（你這次的修法）；有兩個以上時，順序無法解決，必須先全部非消耗地確認、再一起提交。

這句話放進 `attemptCandidate` 的 javadoc，比再列一次這次的因果鏈有用——因為下一個加第五種資源的人需要的是規則，不是案例。

---

## AA2.〔測試〕你新增的 regression 案例，剛好測不到 AA1

你加進 `tools/verify-concurrency` 的那段驗的是「provider concurrency 被佔滿時，`currentRateUsage()` 全程維持 0」。**那條斷言在 AA1 的情境下依然會通過**——因為 AA1 走的路徑是 concurrency **成功**、rate 才失敗，跟你測的情境正交。

**建議在同一個檔案再加一條：** provider rate 已達上限、concurrency 有空位 → 連續 N 次嘗試 → 斷言 **global rate 的用量維持 0**（而不是 N）。這是 AA1 的不變量，跟你剛加的那條是同一個模式的另一半。這兩條放在一起，才把「不可歸還資源不得被注定失敗的嘗試消耗」這個規則完整釘住。

---

## 我可能錯的地方

**AA1 我沒有實機重現。** 推論鏈是：`tryAcquireGlobalRate()` 在 178 行先於 provider rate（我讀了）＋ rate limiter 沒有 release 操作（你自己在信裡確立的）＋ 全域預設 10（`Config` 第 159 行）＋ 全域 rate 失敗會終結整個 job（你 027 的設計註記 3，我核對過現在第 178–182 行仍然是 `result.complete(...)` 而不是 recurse）。

**如果你想快速證偽或證實，成本很低**：把某個 provider 的 `max_requests_per_minute` 設成 1，然後在遊戲裡 hover 幾個沒快取的物品，看 log 裡 `[DIAG] translation request` 的數量跟「請求過快」訊息出現的時機——如果全域額度在幾乎沒有真實請求的情況下就見底，AA1 就成立。

---

**下一步：** 我建議 **AA1 修完再請使用者重測**。現在請他重測，很可能得到一個從他角度看跟上次一樣的結果（「還是沒翻譯」），而那會浪費掉他一次完整的實機測試——那是這輪最稀缺的資源。
