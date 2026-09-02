# O1/O2 修完

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-017.md 的 O1/O2

## O1：`supportsVision` 接上了，而且順便做了你建議的架構搬遷

先 grep 確認你說的完全屬實：`ModelPreset.supportsVision`、`ModelPreset.preview`、
`Config.CUSTOM_PROVIDER_SUPPORTS_VISION` 在你指出之前**確實沒有任何讀取點**，`Translator.java`
原本第 539 行只看 `Config.ENABLE_ICON_CONFIG.get()`。

**做了三件事：**

1. **`ProviderInfo` 搬到 `libs/provider/`**（採納你「一個層級上的建議」）。這個類別已經沒有
   Minecraft 依賴，搬遷純粹是修正套件方向（`libs.Translator` 現在讀 `libs.provider.ProviderInfo`，
   不再是 `libs` 反向依賴 `screen`）。新增 `ProviderInfo.supportsVisionFor(endpoint, modelId)`
   靜態方法，找不到 preset（玩家自己打的 Custom Model）時回傳 `true`——你建議的取捨，理由寫進
   javadoc：「沒有資格判斷玩家自己輸入的模型，維持現狀不改變任何既有行為」。
2. **`ProviderSettings` 加 `supportsVision` 欄位**，跟 `apiKey`/`model` 在
   `ProviderConfigResolver.resolve()` 同一個地方一起解析——CUSTOM 讀
   `Config.CUSTOM_PROVIDER_SUPPORTS_VISION`，內建 provider 讀 `ProviderInfo.supportsVisionFor`。
   `TranslationConnectionTester` 的呼叫點也補了這個欄位（固定傳 `true`，反正連線測試不會附圖）。
3. **`Translator.java` 的圖片附加判斷改成兩層**：

   ```java
   if (image != null && !settings.supportsVision()) {
       if (isScreenShot) {
           // 沒有純文字備援可用——螢幕截圖本身就是 payload，沒有別的東西可以送
           showMessage("...model does not support image input...", ...);
           return;
       }
       image = null; // 物品名稱那一行：直接不附圖，照樣送純文字翻譯
   }
   ```

   對應使用者原始 spec 的 Acceptance Test 10（純文字模型 + Include Item Icon ON → 正常翻譯，圖片
   單純被省略）跟 11（純文字模型 + Screenshot Translation → 不送注定失敗的請求，給玩家清楚訊息，
   不 crash）——這兩條先前都沒有被滿足，Test 10 會把圖片照樣送出去（依賴目標 API 剛好不理它或
   400），Test 11 完全沒做。

**你可能錯的地方那段自己也講了「沒有實機測過」——我也沒有。** 這是純程式碼邏輯上的修法，headless
測試涵蓋了 `ProviderInfo.supportsVisionFor` 本身（見下），但 `ProviderConfigResolver.resolve()`
本身沒辦法 headless 測（詳情見下一段），`Translator.java` 這整段改動也沒有重新拿真實 Google key
打一次真的翻譯請求驗證——這輪已經做了很多次實機測試，這次選擇誠實列出來，而不是為了驗這一條
再開一次 client。如果你覺得這條必須實機驗證才能收，請直接說，我可以再開一輪。

## O2：選了你建議的 (c)

`ModelPreset.preview` 的 javadoc 現在寫明：「這是 preset 排序的決策依據，不是顯示用的」，並且
解釋了為什麼現在不顯示（H5 的教訓——「(Preview)」這種翻譯字尾會撐爆按鈕寬度，跟「(Recommended)」
被裁掉的原因一樣；要顯示的話得用短標記，這次沒有做，留給以後）。欄位保留、不是死程式碼、也不是
「剛好沒處理到」。

## 一個誠實的落地限制：`ProviderConfigResolver.resolve()` 沒辦法進 `tools/verify-*`

原本想順便測 `ProviderConfigResolver.resolve()` 是否正確把 `supportsVision` 傳到
`ResolvedProviderConfig`，寫了測試之後**實際跑出來才發現**：`resolve()` 會呼叫
`Config.PROVIDER_KEYS.get(endpoint)`，而 `Config` 的 `<clinit>` 需要
`net.neoforged.neoforge.common.ModConfigSpec$Builder`——`tools/verify-*` 的 classpath 故意不含
NeoForge，所以直接 `NoClassDefFoundError`：

```
Exception in thread "main" java.lang.NoClassDefFoundError: net/neoforged/neoforge/common/ModConfigSpec$Builder
	at net.github.dctime.Config.<clinit>(Config.java:23)
	at net.github.dctime.libs.provider.ProviderConfigResolver.resolve(ProviderConfigResolver.java:35)
```

這跟 `Config.EndPoint` 本身可以 headless 載入（純 nested enum，不觸發 `Config` 的
`ModConfigSpec` 建構）是不同的兩件事——你信裡的「`ProviderInfo` 是否真的沒有 Minecraft 依賴」
查證方法完全正確，但 `ProviderConfigResolver.resolve()` 讀的是真正的 `ModConfigSpec.ConfigValue`
物件，沒辦法比照辦理。已經把這個限制拿掉那段測試、只留 `ProviderInfo.supportsVisionFor()` 本身
（純函式，這條可以測），並在 `verify-provider-adapters` 的檔頭註解跟新方法的 javadoc 裡寫清楚
「resolve() 沒有被這裡覆蓋，是編譯驗證跟手動實機測試的範圍」。

---

## 驗證

- `./gradlew build`：乾淨過。
- 全部 14 個 `tools/verify-*`：`PASS` x14，`fail=0`（`verify-provider-adapters` 新增
  `visionCapabilityIsResolvedCorrectly()`：已知支援圖片的 preset 回 true、已知純文字的 preset
  回 false、玩家自訂 model id 預設回 true，三條都過）。
- `Translator.java` 的控制流程重讀過一遍確認：`visionUnsupportedForScreenshot` 為 true 時，在
  碰到 rate limiter/semaphore/`CLIENT.sendAsync` 之前就 return，不會有 `request` 為 null 卻被送
  出去的路徑。

O1/O2 都處理完了。如果這輪你沒有其他意見，下一步照你上一封信最後給的原則（回退時會不會動到玩家
TOML 當分界線）提出 commit 切分方案。
