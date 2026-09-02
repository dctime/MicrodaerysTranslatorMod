# V1 / V2 已處理

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-027.md

## V1 — 照你的建議，把 `sendAsync` 包進同一個 try

沒有嘗試論證「這裡不會同步丟」——你在「我可能錯的地方」提的兩種立場我選「不信任」那條，跟上一輪
`computeGap` 的判斷一致：有 catch 就代表不信任，保護範圍就該蓋滿，不要蓋一半。現在 `buildTranslationRequest`
跟 `CLIENT.sendAsync(...).whenComplete(...)` 這整條鏈都在同一個 try 裡，catch 走跟原本
`buildTranslationRequest` 完全一樣的清理路徑（release 兩個 permit、decrementInFlight、markAttempted、
`recordFailure(MALFORMED_RESPONSE)`、recurse）。因為 `.whenComplete(...)` 是同一個運算式的一部分，
`sendAsync` 同步丟例外時它從來沒被註冊過，不會有「先註冊了 callback、又進了 catch」的雙重清理風險。

## V2 — 加了 `ProviderFailureType.NO_ELIGIBLE_PROVIDER`，三條路徑分開了

你列的三條路徑裡，第三條（`hardFilter` 後整個 raw pool 沒有任何一個 enabled+有憑證的 candidate，且
非 SINGLE 模式）現在回傳新加的 `NO_ELIGIBLE_PROVIDER`。前兩條（全域耗盡、reachedMaxAttempts 但全是
budget skip）維持回 `context.lastFailureType()`，也就是维持 null——這兩條本來就該沉默,跟舊的
「dropped, not queued」語意一致，不需要新型別。

SINGLE 模式刻意排除在 `NO_ELIGIBLE_PROVIDER` 判斷之外：`hardFilter` 對 SINGLE 從來不檢查
`hasCredentials()`，所以 SINGLE 模式的「沒 key」從來就不是走空池這條路——它會真的送出去，讓真實的
401 打回來，走的是正常的 attempt/response 路徑，不是這裡。

這個型別現在只是定義好、有正確的分類邏輯，**還沒有接到任何會顯示給玩家的訊息**——那是下一批
`Translator` 整合時的事(把 `TranslationResult.finalFailure()` 對應到聊天訊息)。這批只確保「型別上已
經回答了 null 是什麼意思」,不會讓整合那批被迫用 null 去猜。

## 沒做的部分

沒有另外寫 headless test 驗證 `inferEmptyPoolFailure` 這三條分支——它是 private，而且
`TranslationRouter.attemptNext` 整條路徑都要吃到 `ProviderPool.all()`,而 `ProviderPool` 走
`ProviderConfigResolver.resolve()`,那個方法（跟 `verify-provider-adapters` 自己的免責聲明一樣）需要
真正的 NeoForge classpath 才能跑,不是純 headless 可測的。這條邏輯目前只靠讀程式碼審查,沒有自動化測
試撐著——如果你覺得這值得為了測試性再拆一層（例如把三條判斷邏輯抽成一個吃 plain boolean 參數的純函
數,不吃 `List<ProviderCandidate>`/`Config`），我可以做,但目前沒有主動做,想先聽你的判斷值不值得。

`./gradlew build` 乾淨、17 個 `tools/verify-*` 全過（不需要新增,這兩個修正沒有新增可離線測的邏輯面,
只是重新整理既有邏輯的保護範圍跟回傳值分類）。

## 下一步

開始 `Translator.java` 整合——這是這輪目前為止唯一會讓 Router 從死碼變成有呼叫點的一批,做完之後才
會有第一次「這件事真的在跑」的訊號(仍然只是編譯/離線驗證,還沒有真正開遊戲測)。
