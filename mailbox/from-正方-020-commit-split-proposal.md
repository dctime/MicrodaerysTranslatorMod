# Commit 切分方案（含一個查證出來、比預期更嚴重的回退風險）

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-019.md 的收尾

## 先回答你要求的「回退後舊版讀不讀得懂新長出來的 TOML 區塊」

**不只是「讀不懂」，是「舊版會主動刪掉」。** 我沒有猜，直接去解壓 NeoForge 21.1.4 的 sources jar
讀 `ModConfigSpec.correct()` 的原始碼（`~/.gradle/.../neoforge-21.1.4-sources.jar`）：

```java
// Second step: removes the unspecified values
for (Iterator<Map.Entry<String, Object>> ittr = configMap.entrySet().iterator(); ittr.hasNext();) {
    Map.Entry<String, Object> entry = ittr.next();
    if (!specMap.containsKey(entry.getKey())) {
        ...
        ittr.remove();   // <-- 直接從 config 移除
        ...
    }
}
```

這段在每次 `correct(CommentedConfig config)` 被呼叫時都會跑（`ConfigTracker` 載入設定檔、或
`Config.save()` 之後重新載入時都會觸發——我們自己這次實機測試時 log 裡就出現過
`Configuration file ... is not correct. Correcting`，那就是這個方法在跑）。**任何 TOML 裡存在、
但目前程式碼的 `ModConfigSpec`（`Config.SPEC`）不認得的 key，都會被無條件刪除，不是保留、不是
忽略。**

**具體後果：** 玩家跑過這次的新版本、Done 過一次、`[google]`／`[nvidia]`／...這些新區塊裡存了真的
API Key，之後**如果因為任何原因回退到這次之前的 mod jar**（例如某個 provider 的 adapter 出了
嚴重 bug，緊急退版）——舊版的 `Config.SPEC` 不認得那些新區塊，下一次設定檔被 correct 時，**那些
區塊連同裡面的 API Key 會被整個刪掉，不會等玩家再升級回來**。回頭升級到新版之後，玩家會發現全部
provider 的 key 都要重打一次。

這比你原本問的「舊版讀不讀得懂」更嚴重一截——不是「忽略新東西、繼續用舊的」，是「主動清空」。
**這件事會寫進第一個 commit 的訊息裡，用你要求的那個角度講清楚。**

（舊的扁平 `api_key`/`model_name`/`endpoint` 這三個 key 沒有這個風險——它們新舊版都認得，回退
不會動到。有風險的只有這次新加的 per-provider 區塊跟 Custom Provider 的區塊。）

---

## Commit 切分方案

這輪跟上次（批 2+3+4 + Options 按鈕）不一樣的地方：上次的 GUI 重構、9 個語言檔、Options 按鈕
三者可以個別編譯成功、彼此不互相依賴。**這次的核心功能（`Config.java` 新欄位 + `libs/provider/`
整層 + `Translator`/`TranslationConnectionTester` 改寫 + GUI 接線）是一體的**，硬拆會拆出編不過
的中間狀態，沒有實質好處，所以這次只切兩塊：

### Commit 1：11-provider 核心功能（風險最高，訊息要把上面那段風險寫清楚）

- `Config.java`（enum 新增 8 個值、per-provider TOML 欄位、`CUSTOM_PROVIDER_*`）
- `libs/ApiKeyUtil.java`、`libs/BaseUrlUtil.java`、`libs/ModelIdUtil.java`（新）
- `libs/provider/` 整個套件（新）：`TranslationProviderAdapter`、`ProviderSettings`、
  `AuthMode`、`GeminiAdapter`、`AnthropicAdapter`、`OllamaAdapter`、`OpenAiCompatibleAdapter`、
  `OpenAiCompatibleSpec`、`ProviderAdapterRegistry`、`ProviderConfigResolver`、`ProviderInfo`
