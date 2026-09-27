# Translator 整合完成——Router 從死碼變成真的有呼叫點

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-028.md

## 這批做了什麼

`Translator.java` 現在完全改成呼叫 `TranslationRouter.translate(job)`：

- `CacheKey` 私有 record 拿掉，改用你之前建議、我上一批就已經建好的共用
  `net.github.dctime.libs.TranslationCacheKey`，`IN_FLIGHT` 從 `Set<String>` 改成
  `Set<TranslationCacheKey>`——job identity 在 `requestTranslateToTraditionalChinese` 一開頭 resolve
  一次（`TranslationCacheKey jobKey = keyFor(fixedText)`），之後 `IN_FLIGHT.add/remove` 跟最後的
  `translationCache.put` 全部重用同一個 `jobKey`,不會中途重新 resolve。
- `RETRY_AFTER`/`RETRY_ATTEMPTS`/`CONCURRENCY_LIMIT`/`REQUEST_RATE_LIMITER`/`CLIENT` 全部刪掉,不是
  改名——這些狀態現在活在 `ProviderRuntimeState`（per-provider）跟 `TranslationRouter`（global safety
  cap）裡。
- 舊的「vision-capability gate」（判斷單一 provider 支不支援圖片、screenshot 沒圖片就擋掉）整段刪掉,
  改成把 `VisionRequirement`（screenshot→REQUIRED,item icon→OPTIONAL,其餘→NONE）放進
  `TranslationJob`,由 Router 對每個 candidate 各自判斷。
- 舊的 `handleHttpResponse`/`handleHttpError`/`scheduleRetryBackoff`/`handleConnectionError` 全部刪
  掉,換成一個新的 `handleTranslationFailure(ProviderFailureType, boolean isScreenShot)`,對應你在
  V2 定的那三分法：`null` 沉默、`NO_ELIGIBLE_PROVIDER` 顯示新訊息(告訴玩家去 Manage Providers 檢查)、
  `UNSUPPORTED_CAPABILITY` 沿用舊的 `vision_unsupported` 在地化訊息、其餘沿用舊的雙語文字組合
  （AUTH→檢查 API Key、RATE_LIMIT→RPM 超過、TIMEOUT/CONNECTION→檢查網路、其餘→通用失敗訊息帶
  `ProviderFailureType` 名稱方便診斷）。
- 舊的 `[DIAG]` request/response log 從 `Translator`（單一 provider 視角）搬進 `TranslationRouter`
  （per-attempt 視角,現在會標明是哪個 provider）,`Translator` 自己剩一行 `[DIAG]` 記
  `provider_mode`/目標語言/文字內容,不再記單一 `endpoint`（因為現在不再只有一個）。
- `Config.MAX_REQUESTS_PER_MINUTE` 的註解照 plan 更新,講清楚它現在是疊加在每個 provider 各自
  RPM 之上的全域安全上限,不是唯一的節流器了。

## 順便修的東西（不是你提的,是我自己編譯+跑 verify 時發現的）

`tools/verify-concurrency` 整個是舊架構的複製品測試——它的「per-key in-flight」/「global
concurrency cap」兩段是重新實作 Translator 舊有的單一 `Semaphore(4)` + 文字鍵 `IN_FLIGHT`,而那個
設計已經被這批完全刪掉了。它照樣會 PASS（因為是獨立複製品,不是真的呼叫 Translator）,但如果不修,
它的檔頭註解跟內容都會變成在講一個已經不存在的架構,而且沒人會發現,因為它綠燈。

修法：RetryPolicy 那段（真的呼叫 production code）保留不動;in-flight 那段改成真的用
`net.github.dctime.libs.TranslationCacheKey`（真的 class,不是重新發明字串鍵）,新增一條斷言驗證
「同樣文字、不同語言」不會互相擋（這正是 R2 修的那個洞）;concurrency 那段改成讀真正的
`TranslationRouter.GLOBAL_CONCURRENCY_LIMIT`（我把它從 private 改成 public,理由寫在它自己的註解
裡）跟 `ProviderRuntimeState.DEFAULT_PROVIDER_CONCURRENCY`,不再獨立寫死一個可能跟正式碼漂移的數字。
檔頭註解也整段重寫,誠實揭露「還是驗不到 `TranslationRouter.attemptCandidate` 真正的 acquire/release
順序」這個限制的理由（`ProviderPool.all()` 需要真正的 NeoForge classpath）。

## 現在的狀態

`./gradlew build` 乾淨、17 個 `tools/verify-*` 全過。**這是這輪第一次 Router 真的會被遊戲呼叫到**——
下次啟動遊戲,每一次翻譯請求都會走 `TranslationRouter.translate()`。但因為 `Config.PROVIDER_MODE`
預設 `AUTOMATIC`、目前沒有 migration 邏輯把既有玩家設回 `SINGLE`,**如果現在就上遊戲測試,既有玩家會
直接落地在 AUTOMATIC 模式,行為會跟舊版不一樣**——這正是為什麼 migration 是下一步,不能跳過。還沒有
做任何真正開遊戲的測試。

## 下一步

Legacy migration 本體（`ProviderMigrationMarker` 接上 `FMLClientSetupEvent`）跟
`PendingTranslatorConfig` 的 pending-state 擴充——這兩個做完之後,既有玩家的行為才會回到跟你在 S2
討論時談的「SINGLE + 舊 provider」一致,到時候才安全開遊戲做第一次真正的手動測試。
