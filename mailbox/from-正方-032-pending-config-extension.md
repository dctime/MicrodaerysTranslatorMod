# PendingTranslatorConfig 擴充送審

**From:** 正方
**To:** 反方
**時間:** 2026-09-02

## 做了什麼

`PendingTranslatorConfig` 加了 `providerMode`（頂層欄位,像 `endpoint` 一樣,因為它是 Simple 畫面
自己的 CycleButton,不屬於任何單一 provider)。`ProviderPendingState`（每個 provider 各自一份的
scratch state)加了 `enabled`/`priority`/`maxRequestsPerMinute` 三個欄位,`freshStateFor()` 從
`Config.PROVIDER_KEYS`/`Config.CUSTOM_PROVIDER_*` 讀進來。

**故意沒有**照 `apiKey`/`modelSelection` 那種「目前 active 的 provider 才有一份頂層欄位、換
provider 要呼叫 `onEndpointChanged` 同步」的模式。理由：Manage Providers/Provider Detail 是對**明
確指定**的某個 provider 操作,不是對「目前 active 的那個」——所以我加的是四個吃 `Config.EndPoint`
參數的 getter/setter（`isProviderEnabled(ep)`/`setProviderEnabled(ep, ...)` 等),直接讀寫
`perProvider` map 裡對應那個 provider 的 entry,不需要 sync-into-map/load-from-map 那一套。

`saveToConfig()` 對應寫回 `Config.PROVIDER_MODE` 跟每個 provider 的三個新欄位（含 Custom)。

## 行為改變（明確揭露,不是順手改的)

`translationRelevantSettingsChanged()` 從「endpoint/model/prompt/promptScreenshot 任一個變了就觸
發清快取確認」收窄成「只看 prompt/promptScreenshot」。這是 spec 明確要求的行為改變,理由寫在新
javadoc 裡：AUTOMATIC/ROUND_ROBIN 模式下,同一段文字本來就會合法地在不同時間被不同 provider 服
務——如果手動切 provider 還跳出「要不要清快取」,語意上就跟這個新常態矛盾了。快取本身的 key 沒變
（還是 `(language, text)`,沒有 provider 維度),所以這個收窄不會讓快取變得不正確,只是不再為了
「玩家自己按了一下 Service 下拉選單」這件事去問一次多餘的問題。

`originalEndpoint`/`originalModel` 這兩個欄位因此變成真的死碼,整個刪掉了,沒有留著改名假裝還有
用。

## 驗證

`./gradlew build` 乾淨、17 個 `tools/verify-*` 全過（這批沒有新增可離線測的邏輯,主要是資料結構擴
充跟既有 pattern 的延伸,沒有寫新 verify tool)。沒有開遊戲測——GUI 還沒接上這些新欄位,現在整批
Manage Providers/Provider Detail 對應的 GUI 還不存在,這些新增的 getter/setter 目前沒有任何呼叫
點,是死碼但可編譯。

## 下一步

開始蓋 GUI：`TranslatorConfigScreen`（Simple)簡化成只留 Provider Mode CycleButton +
`[Manage Providers]` 按鈕、新的 `ManageProvidersScreen`、新的 `ProviderDetailScreen`。這批會比較
大,寫完整批送審。
