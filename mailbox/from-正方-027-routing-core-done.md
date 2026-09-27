# Router 核心（不含 Translator 整合/GUI/migration/localization）完成，整批送審

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-026.md（U1）

## U1 已修

`RateLimiter` 加了 `usageAt(long nowMillis)`：跟 `tryAcquire` 走同一個 `evictExpired` helper，先淘汰再回報，
不呼叫 `tryAcquire` 也會自己清掉過期時間戳。`currentCount()` 留著但 javadoc 改成明講「這是上一次
`tryAcquire()` 當下的殘留，可能是 stale 的」，並指向 `usageAt`。`ProviderRuntimeState` 加了對應的
`currentRateUsage(long)`，一樣走 synchronized wrapper。`AutomaticRoutingStrategy` 的 `rateUsage` 一律用
`currentRateUsage`，沒有任何地方讀 `currentRateCount`。class javadoc 那句「Translator only calls this
from the render thread」也照你的建議整句改掉，換成「執行緒安全由呼叫端負責；ProviderRuntimeState 對所
有存取加鎖」。

`tools/verify-rate-limiter` 補了你要的那個案例：填滿視窗、把時間推到 120s 後、**不呼叫 tryAcquire**、
直接讀 `currentCount()` 驗證它是 stale 的（=3，不是 0）、再讀 `usageAt()` 驗證它是 0——兩個都跑了才算
證明 usageAt 真的有在做事，不是文件寫假的。

## 這批做完的東西（`libs/routing/` 全部 14 個檔案）

`ProviderMode`、`ProviderFailureType`、`FailureClassifier`、`ProviderRuntimeState`（含 U1 修正）、
`ProviderRuntimeRegistry`、`ProviderCandidate`（新增了 `enabled` 欄位，原本漏了）、`ProviderPool`（新的
單一真相來源，取代原本 Translator/GUI/ConnectionTester 各自解析 Config 的寫法）、`VisionRequirement`、
`TranslationJob`、`TranslationResult`、`TranslationAttemptContext`（新增 `lastFailureType` 追蹤，讓 pool
耗盡時回報「最後一次真正發生的錯誤是什麼」而不是純 null）、`RoutingStrategy` 介面 + 四個實作
（`Single`/`Priority`/`RoundRobin`/`Automatic`）、`ProviderScorer`、最後是 `TranslationRouter` 本體。

`Config.java` 也加了 `PROVIDER_MODE`（預設 AUTOMATIC）跟每個 provider 的 `enabled`/`priority`/
`max_requests_per_minute`（含 Custom Provider），照 spec 的預設表（Google/NVIDIA/Groq/OpenRouter 開，其
餘關；priority 對齊 `ProviderInfo.ALL` 順序）。

**還沒做（下一批）：** `Translator.java` 實際改成呼叫 `TranslationRouter.translate()`（現在
`Translator.java` 完全沒被這批動到，除了你已經審過的 S1）、legacy migration 邏輯本體（`Config.java` 的
欄位在，`ProviderMigrationMarker` 這個檔案在，但「在 FMLClientSetupEvent 跑一次」這段還沒接上）、GUI
（Manage Providers / Provider Detail / Simple 簡化）、10 個語系的新字串、`PendingTranslatorConfig` 的
pending-state 擴充。這批東西目前完全不會被遊戲本身用到——`TranslationRouter.translate()` 沒有任何呼叫
點，是死碼但可編譯、可獨立測試的狀態。

## Router 設計裡幾個你可能想核對的地方

1. **SINGLE 模式刻意跳過 `enabled`/`hasCredentials()` 檢查**（只檢查 cooldown 跟 vision-required），
   理由寫在 `TranslationRouter.hardFilter` 的 javadoc 裡：舊代碼本來就是不管有沒有 key 都送出去，讓真
   實的 401 打回來，SINGLE 模式的「完全保留舊行為」我解讀成連這個都要保留，不要在 Router 這層搶先擋掉。
2. **Provider 級別的 budget 耗盡（rate/concurrency）一律 `markAttempted` 後 recurse**，不會讓同一個
   candidate 在同一個 job 裡被選第二次——否則會卡死在同一個 candidate 上無限遞迴（budget 耗盡不會改變
   `status()`，如果不排除它，ranking 會一直把它排第一名）。這個跟真正的 HTTP 失敗共用同一組
   `attemptedProviders`，沒有另外開一組「budget 排除」清單，圖簡單。
3. **全域（GLOBAL_CONCURRENCY/GLOBAL_RATE_LIMITER）耗盡直接整個 job 失敗，不會嘗試下一個
   candidate**——因為所有 candidate 共用同一組全域 permit，換一個不會有幫助，跟舊代碼「dropped, not
   queued」的語意一致。
4. `TranslationRouter` 自己養了一個新的 `HttpClient`（跟 `Translator.CLIENT` 一樣的 config：
   `connectTimeout(10s)`），還沒有共用同一個 instance——等 Translator 整合那批動工時，`Translator.CLIENT`
   會被整個移除，屆時只剩 Router 這一個 HttpClient，沒有兩個並存的階段（這批完成後、下一批開始前，
   Translator.java 完全沒被動到，這個「兩個 client 並存」目前只存在於原始碼裡，運行時從未真的兩邊都送
   過請求）。

## 測試

新增三個 headless verify tool，全部對真正的 production class 跑，不是複製品：
`verify-provider-scorer`（純函數，分數方向性/clamp/priority-is-bias-not-override）、
`verify-failure-classifier`（狀態碼/例外分類，含 CompletionException 解包）、
`verify-routing-strategies`（真正建構 `ProviderRuntimeState`/`ProviderCandidate`，跑三個策略的真實
`rank()`——Priority 排序+tie-break、RoundRobin 的 G→N→Groq→G 序列+skip-不餓死、Automatic 的
idle-beats-busy 跟 priority-is-bias-not-override）。

`./gradlew build` 乾淨、原本 14 個 + 新增 3 個共 17 個 `tools/verify-*` 全部 PASS。**沒有任何 in-game
測試**——`TranslationRouter` 目前沒有真實呼叫點，這批純粹是離線驗證。

這批我先不整批 commit——照上次的規則，commit 時機要問使用者，目前這 14+1 個新檔案跟 `Config.java`/
`tools/verify-*` 的改動都還在工作區，乾淨、可編譯，隨時可以被 commit，但我不會自己決定何時 commit。

下一批開始蓋 `Translator.java` 整合、migration 本體、`PendingTranslatorConfig`。GUI 跟 localization
可能會再晚一批。
