# 提案 #002：自訂 Config GUI 取代 NeoForge ConfigurationScreen

**From:** 正方
**To:** 反方
**時間:** 2026-09-01

## 要解決的具體症狀

玩家打開 Mods → Microdaery's Translator → Config，看到的是 NeoForge 自動產生的
`ConfigurationScreen`：左邊欄位顯示原始 key 被截斷（`microdaerystranslator.confi...`），
`endpoint`/`model_name`/`target_language`/`timeout_duration`/`max_requests_per_minute`
全部要手動打字，新手完全不知道該填什麼。目標：換成分「Simple / Advanced」兩層的自訂畫面，
新手只填 API Key 就能用，進階設定收在 Advanced Settings 裡。

## 牽涉的檔案

**新增：**
- `screen/TranslatorConfigScreen.java` — Simple Settings（Service/Model/API Key/Language/Feature toggles）
- `screen/TranslatorAdvancedConfigScreen.java` — Advanced Settings（icon/pretranslate/prompt/animation/timeout/RPM/cache）
- `screen/PendingTranslatorConfig.java` — 兩個畫面共用的 pending state（Cancel/Done 語意見下）
- `screen/ProviderInfo.java`、`screen/ModelPreset.java` — endpoint/model 的顯示名稱與 preset 定義
- `screen/CustomPromptEditScreen.java` — multiline prompt 編輯（用 vanilla `MultiLineEditBox`）
- `events/OptionsScreenButtonInjector.java` — 監聽 `ScreenEvent.Init.Post`，偵測 `OptionsScreen` 時掛一顆入口按鈕
- `libs/TranslationConnectionTester.java` — Test Connection 的三個 provider 請求（reuse `JsonUtil` 組 body）

**修改：**
- `MicrodaerysTranslatorClient.java` — `IConfigScreenFactory` 從 `ConfigurationScreen::new` 換成
  `(container, parent) -> new TranslatorConfigScreen(parent)`（signature 已用 sources jar 核對，見下方確認清單）
- `Config.java` — 加 `public static void save()`（包一層 `SPEC.save()`，因為 `SPEC` 目前是 package-private，
  screen 在別的 package 存不到）；其餘 key/預設值完全不動
- `TargetLanguage.java` — 加 `KNOWN_CODES`（固定順序的 `List<String>`），給 CycleButton 用；
  `displayName()`/`isAlreadyInTargetLanguage()` 完全不動
- `Translator.java` — 只加兩個新方法，不動既有邏輯：
  - `getCacheSize()`：回傳 `translationCache.size()`
  - `clearCache(boolean showMessage)`：見下方「最脆弱假設」第 3 點
- `datagen/LanguageProvider.java` — 補新畫面用到的 lang key（en_us + zh_tw）

**不動：** `PromptTemplates.java`、`JsonUtil.java`、`TranslationDiskCache.java`、`RateLimiter.java`、
`RetryPolicy.java`、`OfficialTranslationLookup.java`、所有 Jade/FTB/Advancements/Screenshot 整合。

## 我自己覺得最脆弱的假設

**1. `resolveTargetLanguage()`/`keyFor()` 單一真相來源不被破壞。**
GUI 全程只在 pending state 裡操作 `selectedFollowGameLanguage`/`selectedTargetLanguage` 這些欄位，
按 Done 才一次寫回 `Config.FOLLOW_GAME_LANGUAGE`/`Config.TARGET_LANGUAGE`。Translator 內部完全沒改，
所以 `resolveTargetLanguage()`/`keyFor()` 還是唯一真相來源。我沒把握的地方：如果玩家在 GUI 開著、
沒按 Done 之前就切換遊戲語言（理論上 GUI 開著時進不去語言選單，但如果之後有其他方式能在背景改變
`getLanguageManager().getSelected()`），pending state 不會即時反映——這應該是可接受的，因為 Done 前
本來就不該有任何副作用。

**2. Test Connection 是一次性事件，但沒有掉進「安全網失效」的坑。**
我知道你會盯這個：一次性呼叫點如果假裝有下一次 tick 補救，會直接壞掉（#16 的教訓）。
但 Test Connection 刻意設計成完全獨立的呼叫路徑，不共用 `IN_FLIGHT`/`CONCURRENCY_LIMIT`/
`REQUEST_RATE_LIMITER`/`translationCache` 任何一個：
  - 不寫入 `translationCache`（不需要 `cacheDirty`）
  - 不佔用 `CONCURRENCY_LIMIT` 的 4 個併發名額
  - 不受 `REQUEST_RATE_LIMITER`（RPM 節流器）限制，也不會佔用它的配額
  - 失敗不會排入 `RETRY_AFTER`/`RETRY_ATTEMPTS` 退避佇列
  用的是全新的 `HttpClient`（或直接 new 一個短 timeout 的），送一個「Reply with OK only.」的極小請求，
  成功/失敗都直接更新畫面上的 Status 文字，不會有「這次沒搶到、指望下一 tick 自動重試」的隱性假設——
  因為根本沒有假設會重試，按鈕本身就是唯一觸發點，按下去 disable、结果出來才重新 enable。
  我沒把握的地方：如果按下 Test Connection 後玩家立刻關掉 GUI（Cancel/ESC），非同步的
  `whenComplete` 回呼還在飛，回呼裡用 `Minecraft.getInstance().execute(...)` 切回主執行緒更新畫面元件時，
  screen 可能已經不是目前顯示的 screen 了——我會在回呼裡判斷
  `Minecraft.getInstance().screen == thisScreenInstance` 才更新 widget，避免對已關閉畫面的元件操作，
  但這個 race 我沒辦法在 headless 環境驗證。

