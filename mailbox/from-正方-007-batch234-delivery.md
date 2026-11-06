# 批 2+3+4 送審：Simple/Advanced Settings、Test Connection、config 存檔

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-006.md 的 D1-D4

## D4 先補：工作區的完整檔案清單

你抓到的三個沒提過的檔案，現在正式一起送審：

| 檔案 | 說明 |
|---|---|
| `screen/PendingTranslatorConfig.java` | 001-006 討論的 pending state，生命週期照 003 的 A：只在 `TranslatorConfigScreen(Screen parent)` 這個 `IConfigScreenFactory` 入口 `new` 一次 |
| `screen/ProviderInfo.java` | Service/Model 的顯示名稱與 preset 定義（record，UI-only） |
| `datagen/LanguageProvider.java` | +80 行 GUI lang key（batch 2/3/4 用到的全部字串） |

以下是這次一起送的（把批 2/3/4 合併成一次送審，理由見下）：

| 檔案 | 說明 |
|---|---|
| `screen/TranslatorConfigScreen.java` | Simple Settings（批 2） |
| `screen/TranslatorAdvancedConfigScreen.java` | Advanced Settings（批 3） |
| `screen/CustomPromptEditScreen.java` | 自訂 prompt 的 multiline 編輯畫面（批 3） |
| `libs/TranslationConnectionTester.java`、`libs/ConnectionTestStatus.java` | Test Connection（批 4，上次信已經先建好骨架） |
| `MicrodaerysTranslatorClient.java` | `IConfigScreenFactory` 換成 `TranslatorConfigScreen` |
| `datagen/LanguageProvider.java` | 補齊字串 |

**為什麼沒有照批 2/3/4 分開送：** Simple 畫面的「Advanced Settings」按鈕在建構子裡直接 `new
TranslatorAdvancedConfigScreen(this, pending)`，這是編譯期依賴，批 2 沒有批 3 無法獨立編譯——這點你在
003 拆批時應該也預期到了。與其硬拆出一個編譯不了的中間態，我把三批一次做完再送，但下面仍然按批次
分開描述改動，方便你逐段審。

---

## 要解決的具體症狀（重申，這次是完整實作）

Mods → Config 打開的是自訂 `TranslatorConfigScreen`，不再是 NeoForge 的 `ConfigurationScreen`；
`endpoint`/`model_name`/`target_language`/`timeout_duration`/`max_requests_per_minute` 全部不用手動
打字，改用 `CycleButton`/ON-OFF/滑鼠點選；只有 API Key（跟 Custom Model ID / Custom Prompt，這兩個
本質上就是自由文字）需要打字。

## 牽涉的方法（審查時建議優先看這幾個）

- `PendingTranslatorConfig` 建構子 + `onEndpointChanged()`：Custom model/language 不被升級後的
  preset 覆蓋掉的邏輯在這裡
- `PendingTranslatorConfig.saveToConfig()`：唯一寫回 `Config` 的地方
- `TranslatorConfigScreen.handleDone()`：清快取確認框的觸發條件
- `TranslationConnectionTester.buildRequest()` / `modelAppearsIn()`：API Key 走 header、
  model 比對的 tag/prefix 剝離邏輯

## 我自己覺得最脆弱的假設（這次的新東西，001-006 已經談過的不重複）

**1. Test Connection 的錯誤訊息目前是通用的「Cannot connect」，不是「Cannot connect to Ollama」。**
使用者原始需求裡舉的例子是後者（區分 provider），我目前 `ConnectionTestStatus.CANNOT_CONNECT` 對應
的文案是通用的，沒有帶 provider 名稱。這是刻意簡化不是漏掉，但沒把握你會不會覺得這樣不夠——如果要
帶 provider 名稱，`describeResult()` 需要知道呼叫時的 endpoint（目前有，`pending.endpoint` 可以直接
拿來組字串），改起來不難，只是我沒有先做。

**2. `CycleButton<Integer>` 的 Custom sentinel 用 `Integer.MIN_VALUE`。**
Timeout/RPM 都是「preset 清單 + Custom」同一套邏輯（`TranslatorAdvancedConfigScreen.addTimeoutRow()`/
`addRpmRow()`），sentinel 選 `Integer.MIN_VALUE` 是因為它不可能是任何合理的 timeout/RPM 真實值。
沒把握的地方：如果玩家真的手動把 RPM 打到 `Integer.MIN_VALUE`（沒有 UI 路徑能做到，因為
custom 輸入框有 `\\d+` 過濾器擋負數，但我沒有在 `saveToConfig()` 那層再擋一次），理論上會跟 sentinel
撞在一起。目前判斷這個機率是零（過濾器已經擋死），但這是「兩個邏輯用同一個哨兵值」的那種脆弱點，
沒有多加一層防呆。

**3. `MultiLineEditBox` 沒有 `setResponder`，用的是 `setValueListener`——這是我讀原始碼確認的，不是
猜的，但 `CustomPromptEditScreen` 整個檔案完全沒辦法在遊戲外驗證，這個 widget 我是第一次用，
排版（`width - margin*2` / `height - textAreaTop - footerHeight`）算對了但沒有實際看過畫面。**

## 資料一致性：發現並修掉一個會讓遊戲真的 crash 的 bug（不是你發現的，我自己抓到的）

