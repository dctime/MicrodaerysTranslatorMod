# 新一輪：Multi-Provider Router（開工通知，還在規劃階段）

**From:** 正方
**To:** 反方
**時間:** 2026-09-02

## 背景

使用者這輪要把目前「同一時間只 active 一個 provider」升級成「可以同時啟用多個 provider，由
Router 依 mode 自動分配翻譯 request」。四種 Provider Mode：SINGLE（完全保留現有行為）、
PRIORITY（固定順序 + 自動 fallback）、ROUND_ROBIN（在 eligible provider 間輪流）、AUTOMATIC
（deterministic score：rate usage / in-flight / latency / failure / priority bias，預設模式）。

這輪範圍很大，重點限制（都是使用者明講的）：

- **不是** parallel racing（不會同時送多家、比快慢）——一筆 request 一次只送一家，失敗才 fallback。
- 429/timeout/connection failure/5xx/provider unavailable 才 fallback；400 這種通常不是 transient
  的錯誤，最多每個 eligible provider 各試一次（`MAX_PROVIDER_ATTEMPTS`），不能無限迴圈。
- **Global cache / IN_FLIGHT 必須在 Router 之前**——同一段文字（tooltip/Jade/container
  pretranslate 同時觸發）只能建立一個 translation job，fallback 到下一個 provider 不能被誤判成
  新的 job、也不能讓其他呼叫點插進來重複發送。
- **Cache key 不變**（還是 `(lang, text)`，不含 provider/model）——這點在這次的 11-provider
  refactor 就已經定案，這輪只是要重新檢查 `translationRelevantSettingsChanged()`：Automatic
  模式下同一段文字本來就可能被不同 provider/model 翻，所以「provider/model 換了」不能再自動
  視為要問「要不要清快取」，只有 prompt/target language 這種真的會改變輸出語意的設定才算。
- 每個 provider 要有自己的 RateLimiter（rolling 60s）跟 concurrency 上限，但保留一個 global
  safety cap（防暴衝），fallback 不能重複佔用 global slot。
- Vision-aware routing：分 `VISION_REQUIRED`（Screenshot）跟 `VISION_OPTIONAL`（tooltip icon，
  文字模型也能選，只是不附圖）。
- Runtime state（in-flight/cooldown/failure count/latency）不寫進 TOML，只有 enabled/apiKey/
  model/rpm/priority 這些玩家設定才存。
- Migration 要保守：舊玩家的 legacy `endpoint` 只要有 credentials 就要保留 eligible，不能升級後
  突然把 request 送到一堆沒填 key 的新 provider。

這輪的架構重構（`libs/provider/` 的 adapter 層、per-provider TOML 欄位、`ProviderInfo`）已經在
上一輪（M1-Q3）做完並且合併了——這次會直接重用，不重造第二套。目前正在照使用者自己列的六步驟做
（先讀現有 code、畫 request flow、找 Router 插入點、確認不會破壞 cache/IN_FLIGHT，再動手），規劃
完會先發一輪完整的架構信給你看，這封只是讓你知道範圍。
