# Q1（兩個常數)+ Q2（GUI 可觀察性)都修了

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-037.md

## Q1 — 三個常數都處理了

`LATENCY_FULL_PENALTY_MS` 5000 → **15000**——對照你指出的實測範圍（Groq 0.3-0.6s 到 NVIDIA
9.5-15s,50 倍跨度),現在 6s（OpenRouter)跟 15s（NVIDIA)不會再是同一個分數。

`STALENESS_MAX_DISCOUNT` 0.7 → **0.5**——你抓到的「巧合的相等」（1.0 打 7 折剛好等於中性值 0.3)
確認是常數各自選出來後才撞上的,不是刻意設計,已經改掉。現在 worst-case 全打折之後是 0.5,永遠比中
性值 0.3 差,「有資訊,永遠不該讓 provider 看起來比沒資訊更好」這條原則現在真的成立,不是巧合成
立。

`STALENESS_FULL_RECOVERY_MS` 維持 5 分鐘,你的那句話（「每個表現不佳的 provider,大約每 5 分鐘會
被重新試一次,那一次翻譯會慢」)原封不動搬進 `ProviderScorer` 的 javadoc,也會放進完成報告。

`priorityBias` 那句「a nudge, not an override」也照你的意思補了限定範圍——相對嚴重的懲罰是 nudge,
在一群本來就健康的 provider 之間其實是主導項,兩種情況都寫進 class javadoc,不再是一句無條件的話。

## Q2 — Provider Detail 的 Status 行現在會顯示這兩個新輸入

`hasLatencySample`/`lastAttemptMillis` 現在直接顯示在 Status 行後面：沒試過就顯示「（未試過)」,
試過就顯示「（平均 X.X 秒,上次嘗試於 N 秒前)」——跟你舉的例子「NVIDIA 平均 12.3 秒」一樣的形狀。
`tick()` 本來就每 tick 重算,這兩個數字會跟著 Status 一起即時更新。

有個小細節：平均延遲用 `String.format("%.1f", ...)`,特地指定 `Locale.ROOT`——不然某些 JVM 預設
locale（像德文)的小數點會印成逗號,把數字塞進翻譯句子裡卻用錯分隔符號,是這個 mod 已經在別處
（`BaseUrlUtil`/`ApiKeyUtil`)防過的同一種 locale-dependent 陷阱。

10 個語系都補了兩個新 key（`provider.status.detail.untried`/`provider.status.detail`),
`runData` + `verify-lang-placeholders` 都過。

## 驗證

`tools/verify-provider-scorer` 把所有 `5_000.0` 換成 `15_000.0`,新增一條直接對應你抓到那個「巧合
相等」的 regression test：`staleBadData > untried`（之前這個常數改法會讓兩者相等,現在斷言嚴格大
於)。`./gradlew build` 乾淨、18 個 `tools/verify-*` 全過。跑了一次開機測試,沒有新的
Exception/ERROR。

**沒做的**：沒有把「大約每 5 分鐘重探索一次」這件事做成使用者看得到的提示（例如聊天訊息)——你只要
求寫進完成報告,我沒有擴大範圍去加額外的通知機制。
