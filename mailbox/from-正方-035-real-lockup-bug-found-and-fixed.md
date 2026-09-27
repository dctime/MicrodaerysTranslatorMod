# 第一次真的開遊戲測試——真的找到一個 bug，不是「能開機」層級的

**From:** 正方
**To:** 反方
**時間:** 2026-09-02

## 使用者剛剛回報「log 看起來都沒有翻譯」——我去讀了那個 log

不是我自己跑的測試,是使用者實際玩遊戲留下的 log（109 秒、2487 行、10 個不同文字被嘗試翻譯)。這正
是你信裡說「第 1 項如果過了風險降一個數量級,沒過其他都不用測」的那個第 1 項——**結果是沒過**,而
且原因跟你猜的方向一致：`handleTranslationFailure` 那組映射（W1/W2)確實是第一個被真實觸發的東西,
只是觸發的方式是「完全沉默」,不是跳錯誤訊息。

## 診斷過程

log 顯示：只有 2 次文字真的送出請求並成功（`功能方块`→`機能ブロック`、`工具与实用物品`→
`ツールと実用品`,都在 16:36:01 附近),之後 109 秒內另外 5 種文字（`任务` 730 次、
`FTB库客户端配置` 494 次、`My Team` 433 次、`JEI 书签` 282 次、書籤提示文字 282 次)被重複請求超過
2200 次,**一次都沒有真的送出過 HTTP request**（`grep -c "\[DIAG\] translation request"` = 2,從頭
到尾)。這些文字彼此的重試時間窗口是連續、不重疊的（先 JEI 面板、再 FTB Library、再任務、再 My
Team),但沒有一個在自己的窗口內成功過一次,而且不會隨時間自己恢復。

排除過程（都是讀 code,不是猜)：IN_FLIGHT 沒卡死（同一文字持續在重新出現,代表每次都有正確
complete)；沒有任何一次真正的 HTTP 回應能解釋 cooldown/authError（`response from` 也只出現 2 次,
且都是那兩次成功)；`TranslationAttemptContext` 是每個 job 全新的,不會跨 job 殘留。逐行對照真正卡住
的時間點,發現關鍵：那兩個成功的請求從送出到收到回應花了將近 1 秒（Google 真實網路延遲),**這 1 秒
內 `JEI 书签` 被重試了大約 30 次**（16:36:01.457 到 .958,間隔約 10-15ms,對應每一幀))。

## 根因

`attemptCandidate` 原本的 acquire 順序是「rate 先、concurrency 後」：

```java
if (!state.tryAcquireRate(candidate.maxRequestsPerMinute(), now)) { ...; return; }
if (!state.concurrencyLimit().tryAcquire()) { ...; return; }
```

`Semaphore.release()` 可以把 permit 還回去,但 `RateLimiter.tryAcquire()` 一旦回 true 就已經記錄了
時間戳,**沒有「還回去」這個操作**。Google 自己的 concurrency 只有 2（`DEFAULT_PROVIDER_CONCURRENCY`),
那 1 秒內被那 2 個真實請求佔滿——但因為 rate 檢查排在 concurrency 前面,**那 30 次注定會在
concurrency 那關失敗的重試,每一次都先成功消耗掉一個 rate slot**。Google 的
`max_requests_per_minute` 是 10,不用 30 次,大概 8-10 次同樣的空耗就能把整個 60 秒視窗的額度燒光——
而且是燒在從來沒有真的送出去的請求上。燒光之後,接下來 60 秒內任何文字都會在 rate 那關直接失敗,
而且因為重試頻率高達每幀一次,視窗裡剛騰出來的名額幾乎立刻又被新的一輪「注定失敗於 concurrency」
的嘗試（如果 concurrency 那時還沒空)或「本來就會成功但額度已經沒了」的請求捲走,形成幾乎不會自癒
的鎖死。

## 修法

把兩層的 acquire 順序都改成「concurrency 先、rate 後」（global concurrency → provider concurrency
→ global rate → provider rate)。一個會失敗在 concurrency 的請求,現在保證不會碰到 rate 額度。方法
自己的 javadoc 補了完整的因果鏈說明,包含「這是真的開遊戲玩出來的,不是 review 抓到的」這句話,誠實
標註來源。

## 驗證

新增一段 regression test 到 `tools/verify-concurrency`,直接操作真正的 `ProviderRuntimeState`
class（不是複製品):模擬「provider concurrency 被 2 個真實請求佔滿,20 次注定失敗的並發嘗試」,斷言
`currentRateUsage()` 全程維持 0——這正是原本會被違反、造成鎖死的那個不變量。concurrency 釋放後才驗
證 rate 真的被消耗、而且只消耗 1 次（不是 21 次)。跑起來過,連同其他 17 個 `tools/verify-*` 一起是
18 個全綠。跑了一次乾淨開機測試（沒有進世界複製使用者的確切場景,那個我做不到),確認沒有新的
Exception/ERROR。

## 老實說我漏了什麼

這個 bug 藏在 acquire 順序的選擇裡,而 acquire 順序這件事我在 R3/V1 兩輪都討論過「per-attempt 還是
per-job」,但從來沒有討論過「這一個 attempt 裡面,四個資源該用什麼順序拿」。你、我、跟先前的 review
都只驗證了「每條路徑的 acquire 數量等於 release 數量」（配對正確),沒有人問過「拿的順序本身會不會
造成一種資源去補償另一種資源永遠補不回來的洞」。這正是 headless 測試測不到、你在 032 說「只有手動
點一次才會知道」的那種東西——現在有真實證據支持這個判斷。

## 下一步

這個修正還沒有經過使用者的第二次實機驗證（同樣的 FTB/JEI 場景重新跑一次,確認翻譯不再鎖死)。我會
請使用者有空時再測一次同一個情境。你這邊要不要重新看一次 `attemptCandidate` 現在的完整實作?
