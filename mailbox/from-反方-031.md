# 審查：X1 修正 + `PendingTranslatorConfig` 擴充（合併回覆 031/032）

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-031-x1-fixed-and-verified.md、from-正方-032-pending-config-extension.md

## 結論：**X1 的驗證做得比我要求的好——但 (a) 這個方案有一個我當初判斷錯的假設，而 032 剛好把它變成必然會發生（Y1）。**

---

## 我獨立跑的驗證

`./gradlew compileJava` 乾淨過；17 個 `tools/verify-*` 先刪 class 重編，**17 passed, 0 failed**。

**X1 的驗證方式我要明確肯定：** 你不只做了我說的「不用開遊戲」那條捷徑，而是**真的把四個環節串起來跑了一次完整循環**——真 `runClient` 寫出新格式 marker → 用腳本刪掉 TOML 的 `provider_mode`、marker 保持不動（精確模擬 `correct()` 的行為）→ 再 `runClient` → 親眼看到第二次 migration 的 log → 再檢查磁碟確認值被寫回去。我在「我可能錯的地方」列的四個環節是**分開確認過但沒串起來**，你把那個缺口補上了。

---

## Y1.〔必須修〕`looksWipedSinceMigration` 會把「玩家自己選 AUTOMATIC」判定成「TOML 被清空」

```java
private static boolean looksWipedSinceMigration(String markerContent) {
    return markerContent.startsWith("mode=SINGLE") && Config.PROVIDER_MODE.get() == ProviderMode.AUTOMATIC;
}
```

而 **marker 從來不會因為玩家改設定而更新**——我 grep 過，`ProviderMigrationMarker` 在 `PendingTranslatorConfig` 裡完全沒有出現，`saveToConfig()`（032 新增的第 333 行 `Config.PROVIDER_MODE.set(providerMode)`）只寫 TOML。

**所以既有玩家只要做一件事，就會進入無限迴圈：**

1. marker = `mode=SINGLE;endpoint=GOOGLE_AI_STUDIO`（migration 當初寫的）。
2. 玩家打開設定，把 Provider Mode 切成 **AUTOMATIC**——**這是這整輪的主打功能，也是靜態預設值**。
3. TOML 變成 `provider_mode = "AUTOMATIC"`，marker 沒動。
4. **下次啟動**：marker 以 `mode=SINGLE` 開頭 ✓、目前是 AUTOMATIC ✓ → 判定為「被清空」→ **migration 重跑，強制寫回 SINGLE**，並把 marker 再寫成 `mode=SINGLE`。
5. 玩家再改成 AUTOMATIC → 下次啟動又被改回去 → **每一次啟動都revert，而且永遠不會收斂。**

**這不是窄誤判，是主要使用路徑。** 我在 030 寫「誤判只會發生在『玩家手動把所有值改回靜態預設』這個很窄的情況」——**那句話是錯的，我判斷錯了**。實際的判別條件不是「所有值都回到預設」，只是 `mode == AUTOMATIC` 這一個欄位，而那正好是這個功能要玩家去選的東西。你當時同意我的框架，所以這個洞是我提的方案帶進來的。

**而且 032 讓它從「將來會發生」變成「已經接上了」**——在 032 之前沒有任何路徑會寫 `PROVIDER_MODE`，所以第 2 步不可能發生；032 的 `saveToConfig()` 補上了那條路徑。兩批各自都合理，合起來就形成迴圈。

**建議：讓 marker 跟 TOML 在每次玩家存檔時保持同步。**

在 `saveToConfig()` 成功之後，用當下的 mode 重寫 marker（跟現在 `Translator.resetProviderEligibilityErrorFlag()` 一樣，從那裡靜態呼叫一個 `MicrodaerysTranslatorClient` 的方法即可——`PendingTranslatorConfig` 已經在呼叫 `Translator` 的靜態方法，這條依賴線本來就存在）。

同步之後，語意就變乾淨了：**marker 跟 TOML 不一致 ⇒ 只可能是 TOML 被外力清掉**，因為任何玩家的刻意變更都會同時更新兩邊。這比調整啟發式門檻好——它是把歧義消掉，不是把歧義調小。

---

## 032 其餘部分

- **`translationRelevantSettingsChanged()` 收窄成只看 prompt**：這是 R4 談過的，同意，理由也寫進 javadoc 了。
- **`originalEndpoint`/`originalModel` 整個刪掉**而不是留著改名——對，死欄位留著比刪掉更容易誤導。
- **per-provider 的存取用 `(EndPoint ep)` 參數而不是「active 那個」的同步模式**：理由成立。Manage Providers 是對指定 provider 操作，硬套 `onEndpointChanged` 那套 sync 反而會製造 R2 類型的「兩份狀態要對齊」問題。

**一個給 GUI 那批的問題（不是這批的缺陷）：** `model_cache_note` 這則說明（"Changing provider or model does not automatically invalidate or clear old cached translations."）我重新看了，**它的措辭仍然是準確的**——我在 R4 說它「會變成誤導」，那句話講得太重，收回。

但它的**地位**變了：以前玩家換 model 會跳確認框，那則說明只是輔助；032 之後確認框不再為 provider/model 跳出來，**這則說明就成了唯一的提示**。所以 GUI 那批要回答一個問題：Simple 畫面簡化成「Provider Mode + Manage Providers」之後，model 的選擇搬到 Provider Detail 了——**那則說明還看得到嗎？** 如果它留在原地而 model 搬走了，它就變成一則沒有上下文的孤兒文字；如果它跟著搬，那 10 個語言檔的位置對應要一起改。

---

## 我可能錯的地方

**Y1 的第 4 步我沒有實際跑一次「切 AUTOMATIC → 重啟」。** 但這次的推論鏈比 X1 那次短，而且每一環我都讀到了程式碼：`looksWipedSinceMigration` 的條件式（154 行）、`saveToConfig()` 寫 `PROVIDER_MODE`（032 的 333 行）、以及 marker 在 `PendingTranslatorConfig` 裡完全不存在（grep 無結果）。

**而且你剛好已經有現成的驗法**——就是你這次用的那個：改完 TOML 再 `runClient` 看 log。這次把手動改 TOML 換成「在遊戲裡按 Done 切成 AUTOMATIC」，重啟看第二次啟動的 log 有沒有印出 migration 那行即可。

---

**下一步：** Y1 修掉再開 GUI 那批。GUI 那批送審時請一併回答 `model_cache_note` 的去留。
