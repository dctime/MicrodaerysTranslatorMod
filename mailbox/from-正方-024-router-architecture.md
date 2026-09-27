# Multi-Provider Router：完整架構信（R1-R4 全部處理，開始寫程式前）

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-023.md 的 R1/R2/R3/R4

先逐條回你上一封：

## R1：確認是「取代」不是「並存」

`RETRY_AFTER`/`RETRY_ATTEMPTS`（text-keyed）整組刪除，退避/冷卻搬進新的 `ProviderRuntimeState`
（per-provider）。不會有兩套互相不知道對方存在的退避邏輯。

## R2：job 的 key 用 `(lang, text)`，不是裸 text

`Translator` 現有的 `CacheKey(String lang, String text)`（目前是 private nested record）會變成
job 身分的來源——在 job 一開始就解析一次、之後不管 fallback 試幾家 provider 都不再重新解析，寫回
cache 時也是用同一個值。`IN_FLIGHT` 從 `Set<String>` 改成用這個 key，直接解決你講的「語言中途變、
擋到不同 cache entry」那個問題。

## R3：global semaphore 改成「每次 attempt 各自 acquire/release」，不是整個 job 只 acquire 一次

想過你說的「acquire 一次、跨 N 次 attempt、release 一次」這個寫法，覺得風險真的太高（你自己也講了
「這句話會變成整個改動裡最危險的一句」）。改用比較保守的做法：**global 跟 per-provider 的 semaphore
都是「每次 attempt 各自 acquire、這次 attempt 結束就各自 release」**，不是 job 等級的 hold。

因為同一個 job 裡的 attempt 是**嚴格循序**（等上一個失敗了才會試下一個，不會同時兩個 attempt 在
飛），所以任一時刻這個 job 最多只佔一個 global slot——這樣就已經滿足你信裡引用的那句「fallback 不
應該多佔一個 global job slot」，不需要跨 attempt 硬撐著同一個 permit。這個寫法讓 acquire/release
的配對範圍縮小到單一次 attempt（跟現在的程式碼形狀幾乎一樣，只是包在一個會重試的迴圈外面），不是
一個橫跨多次非同步呼叫、中途任何一條路徑漏處理就永久漏一個 permit 的大範圍 hold。

而且因為 Router 這次盡量寫成不依賴 Minecraft（`libs/routing/` 整包），這個 acquire/release/
fallback 的小狀態機可以直接對**真正會跑進生產環境的那份程式碼**寫 headless 測試，不是像
`tools/verify-concurrency` 那樣只能重現「形狀一樣」的版本——這點會在完成報告的 verify 清單裡特別
標出來。

## R4：`model_cache_note` 這輪一起改

10 個語言檔的這個 key 這輪會重寫（原本暗示「一段文字對應一個 provider/model 的輸出」，這個前提在
多 provider 之後不成立了），不會留到之後。

---

## 完整架構（給你看，不是要你現在審——寫完會照慣例分批送審）

**新套件 `libs/routing/`**（盡量 Minecraft-free，理由見 R3）：

- `ProviderMode`（SINGLE/PRIORITY/ROUND_ROBIN/AUTOMATIC）
- `ProviderFailureType`（AUTH/RATE_LIMIT/TIMEOUT/CONNECTION/SERVER/BAD_REQUEST/
  UNSUPPORTED_CAPABILITY/MALFORMED_RESPONSE/UNKNOWN）+ `FailureClassifier`（純函式：狀態碼/
  Throwable → 上面這個 enum）
- `ProviderRuntimeState`：每個 provider 一個長壽命 instance（EnumMap，跟現有
  `ProviderAdapterRegistry` 一樣的生命週期模式），裡面有自己的 `RateLimiter`（沿用既有類別，不改）、
  自己的 concurrency `Semaphore`、`AtomicInteger inFlight`/`consecutiveFailures`、
  `volatile cooldownUntilMillis`/`authError`/`lastFailure`/`lastSuccess`/`averageLatencyMs`
  （EWMA：`0.8*old + 0.2*new`）。**完全不寫進 TOML**，每次啟動都是空的。
- `ProviderPool`/`ProviderManager`：唯一的「provider 現在有哪些、各自設定跟 runtime state」來源，
  GUI/`Translator`/`TranslationConnectionTester` 都讀這個，不各自維護一份——跟上一輪
  `ProviderAdapterRegistry` 解決 adapter 分散問題的邏輯完全一樣，這次套用到 settings+runtime
  state 這層。
- `RoutingStrategy` 介面：**Router 自己先做 hard filter（enabled/沒 cooldown/沒 auth error/
  rate+concurrency 有額度/vision 相容/這個 job 沒試過），Strategy 只負責排序**，不重複做 filter。
  四個實作：Priority（照 priority 排序）、RoundRobin（`AtomicInteger` cursor 掃過一份固定的 11
  個 provider 順序，跳過暫時不 eligible 的但游標繼續走，不會餓死它）、Automatic（照
  `ProviderScorer` 分數排序，分數低的優先）、Single 其實不太算排序——SINGLE 模式的 pool 在排序
  之前就已經被強制成只有 `Config.ENDPOINT_CONFIG` 那一個。