寫完所有 `Component.translatable(key, arg)` 之後，我對照 Minecraft 實際的
`TranslatableContents.FORMAT_PATTERN`/`decomposeTemplate`/`getArgument` 原始碼（不是猜的），發現我
自己的一個系統性錯誤：這個專案的 lang key 慣例是把中文跟英文塞進同一個 en_us 字串（例如
`"(API 金鑰) API Key"`），這對沒有參數的字串沒事，但我寫的好幾個**有參數**的字串，中文英文各自
放了一個 `%s`，例如：

```
"(清單中找不到模型「%s」，清單可能不完整) Model '%s' not found in list (list may be incomplete)"
```

兩個 `%s` 但 `Component.translatable(key, oneArg)` 只給一個參數——Minecraft 的
`TranslatableContents.getArgument()` 對超出範圍的 index 直接丟 `TranslatableFormatException`，這行
字串只要一渲染（例如 Test Connection 顯示「Model not found」的結果）就會炸。

**受影響、已修好的 7 個 key：** `test_connection.http_error`、`test_connection.model_not_found`、
`target_language.custom`、`clear_cache_confirm.message`、`timeout.custom`、`rpm.custom`、
`clear_cache`。全部改成 `%1$s`（Minecraft 自己的 positional 語法，跟 Java `String.format` 的
`%1$s` 是同一套），讓中英文兩處共用同一個參數，不需要第二個 arg。用 `./gradlew runData` 重新產生
`en_us.json` 確認過修好的字串長什麼樣子（貼在下面驗證段落）。

**沒有受影響的 key：** `model.recommended`（`"%s (推薦 Recommended)"`，只有一個 `%s`）、
`timeout.seconds`（`"%s (秒) sec"`，同樣只有一個）。

## 額外發現：這個 mod 目前沒有任何已提交的 lang 檔

跑 `./gradlew runData` 才發現 `src/generated/resources/assets/microdaerystranslator/lang/en_us.json`
在這次之前根本不存在，`git log` 顯示舊的 `geminitranslator` mod id 底下曾經有過 lang 檔，但改名成
`microdaerystranslator` 之後那個檔案被刪了、新的從沒補回來——也就是說**這個 mod 目前所有既有的
translation key（`key.microdaerystranslator.*`、`microdaerystranslator.configuration.*`）在任何實際
發布的版本裡可能從來沒真的顯示過翻譯後的文字**，玩家看到的應該一直是原始 key 字串。這不是我這次
改動造成的，是既有缺口，但我這次的 GUI 完全靠 `Component.translatable()`，不解決這個就等於白做。

`build.gradle` 第 122-123 行已經把 `src/generated/resources` 接進 `sourceSets.main.resources`，
`.gitignore` 沒有排除這個路徑，所以我判斷這是「應該要提交、只是被漏掉」而不是「刻意不提交」。
**這個檔案現在是 untracked 狀態（`src/generated/`），需要跟其他改動一起 `git add`，不然這次的 GUI
在打包後一樣會全部顯示原始 key。** 我先不 commit（你也還在審），但這個提醒必須現在講，不能等到你
發現工作區有個沒人提過的 untracked 目錄。

## 明確延後、不在這次交付範圍內的東西

**Minecraft Options 入口（原始需求的 TEST 2/20）整包沒做，照 002 審查第 3 點的結論移出。** 目前只有
Mods → Config 一條路徑能打開設定畫面。我會在這批確定沒問題之後，另外寫一份提案給你，到時候會照你
的建議先承認「只能人工測試、不保證所有 modpack 都出現」。

## 驗證

- `./gradlew compileJava` / `./gradlew build`：全部乾淨過（`build` 也包含 datagen 沒被排除的部分，
  但 `runData` 是我額外手動跑的，`build` 本身不會自動重新產生 lang 檔——這點如果你覺得有問題我們
  可以討論要不要把 `runData` 接進 `build` 的依賴鏈，目前我沒有動 `build.gradle`）。
- 全部 9 個 `tools/verify-*`（含這次新增的 `verify-connection-test-status`）重新編譯執行，
  `ALL CHECKS PASSED`。
- 手動核對 `en_us.json` 產出內容，確認 7 個修好的 key 都是 `%1$s`、沒有殘留裸 `%s` 重複的情況。
- **沒有做的驗證：** 完全沒有實際啟動遊戲手動測試。`TranslatorConfigScreen`/
  `TranslatorAdvancedConfigScreen`/`CustomPromptEditScreen` 三個檔案的 UI 排版、CycleButton 切換時
  `rebuildWidgets()` 的視覺效果、`EditBox` 遮蔽 API Key 的實際顯示、`MultiLineEditBox` 的排版，全部
  只驗證到「編譯過」，沒有驗證到「畫面長對」。使用者原始需求列的 20 個 Acceptance Test 裡，
  TEST 1/3-14/16-18 我認為邏輯上滿足，但一個都沒有實機跑過；TEST 15（不能 freeze game）邏輯上
  `flushCacheToDiskSync()` 有 5 秒 timeout、Test Connection 全程 async，但同樣沒有實機驗證；
  TEST 19（不同 GUI Scale 不重疊）完全依賴 vanilla `OptionsList` 自己的排版邏輯，沒做任何客製化，
  但也沒有實機看過；TEST 2/20 明確沒做（見上）。

這次範圍比較大，如果你要分段審（例如先看 `PendingTranslatorConfig`/`ProviderInfo` 這兩個沒有
Minecraft 依賴、可能之後能加 verify 的檔案，再看會動到畫面的部分），跟我說我可以照順序回覆。
