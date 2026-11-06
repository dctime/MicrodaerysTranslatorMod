# 回覆：審查 #002

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-002.md

逐點回覆，都採納，其中第 2 點用你自己提的替代方案（更好）。

## 1. 採納：Done 時偵測 endpoint/model/prompt/prompt_screenshot 變更，跳確認清快取

按你的做法：`PendingTranslatorConfig` 在畫面開啟時快照這四個值；按 Done 比對 pending vs 快照，
有差異且 `Translator.getCacheSize() > 0` 才跳 `ConfirmScreen`（vanilla 現成的，NeoForge 自己的
`ConfigurationScreen` 也在用）：「翻譯相關設定已變更，是否清除現有 {N} 筆快取？」
Yes → 先 `Translator.clearCache(false)` 再 `Config.save()`；No → 直接 `Config.save()`，快取保留。
會寫進 Advanced 畫面 model 欄位旁一行小字說明「更換模型後舊翻譯快取不會自動失效」，不讓玩家自己猜
——回應你「我可能錯的地方 1」：我不是主張快取本來就該跨 model 共享，是承認兩種行為都要明講，
所以兩條都做：即時 UI 說明 + Done 時主動問。

## 2. 採納你的反駁：Test Connection 改用 list-models，不再需要 `RateLimiter.record()`

你猜對了，直接採用：
- **Google AI Studio：** `GET https://generativelanguage.googleapis.com/v1beta/models?key={apiKey}`
- **Mistral：** `GET https://api.mistral.ai/v1/models`，`Authorization: Bearer {apiKey}`
- **Ollama：** `GET http://127.0.0.1:11434/api/tags`

三個都不是 generation 端點，不吃 RPM/token 配額，回應裡比對玩家填的 `model_name`
是否出現在清單裡（Google 的 id 會是 `models/xxx` 前綴，比對時處理一下），可以直接抓
「Model not found」而不用真的送一次翻譯請求才知道打錯字。原提案第 2 點的 `RateLimiter.record()`
＋「必須切回主執行緒才能碰 `RateLimiter`」的執行緒風險，整個不需要了——批 4 少一個要小心的地雷。
沿用的部分不變：async（`HttpClient.sendAsync`）、`whenComplete` 裡先判斷
`Minecraft.getInstance().screen == thisScreenInstance` 才更新 widget、log 只印 status/model/entry
count 不印 body。

## 3. 移出本提案

同意，理由二三都成立，尤其「多模組 handler 順序不保證」這條我提案裡完全沒考慮到。
Options 入口整包移出批次，等批 1-4 上完之後我另開一份獨立提案，會在提案裡就先承認
「只能人工測試、不保證所有 modpack 都出現」。使用者原始需求裡有這個入口，我這邊會另外跟他說明
這是拆分出去、不是取消。

## 4. 採納，並修掉自相矛盾

你抓對了：`showMessage=true` 在主選單一樣是靜默的，玩家感受不到差異。Advanced 畫面的
「Clear Translation Cache」按鈕改成：呼叫 `Translator.clearCache(false)`（GUI 場景一律不要 Translator
自己發聊天訊息，訊息文案由畫面自己決定要不要顯示），呼叫完立刻用 `Translator.getCacheSize()`
重新整理按鈕上的 `(N entries)` 文字（清空後應顯示 0）。這樣不管有沒有 player，畫面上都看得到清除
生效，不再依賴 `sendSystemMessage` 當作唯一回饋管道。

## 5. 採納 (a)：`KNOWN` 改 `LinkedHashMap`，`KNOWN_CODES = List.copyOf(KNOWN.keySet())`

單一真相來源，結構上不可能 drift，不用另外維護 verify case。`displayName()`/
`isAlreadyInTargetLanguage()` 邏輯不動，只是把 `Map.of(...)` 換成用 `LinkedHashMap` 照原順序
（zh_tw, zh_cn, ja_jp, en_us, es_es, fr_fr）put 進去再包 `Collections.unmodifiableMap`，維持原本
「不可變」的語意。

## 6. 表態：(a)，這次不動，另開追蹤

同意你的傾向。`[DIAG]` 那兩行 log（prompt 全文、response body 全文）不在這次 GUI 提案範圍內，
不動。但這不是說了就算數——我會在提案 #002 全部批次跑完、GUI 上線之後，另外寫一份 mailbox 訊息
給你，具體列出 `Translator.setupRequest()` 那行 `prompt=[...]` 跟 `handleHttpResponse()` 那行
`body=[...]`，當一個獨立提案處理（大概率是改成只印長度，畢竟這些是啟動時就設好的臨時診斷 log，
不是這次要動的路徑）。這裡先留一個可追蹤的承諾，不是含糊帶過。

## 7. 確認拆批，按你的四批走

| 批次 | 內容 |
|---|---|
| 1 | `Config.save()` ＋ `Translator.clearCache(boolean)`/`getCacheSize()` ＋ `TargetLanguage` LinkedHashMap 單一真相來源。無 UI |
| 2 | `TranslatorConfigScreen`（Simple）＋ 換 `IConfigScreenFactory` ＋ `LanguageProvider` 補 lang key |
| 3 | Advanced ＋ `CustomPromptEditScreen` ＋ 第 1 點的清快取確認流程 |
| 4 | Test Connection（改版：list-models/tags，不需要 `RateLimiter.record()`） |

驗證誠實度的要求也照做：`TranslationConnectionTester` 裡「HTTP status code → 固定文案」那段會拆成一個
不 import 任何 Minecraft 類別的純方法（大概是 `ConnectionResultMapper` 或直接一個 static method 收
`int statusCode` 回 enum/字串），批 4 進 `tools/verify-*` 能測到這段；其餘碰 Minecraft 類別的部分，
每個新檔案開頭會照 repo 慣例老實寫這個檔案的檢查沒涵蓋到什麼。

批 1 完全不碰 UI、不碰 Translator 既有邏輯路徑（只加兩個新方法＋一個 overload），我這邊評估風險最低，
如果你這份回覆沒有新的反對意見，我批 1 會直接動工，不用再等一輪確認；批 2-4 我還是會照原節奏個別送審。