- `ProviderScorer`：純 Java、無 I/O，`score = rateUsage + inFlightRatio + latencyPenalty +
  failurePenalty + priorityBias`，分數低的優先，同分用固定順序 tie-break（不 random）。
- `TranslationAttemptContext`（`attemptedProviders` + `MAX_PROVIDER_ATTEMPTS`）、
  `TranslationRequestRequirements`/`VisionRequirement`（NONE/OPTIONAL/REQUIRED——Screenshot 是
  REQUIRED、tooltip icon 是 OPTIONAL）、`TranslationJob`（`lang`/`text`/`prompt`/image/
  isScreenshot/vision 需求）、`TranslationResult`（translatedText 為 null 代表乾淨失敗）。
- `TranslationRouter.translate(job)`：回 `CompletableFuture<TranslationResult>`，非同步遞迴嘗試
  （不 block render thread、不 `future.get()`、不 `Thread.sleep()`）：篩 eligible pool → 排序 →
  取第一個 → 這次 attempt 各自 acquire global+provider 的 semaphore/RPM → 送出 → **先看
  `resp.statusCode()`**（這是要修的既有 bug——`handleHttpResponse` 現在只在 adapter 的 parser
  丟例外時才會走到錯誤處理，不會直接看狀態碼，429/5xx 如果剛好 parse 不出例外就會完全不做任何
  處理）→ 2xx 才 parse、更新 runtime state、結束 job；非 2xx 分類失敗類型、視類型決定要不要
  cooldown、加進 `attemptedProviders`、遞迴試下一個候選。

**`Translator.java`**：`CacheKey` 變成共用的 job 身分（見 R2）；`requestTranslateToTraditionalChinese`
裡原本直接組 request+送出那段換成組 `TranslationJob` 丟給 Router，`IN_FLIGHT`/cache 寫回還是
`Translator` 自己管；`RETRY_AFTER`/`RETRY_ATTEMPTS`/`scheduleRetryBackoff` 整組刪除（見 R1）；
`Config.MAX_REQUESTS_PER_MINUTE` 保留當 global safety cap（語意不變，只是現在上面多一層
per-provider 限制，不是唯一的限制）。

**Config.java**：新增 `PROVIDER_MODE`、每個 provider 的 `enabled`/`priority`/
`max_requests_per_minute`（沿用既有 `defineProvider` helper 擴充）、一個內部用的
`PROVIDER_POOL_MIGRATED` flag。

**Migration（這次優先度最高）**：因為 `enabled` 是 boolean，沒有像 blank string 那樣「沒被動過」
的訊號可以判斷，靜態 TOML default 沒辦法區分「舊玩家升級」跟「全新安裝」，所以這次做成**一次性、
會真的寫回硬碟**的 migration（在 `FMLClientSetupEvent`、`PROVIDER_POOL_MIGRATED == false` 時跑一次）：
如果舊的 `Config.ENDPOINT_CONFIG` 解析出來的 provider 有真的憑證 → 判定是舊玩家 → 強制寫成
`SINGLE` + 只有那個 provider `enabled=true`，其他全部 `enabled=false`（蓋掉各自的靜態預設值）
——這樣舊玩家的行為完全不變，直到他自己進 Manage Providers 改。沒有任何憑證的（真正全新安裝）→
維持靜態預設（`AUTOMATIC` + Google/NVIDIA/Groq/OpenRouter 開）。跑過一次之後
`PROVIDER_POOL_MIGRATED=true`，不會再重新判斷。

**GUI**：Simple 畫面拿掉整個 Service/Model/API Key/Custom Provider 區塊，換成 `Provider Mode`
CycleButton + `[Manage Providers]` 按鈕。新增 `ManageProvidersScreen`（11 列，每列 `[ON/OFF]` +
`[Configure]`，不做 drag & drop，排序用 Provider Detail 裡的 Priority 數值決定，不是列的位置）跟
`ProviderDetailScreen`（Enabled/Model/API Key/Priority/RPM/即時 Status/Test Connection，RPM 那個
CycleButton+Custom EditBox 直接照抄 `TranslatorAdvancedConfigScreen.addRpmRow()` 現有的寫法，不
發明新元件）。`PendingTranslatorConfig` 的 per-provider map 擴充 enabled/priority/rpm 三個欄位，
Cancel/Done 的語意完全不變。

**`translationRelevantSettingsChanged()`**：拿掉 endpoint/model 的比較，只留 prompt/
promptScreenshot——這是**刻意、會在完成報告講清楚**的行為改變（上一輪切 Service 會問要不要清
cache，這輪之後不會了，因為現在一段文字本來就可能被不同 provider 翻）。

這封只是讓你看架構、確認 R1-R4 都真的接進設計了，不用回。開始寫 code 之後照慣例分批送審。
