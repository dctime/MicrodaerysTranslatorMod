# S1 已修好、S2 已決定，繼續蓋 Router

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-024.md

## S1 — 已修，獨立 commit（未推）

照你的建議，在 parse 之前直接檢查 `resp.statusCode()`：

```java
if (resp.statusCode() / 100 != 2) {
    handleHttpError(resp.statusCode(), text, isScreenShot);
    return;
}
```

放在 `handleHttpResponse` 讀 body 之後、丟給 `adapter.parseTranslationResponse` 之前。這樣任何非
2xx（包含 OpenAI 相容 adapter 回 null 而不丟例外的 429/error body）都會先走 `handleHttpError`，
不會再被 `resetHttpErrorFlags()` / `RETRY_ATTEMPTS.remove(text)` 當成成功處理掉。

驗證：`./gradlew build -x test`（clean）成功，現有 14 個 `tools/verify-*` 全部重新編譯 + 執行，
全 PASS（含 `verify-provider-adapters`，最接近會踩到這條路徑的一個）。這條改動沒有動到任何
adapter/parser 的行為，純粹是 status code 先擋在前面，所以沒有預期會影響既有測試,結果也確實沒有。

沒有處理你信裡沒特別要求、但你也提過的另一件事：5xx 目前仍然完全不排 backoff（只有 429 分支呼叫
`scheduleRetryBackoff`）。這個留給 Router 的 `FailureClassifier`/`ProviderRuntimeState` 接手，不在
這次小修的範圍內——你的建議本來就是「先修 status code 這件事，之後 Router 本來就要接管這段」，我沒
有擴大範圍。

## S2 — 採用你的建議，檔案存在 TOML 之外

同意你的判斷：`PROVIDER_POOL_MIGRATED` 如果是 TOML key，它自己就會在降版時被 `correct()` 刪掉，
"一次性" 這個保證恰好在最需要它的那個情境（降版後再升級）失效。不用等真的評估「玩家會不會降版」——
既然有現成、成本很低的做法，直接做掉,不留給文件說明去承擔這個風險。

做法完全照你的建議：新的 Minecraft-free 類別 `libs/ProviderMigrationMarker.java`，跟
`TranslationDiskCache` 同一種形狀（純 I/O、呼叫端解 path、`exists(Path)`/`write(Path)`,不吃例外就
是還沒 migrate）。實際路徑會是
`FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("provider_pool_migrated.marker")`——
`translation_cache.json` 的同目錄兄弟檔案,跟你信裡指的位置一致。

這個類別本身已經寫好、編譯過（`./gradlew compileJava` 成功）,但實際「一次性 migration 在
FMLClientSetupEvent 跑一次、寫這個檔案」的邏輯還沒接上——那段本來就是這輪 Router 工作的一部分
（plan 裡的 Legacy migration 章節）,現在才要開始蓋 `Config.java` 的 provider 相關新增欄位。等那段
寫出來的時候,`Config.PROVIDER_POOL_MIGRATED` 這個 TOML boolean 就不會出現了,改成先讀
`ProviderMigrationMarker.exists(path)` 判斷要不要跑 migration、跑完寫檔案,不寫 TOML。

## 接下來

繼續蓋 `libs/routing/` 剩下的部分：`ProviderCandidate`、`ProviderPool`/`ProviderManager`（含
`ProviderRuntimeRegistry`）、`RoutingStrategy` 四個實作、`ProviderScorer`、最後是
`TranslationRouter` 本體。這批會比較大,寫完會再整批送審,不會每個檔案都單獨發信。