**3. `Translator.clearCache()` 目前綁 player context，GUI 情境下會靜默不落地。**
現有 `clearCache()`：`translationCache.isEmpty()` 直接 return；`player == null` 直接 return（連
`cacheDirty = true` 都不會設）。如果玩家在**主選單**（還沒進世界，`player == null`）就打開設定畫面按
「Clear Translation Cache」，現有邏輯會整個不執行清除——這不只是訊息不顯示的問題，是清除操作本身
被跳過。我打算拆成：
```java
public static void clearCache(boolean showMessage) {
    if (translationCache.isEmpty()) return;
    translationCache.clear();
    cacheDirty = true;
    if (showMessage) {
        Player player = Minecraft.getInstance().player;
        if (player != null) { ...原本兩行 sendSystemMessage... }
    }
}
public static void clearCache() { clearCache(true); }
```
這樣 `KeyMapping`/既有呼叫點行為完全不變（還是走 `clearCache()` → `showMessage=true`），
GUI 按鈕呼叫 `clearCache(true)` 一樣會顯示訊息，但清除動作本身不再被 `player == null` 卡住。
我沒把握的地方：這算不算「合理重構」的範圍——你在規則 3 說漏加 `cacheDirty = true` 是地雷，
我這個改動剛好是把一個「本來會漏設 `cacheDirty`」的邊界情況修掉，但畢竟動了 `Translator.java`
的既有方法簽章（雖然保留舊 overload 向下相容）。

**4.（次要，不到「最脆弱」但一併說明）Options Screen 按鈕注入位置。**
用 `ScreenEvent.Init.Post`（game bus，不是 mixin），判斷 `event.getScreen() instanceof OptionsScreen`。
不硬編 x/y：掃描 `event.getListenersList()` 裡所有 `AbstractWidget`，取最底部那顆（vanilla 佈局裡是
`Done` 按鈕）的 top-Y，和它上面一群按鈕的 bottom-Y 之間的空隙，只有空隙足夠放下一顆按鈕高度時才
`event.addListener(...)`加進去；空隙不夠就直接不加，不 fallback、不硬擠。全程包在 try/catch 裡，
出例外只 log warn，不可能讓 Options 畫面崩潰，Mods→Config 入口不受影響（那是獨立路徑）。
這部分因為沒辦法 headless 驗證實際渲染位置，属于「理論上安全、視覺效果沒手動測過」。

## NeoForge/MC 1.21.1 API 確認清單（已對照 sources jar，非猜測）

- `IConfigScreenFactory.createScreen(ModContainer, Screen)` — 這是我會用的建構子形狀
- `ConfigurationScreen extends OptionsSubScreen`，我打算讓 `TranslatorConfigScreen`/
  `TranslatorAdvancedConfigScreen` 一樣繼承 `OptionsSubScreen`，複用 `layout`/`OptionsList`/
  `addOptions()`/`onClose()` 這套現成骨架，不是憑空刻一個 render loop
- `OptionsList.addSmall(AbstractWidget, @Nullable AbstractWidget)` 吃任意 `AbstractWidget`
  （不限定 `OptionInstance`），`StringWidget` 當左欄標籤、實際控制項當右欄，跟 NeoForge 自己的
  `ConfigurationSectionScreen` 用的是同一招
- `CycleButton.builder(Function<T,Component>).withValues(List<T>).withInitialValue(T).create(...)`
  支援任意 `T`（不限 enum），Service/Model/Target Language 都靠這個
- `ScreenEvent.Init.Post.addListener(GuiEventListener)` — game bus 事件，非 mixin
- `ModConfigSpec.save()` 是 public method，`ConfigurationSectionScreen.onClose()` 本身也是呼叫
  `context.modSpec.save()`（也就是 `Config.SPEC.save()`）來落地，我會走一樣的路徑
- `EditBox.setFormatter(BiFunction<String,Integer,FormattedCharSequence>)` 用來做 API Key 遮蔽
  （render 用馬賽克字元，`getValue()`/`insertText()` 操作的還是真實字串）；已知限制：游標高亮的
  寬度計算是用真實字元寬度算的，不是用遮蔽字元寬度算的，非等寬字型下游標線可能有幾像素的視覺誤差，
  純美觀問題，不影響實際輸入的字串
- `Minecraft.getInstance().keyboardHandler.getClipboard()/setClipboard(String)` 給 Paste 按鈕用

## Test Connection 不洩漏 API Key 的具體做法

- 所有 log 只印 `endpoint`/`model`/HTTP status code，絕不印 request header 或 body
- 錯誤訊息用 switch-on-status-code 對應成固定文案（沿用 `Translator.handleHttpError` 同一套分類：
  403→Invalid API Key、429→Rate limited、connect failure→Cannot connect，其餘→HTTP {code}），
  不會把 provider 回傳的原始 JSON body 顯示在畫面或塞進錯誤訊息字串

準備好了嗎？這個提案通過後我才開始寫程式；如果你覺得第 2、3 點的方向不對，麻煩直接說要怎麼改。
