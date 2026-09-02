# 審查：批 2+3+4 交付

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-007-batch234-delivery.md

## 結論：**不通過。有 1 個會造成「重開遊戲也修不好」的 crash（E1），另有 1 個必須修（E2）、1 個設計取捨要重做（E3）、1 個要你說明依據（E4）、1 個小的（E5）。**

D1–D3 全部通過，你自己抓到的 `%s` crash bug 我獨立核對過、是真的，那是這次交付裡最有價值的東西。但 E1 正好證明了這批的風險在哪：一個只要真的按一次按鈕就會發現的問題，躲過了編譯和全部 9 個 headless 檢查。

---

## E1.〔嚴重・必須修〕Paste 按鈕把未過濾的剪貼簿寫進 API Key → 可造成重開遊戲也修不好的持續 crash

```java
Button pasteButton = Button.builder(..., b -> {
    String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
    if (clipboard != null) pending.apiKey = clipboard;      // 原封不動
    rebuildWidgets();
}).build();
```

**我去讀了 `KeyboardHandler.getClipboard()` 的原始碼**：它只做 `StringDecomposer.filterBrokenSurrogates(s)`，**不移除換行、不移除控制字元**。而從網頁或文字檔複製 API key 帶一個尾端 `\n` 是最常見的情況之一。

**路徑 1 — 按 Test Connection 就 crash：**
`TranslationConnectionTester.buildRequest()` → `.header("x-goog-api-key", apiKey)`。Java 的 `HttpRequest.Builder.header()` 對非法 header value 會丟 `IllegalArgumentException`，而且這是在 `sendAsync` **之前**、**同步**在 render thread 上、就在按鈕的 click handler 裡執行的。例外直接往上竄。

**路徑 2 — 更嚴重，會被寫進設定檔：**
按 Done → `Config.API_KEY.set(帶換行的字串)` → 落地 TOML。之後**每一次翻譯請求**走 `Translator.setupRequest()` 的 `.header("x-goog-api-key", ...)` 都會丟同一個例外，而且是在 tooltip render 的 render thread 上。`RenderTooltipEvent` 只 catch `IOException` / `InterruptedException`，**接不到 `IllegalArgumentException`**。

結果：**滑鼠碰到任何物品就 crash，重開遊戲照樣 crash**（壞值在設定檔裡），連想進設定畫面按 Test Connection 排查也會 crash。玩家唯一的活路是自己去手改 TOML——正好是這個 GUI 想消滅的那件事。

**為什麼是這次新增的：** 以前唯一設定 key 的途徑是手改 TOML，而 TOML 基本字串本來就不能含裸換行，parser 會擋下來。Paste 按鈕開了一條一鍵直達的路。

**建議做法——在 choke point 消毒，不要只修 Paste 按鈕：**
放在 `PendingTranslatorConfig`（Paste 跟 EditBox 兩條路最後都會經過 `saveToConfig()`），例如 `apiKey.replaceAll("[\\p{Cntrl}\\s]", "")`。API key 本來就不含空白字元，這個處理不會誤傷任何合法值。Paste 按鈕那邊也順手做一次，讓畫面顯示的就是實際會用的字串，而不是「看起來有、送出去炸掉」。

## E2.〔必須修〕Custom 模型留空可以一路按到 Done，寫出空的 `model_name`

`handleDone()` 沒有任何驗證。玩家選 `Custom...`、輸入框留白 → `resolvedModel()` 回 `""` → `Config.MODEL_NAME` 存成空字串。

- Google 組出 `https://generativelanguage.googleapis.com/v1beta/models/:generateContent` → 404
- Mistral 的 body 是 `"model": ""` → 422

**而 Test Connection 抓不到這件事**：它打的是 list-models 端點，跟 `model_name` 無關，所以會顯示「Connected」＋一行「找不到模型 ''」的黃字提示。而你（正確地，照 003 的 C）已經把 model 比對降級成提示不是錯誤，所以玩家可以無阻礙地一路 Done。兩個各自正確的決定疊出一個沒人擋的洞。

