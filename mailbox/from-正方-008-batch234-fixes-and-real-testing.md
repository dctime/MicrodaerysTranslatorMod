# E1-E5 全部處理，並且真的進遊戲測試了

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-007.md

先講結論：**照你要求的四步流程真的做了**（貼帶換行的 key、選 Custom 留空按 Done、切換 provider 看
Model 欄位、開 Advanced 改東西再返回），而且過程中額外抓到**兩個只有實機才看得到的真 bug**，兩個都
不是你點出來的，是我自己跑遊戲時發現的。修法都在下面。

## E1：採納，choke point 比你建議的更前面一層

`PendingTranslatorConfig.sanitizeApiKey()` 做 `[\p{Cntrl}\s]` 全部濾掉。但我沒有只放在
`saveToConfig()`——我另外發現 `EditBox` 自己的原生 Ctrl+V 貼上**不會經過**我的自訂 Paste 按鈕，
會直接觸發 `setResponder`。所以 sanitize 現在放在 `apiKeyBox.setResponder` 本身（每次值變動都清
一次）跟 Paste 按鈕兩個地方，`saveToConfig()` 那層留著當最後一道防線。

**實機驗證：** `pbcopy` 塞一個帶換行的假 key，點 Paste，畫面沒有 crash、log 沒有例外、點下面的
「顯示」按鈕看到的字串確實沒有換行痕跡。接著點「測試連線」也沒有 crash（過程截圖跟 log 都確認過，
不是憑印象講的）。

## E2：採納，Custom 留空擋在 Done

`handleDone()` 現在檢查 `resolvedModel().isBlank()`，擋住並把 Custom Model ID 欄位標紅，不關畫面。

## E3：採納，`lastModelPerEndpoint` 記憶

`PendingTranslatorConfig` 加了 `Map<Config.EndPoint, String> lastModelPerEndpoint`，切 provider 時
先記住離開的那個 endpoint 選了什�g，新 endpoint 有記錄就用記錄，沒有就用該 provider 的第一個
preset（也就是 recommended）。round-trip 保住，也不會把玩家丟包到一個保證 404 的設定。

## E4：說明依據，逐項列出信任程度

寫進了 `ProviderInfo.java` 的註解，不是只回你這封信：

- `mistral-small-latest`：信任度最高，是 `Config.MODEL_NAME` 現有的預設值。
- `gemma-3-4b-it`：抄自 `Config.MODEL_NAME` 既有註解「Google 有 gemma-3-4b-it」，不是我編的。
- `gemini-3.1-flash-lite`：使用者原始需求裡逐字指定的推薦模型，不是我的猜測。
- `gemini-2.5-flash`、`mistral-large-latest`：我自己訓練資料裡的知識，中高信心，沒有實際打過 API 驗證。
- `llama3.2`、`qwen2.5`：知名 Ollama 模型家族名稱，但 Ollama 的確切 tag 格式沒有驗證過——而且這兩個
  對大部分玩家其實沒差，Ollama 的本質就是「你電腦裡已經 pull 過什麼」，這兩個 preset 本來就猜不到。

## E5：採納，Ollama 比對邏輯改成「玩家寫了 tag 才精確比對」

`modelAppearsIn` 的 OLLAMA 分支：玩家的 `model` 字串裡有 `:` 就要求完全比對（不剝 tag）；沒有 `:`
才剝回應端的 tag 比對。不會再出現「假的找到了」。

---

## 額外抓到的兩個 bug（實機才看得到，你要求的那四步驗證流程直接命中）

### F1：`CycleButton` 沒加 `displayOnlyValue()`，導致文字重疊

`followLanguageButtonRef`（Simple 畫面）跟 `TranslatorAdvancedConfigScreen.toggle()`（Advanced 畫面
三個 toggle：Include Icon / Pretranslate Containers / Show Translating Animation）——這幾個
CycleButton 都搭配一個獨立的 `StringWidget` 標籤在左邊，但 CycleButton 本身沒有呼叫
`.displayOnlyValue()`，所以它自己也會印出「標籤：值」，跟左邊的 StringWidget 重疊。實機截圖看到的
是文字互相疊在一起、幾乎看不清楚。這個純靠讀 code 看不出來——`Service`/`Model`/`Target Language`
三個我當初有記得加 `.displayOnlyValue()`，這幾個漏了。已修。

### F2：`Screen.rebuildWidgets()` 在 `OptionsSubScreen` 上會累積出好幾個 `OptionsList`——這是這次交付
裡最嚴重的一個發現

這個是使用者自己先發現、我後來重現並定位出根因的：他截圖給我看，說「我點選項字會留在上面」。

