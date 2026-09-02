# 審查：O1 / O2

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-018-o1-o2-done.md

## 結論：**O1/O2 的修法通過，控制流程我逐行讀過是對的。** 兩個新意見（P1/P2），都落在你新加的那一則聊天訊息上。

**先直接回答你的問題：不需要為了這條再開一次 client。** 這次改動是一個純條件判斷，而唯一有「卡死」風險的地方（螢幕截圖路徑 return 的時機）我讀碼確認過——`return` 發生在 `if (isScreenShot) screenshotTranslating = true;` **之前**，所以不會留下一個永遠清不掉的 busy flag。這條靠讀碼可以定案，不用實機。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 14 個 `tools/verify-*`（先刪 class 重編） | **14 passed, 0 failed** |
| `screen/ProviderInfo.java` 是否真的搬走 | 是，`screen/` 底下只剩 5 個畫面相關檔案 |
| 螢幕截圖路徑的 return 時機 | 正確，在 `screenshotTranslating = true` 之前，且註解就寫在那一行旁邊 |
| `hasShowVisionUnsupportedError` 有沒有被 reset | **有**，已經加進 `resetHttpErrorFlags()` 第 836 行 |

## 做得對的

1. **你先 grep 確認我 O1 說的屬實才動手**，而不是直接照做。
2. **`ProviderConfigResolver.resolve()` 沒辦法 headless 測——你是寫了測試、跑了、拿到 `NoClassDefFoundError` 才知道的**，而且貼了完整堆疊。更重要的是你分辨出「`Config.EndPoint` 是 nested enum、載入它不會觸發 `Config` 的 `<clinit>`」跟「`resolve()` 讀的是真的 `ModConfigSpec.ConfigValue`」是兩件事——這個區分很細，而且直接決定了哪些東西進得了 verify、哪些進不了。**發現測不動之後把測試移除並在檔頭寫明「這段是編譯驗證＋手動實機的範圍」，比留一個測不到重點的測試好。**
3. **兩個呼叫端給不同待遇的理由寫在程式碼裡**（tooltip 有純文字備援 → 靜默降級；螢幕截圖圖片就是 payload → 不送注定失敗的請求），而不是只寫「照 spec 做」。
4. O2 選 (c) 並把 H5 的教訓（`(Preview)` 這種字尾會撐爆按鈕，跟 `(Recommended)` 同一個原因）寫進 javadoc——把一次取捨的理由留給後人，正確。

---

## P1.〔請至少記錄，建議直接改〕你新加的是一則**雙語**聊天訊息

```java
showMessage(
        "Translation failed! The selected model does not support image input.",
        "無法翻譯! 目前選擇的模型不支援圖片輸入",
        ...
```

使用者的原則是「不應該中文旁邊還跟著英文，應該根據玩家目前語言只顯示一種」，而且他明講**「整個 mod 都要改」**；你在 009 也答應另開提案處理 `Translator` 的聊天訊息（G1）。

**這則是在那個承諾之後新增的，而且是在 10 個語言的 lang 基礎設施已經建好之後。** 具體後果：韓文/俄文/德文玩家按下螢幕翻譯，收到的是**英文 + 繁體中文**兩行——而這則訊息是螢幕截圖路徑**唯一**的回饋（請求根本沒送出去），所以它比其他錯誤訊息更需要玩家看得懂。

**兩條路，我不硬性要求哪一條，但一定要選一條：**

- **(a) 現在就用 `Component.translatable`。** 成本是 `showMessage(en, zh, ...)` 這個雙字串簽章要動（或加一個 overload），加 1 個 key × 10 個語言檔——而那 10 個檔你這輪本來就在生成。**我傾向這條**，因為這則訊息的內容是「你該去換一個模型」的可行動建議，而且它是那條路徑上唯一的輸出。
- **(b) 維持雙語，但把這一則明確加進 G1 提案的範圍清單。** 反正 G1 會把所有訊息重構一次，多一則不算多——**條件是它被寫下來**。現在的狀態是「答應要修的債，又默默多加了一筆」。

## P2.〔建議直接拿掉那個 flag〕一次性抑制在這條路徑上會製造「完全沒有反應」

`hasShowVisionUnsupportedError` 你有加進 `resetHttpErrorFlags()`，這點我查過了，比我原本擔心的好。**但 `resetHttpErrorFlags()` 只在 `handleHttpResponse()` 裡被呼叫**——也就是「某次請求真的送出去而且拿到可解析的回應」才會清。

而這條路徑**根本不送請求**。所以清除只能靠**別的**翻譯成功來觸發。

**會在什麼情況下壞掉：** 一個把 `enable_tooltip_translation` 關掉、只用螢幕翻譯的玩家（這是設定裡明擺著的組合），選了純文字模型：

1. 按螢幕翻譯 → 看到訊息一次 → flag 設起來。
2. 之後**沒有任何其他翻譯**會發生（tooltip 關了），所以 `handleHttpResponse()` 永遠不會被呼叫，flag 永遠不會清。
3. 之後每一次按螢幕翻譯 → **完全沒有反應**：沒有訊息、沒有請求、什麼都沒有。

這正是你在那段註解裡寫「until the player figures out why nothing happens」要避免的東西，只是換成了第二次以後才發生。

**建議：這則訊息不要 gate。** 那幾個 `hasShowXxxError` flag 存在的理由是**擋 tooltip 每幀重試造成的洗頻**——那是渲染迴圈驅動的。**螢幕翻譯是玩家主動按鍵觸發的，一次按鍵最多一則訊息，本來就沒有洗頻風險**（而且 `screenshotTranslating` 已經擋掉了併發重複）。

拿掉 gate 之後：每按一次、講一次，玩家立刻知道為什麼沒反應。**這比「加更多 reset 觸發點」乾淨**——因為真正該觸發 reset 的事件其實是「玩家換了 provider/model」，那個事件跟 `handleHttpResponse` 沒有任何關係，硬要接會是另一條跨模組的耦合。

---

## 我可能錯的地方

**P2 我沒有實機驗證「只開螢幕翻譯、關掉 tooltip 翻譯」這個組合。** 推論是從 `resetHttpErrorFlags()` 的唯一呼叫點（`handleHttpResponse`）反推的。如果你發現還有別的地方會清那些 flag，這條的嚴重性就降低——但「主動觸發的動作不需要一次性抑制」這個論點本身不受影響，拿掉 gate 仍然是比較好的設計。

---

## 下一步

P1 選 (a) 或 (b)、P2 建議拿掉 gate。這兩件都在同一個 `showMessage` 呼叫附近，一起改完就行。

之後就是 commit 切分方案，我照上一封講的原則（**回退時會不會動到玩家的 TOML 當第一條分界線**）看。提醒一下這輪特別要小心的地方：`Config` 的 per-provider 欄位一旦被玩家的遊戲寫進 TOML，回退程式碼**不會**把 TOML 改回去——所以那一批的 commit message 需要說清楚「回退後舊版讀不讀得懂新長出來的區塊」，這比檔案怎麼分組重要得多。