**建議：** 在 `handleDone()` 擋住——`resolvedModel().isBlank()` 時不關畫面，把 Custom Model ID 那欄標紅並顯示提示。**擋住比靜默回退到預設值好**，因為玩家是明確選了 Custom 才會走到這裡，偷偷幫他換掉會更難理解。

## E3.〔設計取捨・請重做〕切換 Service 之後 Model 停在上一個 provider 的 id

`onEndpointChanged()`：`current = resolvedModel()` → 新 provider 的 preset 找不到 → `modelSelection = CUSTOM_MODEL`, `customModel = current`。

所以：Mistral（`mistral-small-latest`）→ 切到 Google AI Studio → Model 欄位顯示「Custom... / `mistral-small-latest`」。**新手換完 provider 拿到的是一份保證 404 的設定**，這對「新手只填 API Key 就能用」是直接傷害。

**我理解你的目的**（切過去再切回來不要弄丟選擇），而且你現在的寫法**確實能正確 round-trip**：切回 Mistral 時 `current` 重新對上 preset。所以這是取捨，不是單純寫錯——但取捨的代價落在最不該承受它的人身上。

**建議（兩者兼得，約 5 行）：** `PendingTranslatorConfig` 裡放 `Map<Config.EndPoint, String> lastModelPerEndpoint`。`onEndpointChanged()` 先把目前的 `resolvedModel()` 記到**舊** endpoint 底下，再查**新** endpoint 有沒有記錄：有就用，沒有就用 `ProviderInfo.of(newEndpoint).models().get(0).modelId()`（你的 `ProviderInfo` javadoc 已經定義「第一個就是推薦預設」）。round-trip 保住，換 provider 的預設值也合理。

## E4.〔請說明依據〕`ProviderInfo` 的 preset model id 是怎麼確認的？

`gemini-3.1-flash-lite`、`gemini-2.5-flash`、`gemma-3-4b-it`、`mistral-small-latest`、`mistral-large-latest`、`llama3.2`、`qwen2.5`。

這些是**寫死在 UI 裡、標成「推薦 Recommended」的預設值**。任何一個 id 不存在或已改名，新手第一次用就是 404，而唯一逃生口（Custom）需要他自己知道正確 id——正好是這個 GUI 要消滅的問題。

**我不是說它們錯，我沒有辦法在這裡驗證。** 但這跟我一路要求的驗證誠實度是同一件事：請說明這份清單的來源（provider 官方文件？實際打過 list-models？還是憑印象）。如果是憑印象，建議至少把每個 provider 的第一順位換成你有把握的——`mistral-small-latest` 是 `Config.MODEL_NAME` 現有的預設值，至少是這個專案已經在用的字串。

## E5.〔小〕Ollama 的 model 比對兩邊都剝 tag，會給出假的「找到了」

```java
case OLLAMA -> streamNames(...).anyMatch(name -> stripTag(name).equals(stripTag(model)));
```

玩家填 `llama3:70b`，本機只裝了 `llama3:latest` → `stripTag` 兩邊都變成 `llama3` → 比對成功 → 顯示綠色「Connected」、**沒有**黃字提示 → 玩家以為沒問題，實際翻譯時 Ollama 回 model not found。這比不比對更糟，因為它給了假的保證。

**建議：只剝回應端的 tag。** 玩家有寫 tag 就精確比對；玩家沒寫 tag 才拿剝過的名字比。這剛好對應 Ollama 自己的語意（沒寫 tag = `:latest`）。

---

## 通過 / 做得好

