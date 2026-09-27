# 新一輪：Provider 從 3 個擴充到 11 個（開工通知）

**From:** 正方
**To:** 反方
**時間:** 2026-09-02

## 背景

使用者開了新的一輪需求：目前 Config GUI（批 2+3+4 + Options 按鈕，你上一輪已經確認可以合併）不變，
但翻譯供應商要從 Google/Mistral/Ollama 3 個擴充到 11 個：

Google AI Studio, NVIDIA NIM, Groq, OpenRouter, Mistral AI, DeepSeek, Cerebras, Anthropic Claude,
OpenAI, Ollama (Local), Custom Provider。

同一時間仍然只 active 一個 provider——明確排除 automatic fallback / round robin / load balancing /
multi-provider simultaneous requests，這些之後才做。

## 這輪的架構方向（已經做了一半）

- `Config.EndPoint` 新增 8 個值，**只 append，不 rename/reorder**現有的
  `GOOGLE_AI_STUDIO/OLLAMA/MISTRAL`（NeoForge `EnumValue` 用 `.name()` 存 TOML，append 對舊存檔安全）。
- 新增 `libs/provider/` 套件：`TranslationProviderAdapter` interface（`buildTranslationRequest`/
  `parseTranslationResponse`/`buildConnectionTestRequest`/`modelAppearsInConnectionTestResponse`/
  `supportsModelListing`），四個實作：`GeminiAdapter`、`AnthropicAdapter`（新）、`OllamaAdapter`、
  `OpenAiCompatibleAdapter`（NVIDIA/Groq/OpenRouter/Mistral/DeepSeek/Cerebras/OpenAI/Custom 共用一個
  class，靠 `OpenAiCompatibleSpec` 帶 baseUrl/authMode/headers 差異化）。`ProviderAdapterRegistry`
  是唯一查找點，取代掉原本 `Translator.java` 裡兩條各自獨立、都會「找不到就默默當 Google 處理」的
  `if/else` dispatch，跟 `TranslationConnectionTester` 自己另一份 `switch`——這三個各自獨立的
  dispatch point 已經收斂成一個。
- `Config.java` 每個 provider（除了 Ollama 不需要 key、Custom 另外一組欄位）都有自己獨立的
  `api_key`/`model` TOML 區塊，不再共用一組扁平的 `API_KEY`/`MODEL_NAME`——這是解決「切 Provider 會
  洗掉別人 API Key」這個真實 bug 的核心修法。舊的扁平 key **完全保留**，只當成一次性 migration 的
  fallback 來源（`ProviderConfigResolver`，`Translator`跟 `PendingTranslatorConfig` 共用同一份）。
- `PendingTranslatorConfig` 的 `lastModelPerEndpoint`（原本只記 model）擴充成記每個 provider 自己的
  `apiKey`/`model`（Custom 另外還有 name/baseUrl/authMode/supportsVision），在建構時就把全部 11 個
  provider 的設定從 Config 讀出來（套用 legacy fallback），不是等玩家這個 session 真的切過去才記。

## 目前狀態

程式碼還在寫，`./gradlew compileJava` 目前卡在最後一個已知錯誤（`TranslatorConfigScreen` 的
`describeResult` switch 少 case，剛加完新 case 還沒重編）。GUI 那邊（Custom Provider 面板：
Provider Name / Base URL / Authentication / API Key / Model / Supports Images）也才剛接上去。

11 個 provider 的 model id 沒有照使用者原始 spec 照抄——他自己在 prompt 裡明講「不要盲信這份清單」，
所以先開了 6 個平行的研究 agent 去對照每家官方文件/model listing，有幾個地方跟使用者原始 spec
不一樣（例如 NVIDIA 的「Muse Glimmer 30B」其實是 Meta 的模型只是掛在 NVIDIA catalog 頁面上、不是
NVIDIA NIM 自己的 infra，這輪先不放進 preset；Claude Haiku 的正確 id 是
`claude-haiku-4-5-20251001` 不是使用者寫的 `claude-haiku-4-5`；Ollama 的 `qwen3.5` 系列不存在，
實際是 `qwen3`）。完整清單跟每個改動的理由會在下一封信（程式碼跑得動、11 個 verify 都過之後）附上，
連同哪些是官方文件直接確認、哪些只能算「跟慣例推斷、還沒實機打過」。

這封先讓你知道這輪在動什麼，不是要你現在就審——等 build 跟 headless verify 都綠燈、我自己先過一輪
在 config 畫面裡切 11 個 provider 來回測過資料不會互相洗掉之後，會發完整的一輪信給你看程式碼本身。

如果你這邊想先看 `libs/provider/` 那幾個新檔案的介面設計（在真正接完 GUI 之前），也可以直接說，
我可以先把那幾個檔案單獨列出來。
