# AB1 已修（(a)+(b) 都做),AB2 記錄不修

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-035.md

## 措辭先修

"never a leak" 改成講清楚：commit 順序下 semaphore 確實不會漏,但 global 已 commit、provider
commit 才輸掉 race 的那個罕見情況,rate 那一格是真的被燒掉一次——不是 permit leak,是 rate slot 的
真實代價,只是量級遠小於原本的 bug。javadoc 現在這樣寫。

## AB1(a) — `hasCredentials()` 改用 `BaseUrlUtil.isValid(...)`

CUSTOM 分支從 `!isBlank()` 改成 `BaseUrlUtil.isValid(settings.customBaseUrl())`——非空白但語法上不
合法的 base URL 現在會被 `hardFilter` 直接濾掉,連 semaphore 都碰不到。`isValid` 內部的 `normalize`
已經處理 null,不用額外判斷。

## AB1(b) — request 建構搬到兩個 rate commit 之前

新順序：GC acquire → PC acquire → peek 兩個 rate（都不消耗)→ **build request（會丟例外的關卡,現在
排在這裡)**→ commit 兩個 rate → `incrementInFlight` → DIAG log → `sendAsync`。build 失敗的 catch
現在只釋放兩個 semaphore（因為兩個 rate 這時都還沒被 commit,不需要釋放不存在的東西),
`releaseAcquiredPermits(state)` 保留給 `sendAsync` 那個 catch 用（那時候 `incrementInFlight` 已經
跑過,而且兩個 rate 已經 commit 完,語意不同,不能共用同一個 helper)。

`[DIAG] translation request:` 那行 log 也跟著搬到 commit 完成之後、`sendAsync` 之前——意思改成「這
個請求現在真的要送出去了」,不是「這個請求已經建好了(但可能後面 commit 才失敗)」。

javadoc 整段重寫,把三次同一個模式（原本那個 bug → AA1 → AB1)串成一條規則,不是分開三段案例。

## AB2 — 記錄,不修

同意你的判斷：AUTOMATIC 是預設模式,`ProviderScorer` 本來就會把忙碌的 provider 排到後面,budget
skip 消耗掉的 `attemptedProviders` 名額影響很小;PRIORITY 才會真的踩到「前 5 順位剛好都忙、後面
6 個閒置卻碰不到」這個停滯（不是鎖死,負載降下來會恢復)。這批不動,寫進完成報告——會客觀寫清楚它
的觸發條件（PRIORITY 模式 + 短時間大量不同文字同時要翻譯,剛好是使用者那個 FTB/JEI 場景會製造的情
境),不是隨手帶過。

## 驗證

`./gradlew build` 乾淨、18 個 `tools/verify-*` 全過（AA1/AA2 的兩個 regression test 在新順序下
還是照樣過,因為它們驗的是「不可歸還資源不該被注定失敗的嘗試消耗」這個不變量,不是特定的程式碼行
號)。跑了一次開機測試,沒有新的 Exception/ERROR。

## 下一步

我判斷三個同型 bug（原本那個 → AA1 → AB1)加上你這輪明講「這是我目前想得到的全部」,現在請使用者
重測同一個 FTB/JEI 場景是合理的時機。如果你還有第四個,現在講最便宜;如果沒有,我會請使用者重跑。
