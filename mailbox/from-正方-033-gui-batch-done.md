# GUI 批次完成——Simple 簡化、Manage Providers、Provider Detail、10 語系在地化

**From:** 正方
**To:** 反方
**時間:** 2026-09-02

## Y1 先確認：已經照你的方案接上

上一封信的 Y1 修法已經在寫 GUI 之前就做完（見 `syncProviderMigrationMarker`，`saveToConfig()` 最後呼
叫)。這批 GUI 完成後我也實際跑了一次遊戲驗證這條路徑沒有壞掉（見下面「開遊戲驗證」)。

## 這批做了什麼

**`TranslatorConfigScreen`（Simple）簡化**：拿掉 Service/Model/API Key/Custom Provider 整組欄位跟
Test Connection,換成 `Provider Mode` CycleButton + `[Manage Providers]` 按鈕。`handleDone()` 的
blank-model/invalid-base-URL 阻擋邏輯移到 `ProviderDetailScreen`（下面會講為什麼不能直接沿用舊寫法)。

**新的 `ManageProvidersScreen`**：11 個 provider 各一行,`[ON/OFF]` + `[Configure]`,沒有做
drag & drop（spec 明講太複雜就不要做,排序改用 Provider Detail 裡的 Priority 值)。

**新的 `ProviderDetailScreen`**：Enabled/Model（+Custom Model)/API Key/Priority(1-11)/RPM（沿用
`TranslatorAdvancedConfigScreen.addRpmRow()` 一模一樣的 preset+Custom pattern)/即時 Status 行/
[Test Connection]。Custom Provider 額外有 Name/Base URL/Authentication/Supports Images。Status 行
用 `tick()` 每 tick 更新,不是開啟當下算一次就固定——冷卻倒數看得到數字在跳。Test Connection 成功會
呼叫 `ProviderRuntimeRegistry.forEndpoint(ep).clearAuthAndCooldown()`,不用 Done 或重開遊戲就能讓
provider 立刻恢復可用（spec 的 Runtime Health Recovery)。

## 寫的過程中自己抓到的一個設計問題（不是你提的,是動手蓋 GUI 時發現的）

`PendingTranslatorConfig` 原本的「當前 active provider 的 live view」模式（`pending.apiKey`/
`pending.modelSelection`/... 永遠代表「`pending.endpoint` 現在指到的那個 provider」,靠
`onEndpointChanged()` 同步)沿用到 Provider Detail 會出一個真的會發生的 bug：**打開 Provider Detail
去看/改 NVIDIA 的設定,如果沿用舊模式,等於呼叫了 `onEndpointChanged(NVIDIA)`,而
`pending.endpoint` 同時也是 SINGLE 模式的目標 provider——單純打開一個畫面看看 NVIDIA 的 RPM,會靜默
把玩家的 SINGLE 模式目標從 Google 換成 NVIDIA,即使玩家根本沒打算換。**

這不是風格問題,是「看設定」跟「改動了會影響翻譯行為的東西」被同一個變數混在一起。修法：把
`PendingTranslatorConfig` 整個改成每個 per-provider 欄位（apiKey/modelSelection/customModel/
Custom Provider 的四個欄位)都改成吃明確 `Config.EndPoint` 參數的 getter/setter,不再有「當前
active」這個概念——跟 enabled/priority/rpm 那三個新欄位本來就已經是的那個 pattern 完全一致,只是把
舊的三個（apiKey/model/custom provider fields)也改成同一套。`pending.endpoint` 現在只單純代表
「SINGLE 模式的目標」,不再兼職「Provider Detail 正在看哪個 provider」。`onEndpointChanged()`/
`syncActiveIntoMap()`/`loadActiveFromMap()` 三個方法整個刪掉,不是留著沒用。

## `model_cache_note` 的去留（上一封信你問的)

跟著 Model 選擇一起搬到 `ProviderDetailScreen`,放在 Model/Custom Model 那組欄位後面——不管是內建
provider 的 CycleButton 還是 Custom Provider 的自訂欄位,都會顯示,因為兩種情況這則說明的內容都適
用（換 provider/model 不會自動清快取)。沒有留在 Simple 畫面變成孤兒文字。

## 在地化

新增的 25 個 key（`provider_mode`/`.single`/`.priority`/`.round_robin`/`.automatic`（各含
tooltip)/`manage_providers`/`.note`/`provider.configure`/`.enabled`/`.priority`（含 tooltip)/
`.rpm`（含 tooltip)/`.status`/`.status.ready`/`.rate_limited`/`.cooldown`/`.invalid_key`/
`.cannot_connect`/`.disabled`)全部寫進 10 個語系檔（en/zh_tw/zh_cn/ja/ko/es/fr/ru/de/pt),
`./gradlew runData` 成功、`verify-lang-placeholders` 過（這條專門驗證每個語系的 key 集合跟 %s 數量
完全一致,這次是它第一次真的在守著 25 個新 key)。非中英文語系的翻譯品質我沒有母語者覆核,是我自己
盡力寫的,措辭上如果有不自然的地方要靠玩家回報。

## 開遊戲驗證

跑了一次 `./gradlew runClient`,確認：
1. 新的三個 GUI 類別（`ManageProvidersScreen`/`ProviderDetailScreen`/簡化後的
   `TranslatorConfigScreen`)編譯進 mod 之後,遊戲正常開機到主選單前的貼圖載入階段,**沒有任何
   ERROR/Exception**（`grep -iE "ERROR|Exception"` 乾淨,包含檢查 mixin/class-loading 相關訊息)。
2. Marker 檔案內容維持 `mode=SINGLE;endpoint=GOOGLE_AI_STUDIO`——這次 migration 正確判斷「不需要
   重跑」（上次已經跑過、TOML 值仍然吻合 marker 的宣告),沒有 Y1 那種誤判重跑的 log 出現。

**誠實揭露這次驗證沒有做到的事**：我沒有辦法對著這個環境做滑鼠點擊/GUI 互動——沒有畫面自動化的能
力。所以三個新畫面本身（Manage Providers 的每一行有沒有正確顯示、Provider Detail 的 Status 行有沒
有真的在跳動、Test Connection 按下去有沒有正確清除 AUTH_ERROR、CycleButton/EditBox 有沒有正確排版
不重疊)**完全沒有被實際點開驗證過**,只驗證了「這些類別存在、能被載入、不會讓遊戲在啟動階段崩潰」。
這跟這個檔案家族一貫的「NOT covered by any headless test」揭露一致,但這次連手動點擊測試都沒做——
需要你或使用者實際開一次遊戲、走一遍 Simple → Manage Providers → Provider Detail 才能真正確認。

`./gradlew build` 乾淨、17 個 `tools/verify-*` 全過。

## 下一步

這批做完之後,Router 這個功能在架構上已經完整（routing 核心 + Translator 整合 + migration + GUI +
localization),但完成報告要誠實列出還沒做的部分：新的 headless verify 沒有針對
`inferEmptyPoolFailure`/GUI 本身;沒有任何一次真正的翻譯請求被觸發過（沒進世界)；GUI 沒有手動點擊
驗證過。這些會留到你決定要不要現在收斂寫完成報告,還是要我先補這些洞。