- **D2/D3 兩條都修了，而且比我要求的完整。** `get(CACHE_WRITE_TIMEOUT_SECONDS, SECONDS)`；三個 catch 分支各自 `cacheDirty = true`，而且註解分別說明了理由為什麼不同（Interrupted =「不知道寫成沒」、Execution =「確定沒寫成」、Timeout =「還在跑」）；`writeCacheToDisk` 的 `IOException` 也補上了，還寫明「兩個呼叫端都在任務執行前就把 flag 設 false，是為了避開 re-check race」——這層理由是我沒講、你自己補完的。
- **你自己抓到的 `%s` × 2 crash bug 是這次交付裡最有價值的東西。** 我獨立核對過 `src/generated/resources/assets/microdaerystranslator/lang/en_us.json`：那 7 個 key 全部是 `%1$s`，`grep -E '%s.*%s'` 沒有任何殘留。而且你是去讀 `TranslatableContents.getArgument()` 確認超界會丟 `TranslatableFormatException` 才下結論，不是猜的。這正是我要的做事方式。
- `PendingTranslatorConfig` 的 class javadoc 把 003 的 A 連同「在 `init()` 裡快照會怎麼靜默失效」一起寫進去了。
- **`translationRelevantSettingsChanged()` 只比 endpoint/model/prompt/promptScreenshot，沒有把 target language 算進去——這是對的**，快取 key 本來就含語言，多比會讓玩家每次換語言都被問一次。你沒有多比。
- `onTestConnectionPressed()` 的 `Minecraft.getInstance().screen != this` 檢查有做；Test Connection 的 `whenComplete` 完全不傳 throwable，註解寫明理由。003 的 B 完整落實。
- **「合併批 2/3/4 送審」的理由我接受**：Simple 對 Advanced 是編譯期依賴，硬拆會產生一個編譯不了的中間態。這是我拆批時沒想到的，你講得對。

## `src/generated/` 未提交

**你的判斷我同意，而且這是個真發現。** `.gitignore` 沒有排除 `src/generated`，`build.gradle:122-123` 也確實把它接進 `sourceSets.main.resources`，所以它是「該提交但被漏掉」。

但這代表**一個跟你這次改動無關的既有 bug**：現有的 `key.microdaerystranslator.*` 和 `microdaerystranslator.configuration.*` 在已發布版本裡很可能一直顯示原始 key。**請單獨開一個 issue**，不要讓它混在 GUI 的 commit 裡被當成 GUI 的一部分帶過去——那樣以後沒人查得到這個既有缺口是什麼時候、為什麼被修的。

**`runData` 要不要接進 `build`：我建議不要接。** datagen 產物應該是 review 得到的檔案；自動重生成會讓每次 build 後 diff 都在動，反而看不出人為改動。維持手動跑 + 提交產物。

---

## 我可能錯的地方

1. **E1 我沒有實際貼一個帶換行的 key 進去看它 crash。** 推論鏈是：`getClipboard()` 不過濾控制字元（我讀了 `KeyboardHandler` + `ClipboardManager` 原始碼）→ 換行進 `pending.apiKey` → `HttpRequest.Builder.header()` 對非法 header value 丟 `IllegalArgumentException`（JDK 行為）。如果你實測發現路上有別的過濾把換行吃掉了，請反駁我——**但注意 `pasteButton` 是直接寫 `pending.apiKey`、沒有經過 `EditBox`**，所以就算 EditBox 那端有過濾，這條路也不受它保護。
2. **E4 我無法驗證那些 model id**，這條是要求你說明依據，不是指控它們錯。

---

## 關於「沒有實機測試」

你把「TEST 1/3-14/16-18 邏輯上滿足但一個都沒跑過」講得很清楚，這個誠實度是對的，我不會因為沒實機測就擋這批。

**但請不要把「邏輯上滿足」當成通過。** E1 正是一個只要真的按一次 Paste 就會浮出來的問題，而它躲過了編譯和全部 9 個 headless 檢查。這批的風險就集中在「沒有人看過這些畫面」，E1 是那個風險已經具體化的第一個證據——它大概不會是唯一一個。修完 E1–E5 之後，**在合併之前跑一次真的遊戲**，至少走完：貼一個帶換行的 key、選 Custom 留空按 Done、切換三個 provider 看 Model 欄位、開 Advanced 改 prompt 再返回按 Done。這四件事就能覆蓋 E1/E2/E3 和 003 的 A。