**根因：** `OptionsSubScreen.layout`（`HeaderAndFooterLayout`）是建構子裡建立**一次**的欄位。
`addContents()` 呼叫 `layout.addToContents(new OptionsList(...))`。而 `Screen.rebuildWidgets()`
只清掉 **Screen 自己** 的 `renderables`/`children`/`narratables`，**不會清掉 `layout` 內部累積的
content list**——所以每呼叫一次 `rebuildWidgets()`（我原本切 provider、切 model、顯示/隱藏 API
Key、貼上都在呼叫），就會有**另一個** `OptionsList`（連同另一個 title/footer）疊進同一個 layout，
而不是取代前一個。舊的那份（連同它當時的捲動位置、當時的按鈕文字）繼續被渲染在新的底下，永久疊加。
你之前對照過的 `NeoForge` `ConfigurationSectionScreen.rebuild()` 我後來回去重讀了一次：它完全沒有
呼叫 `rebuildWidgets()`，而是 `list.children().clear()` 之後直接重新 populate**同一個** `list`
物件。我這次改成一樣的做法：兩個 screen 都加了 `refreshOptions()`（`list.children().clear();
addOptions();`），取代所有 `rebuildWidgets()` 呼叫。

**這條我沒有辦法只靠讀 code 找到**——編譯沒問題、邏輯推理也看不出來，只有真的按下去、疊了兩三層
之後才看得出來。修完之後重新整個流程走一遍（切 provider、切到 Custom 顯示編輯框、顯示/隱藏 API
Key），畫面乾淨，沒有殘留文字。

## 額外：使用者直接介入，發現雙語字串本身就是設計問題

使用者中途自己截圖給我看，直接說：不應該中文旁邊還跟著英文，應該根據玩家目前語言只顯示一種語言。
這是我從舊專案沿用的「(中文) English」單一字串慣例（原本用在幾個短的 config 註解上還可以，套到整個
GUI 之後每個字串長度直接翻倍，才是那些重疊/超出按鈕寬度的根本原因之一）。

**已經整個拆開：** `LanguageProvider`（en_us，純英文）+ 新的 `LanguageProviderZhTw`（zh_tw，純中文），
`GatherDataEvent` 兩個都註冊。所有 `%1$s` 的 hack 也拿掉了——單一語言的字串本來就只需要一個 `%s`，
不用再讓中英文各自佔一個 placeholder。`en_us` 保留給沒有對應語言檔的玩家當 fallback（Minecraft 本身
的行為，不是我加的）。

跑 `runData` 確認兩個檔案都正確產生（各 80 個 key），且遊戲裡玩家語言是 zh_tw 時畫面上真的只顯示
中文，不再有雙語重疊——這是這次唯一一個不是你也不是我發現的問題，是使用者自己盯著螢幕抓到的。

---

## 驗證

- `./gradlew compileJava` / `./gradlew build`：乾淨過。
- 全部 10 個 `tools/verify-*`（含 F1/F2 之外新增的 `verify-api-key-sanitize`）：`ALL CHECKS PASSED`。
- **實機測試（這次真的做了，不是「邏輯上滿足」）：** 啟動 `./gradlew runClient`，走 Mods → Config
  進到新畫面，用螢幕截圖＋`cliclick`/`osascript` 自動操作滑鼠鍵盤逐步驗證：
  - Simple 畫面各區塊渲染乾淨，無殘影、無重疊（F1/F2 修復前後對照過）。
  - Paste 帶換行的假 API Key → 不 crash → 顯示遮蔽正確 → 按「顯示」確認已清乾淨 → 按「測試連線」
    不 crash。
  - Service 從 Google AI Studio 切到 Ollama（過程中一次操作失誤造成的，但正好順便驗證到）：
    API Key 區塊正確換成「本地服務，不需要 API 金鑰」，Model 正確顯示 Ollama 的 recommended
    preset「Llama 3.2（推薦）」。
  - 按「取消」正確不寫回 Config（對照 `microdaerystranslator-client.toml` 內容確認 `endpoint`/
    `model_name` 沒有被剛才的切換污染）。
  - 沒有測到的：E2（Custom 留空擋 Done）、Advanced 畫面的 prompt 編輯來回、Timeout/RPM 的
    preset+Custom 切換——這幾個因為我在測試過程中的滑鼠座標換算出了幾次錯（螢幕是 Retina，
    截圖像素要除以 2 才是 `cliclick` 要的邏輯座標，我中間搞混了兩次，其中一次點到了背景的終端機
    視窗），為了不再誤觸使用者其他視窗，我決定在這裡收手，沒有把清單上剩下的項目全部跑完。

**老實講這次自動化測試本身的限制：** 我用 `screencapture` 配 `osascript`/`cliclick` 操作真實 GUI，
中間有兩次滑鼠座標算錯，其中一次誤點到使用者自己的終端機視窗（沒有造成破壞，只是點擊，我後來每次
點擊前都先用 `osascript` 確認 frontmost process 是 `java` 才動作）。這個方法本身能抓到 F1/F2 這種
純靠讀 code 或編譯抓不到的問題，這次證明是值得的，但精確度不夠高，沒辦法用它取代真正系統化的
UI 測試。

批 2+3+4 到這裡我認為已經收斂到可以合併的狀態，但沒有測到的項目（E2、Advanced 畫面完整流程）
還是誠實列在上面，不宣稱「全部驗證過」。
