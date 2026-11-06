# AA1 已修——改成 check-then-commit,不是再排一次順序

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-034.md

你抓得對,而且抓得比我深——我只把「先修好的那個模式」重新套用一次,沒有意識到 global rate 跟
provider rate 是**兩個**不可歸還資源,排序在這種情況下本來就救不了。

## 修法：peek-then-commit

`ProviderRuntimeState` 加了 `hasRateBudget(limit, now)`——non-consuming peek,底層就是你之前要求
加的 `RateLimiter.usageAt()`,不消耗、只讀。`TranslationRouter` 對應加了
`hasGlobalRateBudget(now)`,同一個 pattern。

`attemptCandidate` 現在是：GC acquire → PC acquire →（peek global rate 有沒有額度 && peek
provider rate 有沒有額度,兩個都只讀,沒有任何一個在這步被消耗)→ 兩個都過才真的
`tryAcquireGlobalRate`/`state.tryAcquireRate` 去真正 commit。任何一個 peek 沒過,直接照對應的失敗
語意處理（global 沒額度→ 整個 job 終止;provider 沒額度→ mark attempted 換下一個 candidate),
兩個 rate limiter 都完全沒被碰過。

peek 到 commit 之間有你講的那個 race——同一執行緒、沒有 I/O,微秒等級,跟原本那個「跨越一整秒真實
網路延遲」的視窗不是同一個量級。最壞情況是多放行一個,不是永久燒光額度。

規則寫進 `attemptCandidate` 的 javadoc 了,用你的原話整理："可歸還的資源全部先拿;任何不可歸還的
資源,都不能在還有其他關卡可能失敗時就被消耗。只有一個不可歸還資源時,排到最後就夠了;有兩個以上
時,順序無法解決,必須先全部非消耗地確認、再一起提交。"標成 AA1/前一個 bug 兩輪合併的結論,不是只
講這次的因果鏈。

## AA2 測試

在 `tools/verify-concurrency` 加了鏡像案例：provider rate 填到上限、模擬一個獨立的
`RateLimiter`（跟 `GLOBAL_RATE_LIMITER` 同一個 production class,只是不同 instance,因為
`TranslationRouter` 自己那個 static field 呼叫要吃 `Config`,沒有真正的 NeoForge classpath 連不到)
代表 global,跑 15 次注定在 provider rate 失敗的嘗試,斷言 global 那個 limiter 全程用量維持
0——不是像你說的「原本那條斷言正交、測不到 AA1」,這條是專門為 AA1 寫的,跟原本那條合起來才把
「不可歸還資源不得被注定失敗的嘗試消耗」這個規則的兩個方向都釘住。

## 驗證

`./gradlew build` 乾淨、18 個 `tools/verify-*` 全過（含新的 AA1/AA2 案例)。跑了一次開機測試,
沒有新的 Exception/ERROR。**沒有做你建議的「把某個 provider RPM 設成 1 再實機驗證」那條**——
regression test 已經直接把不變量釘住,而且我沒有辦法精確複製使用者原本那個場景（多個 FTB/JEI
畫面同時渲染的節奏),與其做一個不夠精確的複現,我判斷把資源花在讓使用者真的重測一次更值得。

## 下一步

我認為兩個非入侵性 bug（原本那個 + AA1)都修完、都有 regression test 撐著,現在請使用者重跑同一個
FTB/JEI 場景比較合理。你如果還有第三個不可歸還資源的組合想不到的,現在講會比等他測完再講便宜。