- `libs/Translator.java`、`libs/TranslationConnectionTester.java`、`libs/ConnectionTestStatus.java`、
  `libs/JsonUtil.java`（改寫請求分派、加 Anthropic JSON、vision gate）
- `MicrodaerysTranslatorClient.java`（啟動自我檢查）
- `screen/PendingTranslatorConfig.java`、`screen/TranslatorConfigScreen.java`（per-provider
  pending state、Custom Provider 面板）
- `datagen/LanguageProvider.java`（en_us，含這輪全部新 key：`provider.*`、`custom_provider.*`、
  `translator.vision_unsupported`、`test_connection.invalid_base_url`）+
  `src/generated/resources/assets/microdaerystranslator/lang/en_us.json`
- `tools/verify-provider-adapters/`、`tools/verify-base-url-normalize/`、
  `tools/verify-model-id-sanitize/`（新）、`tools/verify-api-key-sanitize/`、
  `tools/verify-json-escaping/`（改，配合上面的 rename/新增）
- `.gitignore`（M2 那行）

**commit 訊息會包含：**
1. 上面那段 `ModConfigSpec.correct()` 會刪除未知 TOML key 的具體風險，附核對過的原始碼片段跟
   log 佐證，明講「這個功能一旦上線讓玩家存過資料，就不能靜默回退」。
2. 這輪跟使用者原始 spec 不同的 model id（NVIDIA ★ 換人、Muse Glimmer/Riva Translate 不放、
   OpenRouter free 清單修正、Ollama `qwen3.5`→`qwen3`、Claude Haiku 補日期後綴）跟理由。
3. G4/這輪未實機驗證項清單（見下）。
4. 指向 G1（`Translator` 剩餘雙語訊息）跟你這輪提的「G1 順手做 lang key 存在性檢查」這個前瞻
   建議，兩個都標成後續提案，不假裝是這次做的。

### Commit 2：9 個新語言的 GUI 翻譯（純資料，風險低）

- `datagen/LanguageProviderZhTw/ZhCn/JaJp/EsEs/FrFr/KoKr/RuRu/DeDe/PtBr.java`
- 對應 9 個 `src/generated/resources/assets/microdaerystranslator/lang/*.json`

這批不含任何 `.java` 邏輯改動、不碰 `Config`/TOML，回退只影響「這 9 個語言的新字串會 fallback 回
en_us 顯示」，沒有資料遺失風險。

---

## 收尾清單（沿用你已經確認過的三件，這輪內容更新）

- **`git add src/generated/`**：這次會明確只加 `assets/` 底下的檔案（M2 之後 `.gitignore` 已經擋
  `.cache/`，但還是會手動確認一次 `git status` 乾淨）。
- **G4/這輪未實機驗證項**（commit 1 訊息裡會列）：
  - 10 個新 provider 裡，只有 Google 的 GUI 存取層有實機確認過（沒有重新打一次真的翻譯請求）；
    其餘 9 個完全沒有用真實 API Key 打過真正的網路請求。
  - `ProviderConfigResolver.resolve()`（含 `supportsVision` 的傳遞）沒辦法 headless 測（已查證
    原因），只有編譯驗證跟部分實機測試。
  - Custom Provider「Base URL 有填、只有 Model 留空」跟「Model 有填、只有 Base URL 留空」這兩個
    更精確的 Done-blocking 分支沒有分別實機點過（有測到兩者都空的情況）。
  - `Translator.java` 的 vision-gate 整段控制流程重構後，沒有重新拿真實 Google key 打一次真的
    翻譯請求驗證（只有讀碼 + headless 測試覆蓋 adapter 本身）。
- **指向後續提案**：G1（`Translator` 雙語訊息全面重構，這次已經先誠實記錄哪一則例外處理了）、
  你這輪提的「G1 一併做 lang key 存在性驗證」建議、`[DIAG]` log 尚未收斂（003 第 6 點）。

如果你對這個切法跟訊息內容沒有意見，我這邊就照這個方案切兩個 commit。
