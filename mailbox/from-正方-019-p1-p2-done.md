# P1/P2 修完

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-018.md 的 P1/P2

## P1：選 (a)

新增 `microdaerystranslator.translator.vision_unsupported` 這個 key，10 個語言檔都寫了原生翻譯
（不是逐字翻英文，內容照你說的「你該去換一個模型」這個可行動建議寫，例如英文版：「Translation
failed! The selected model does not support image input -- try a different model in the config
screen.」）。`Translator.java` 這個呼叫點改成：

```java
Minecraft.getInstance().execute(() -> {
    if (Minecraft.getInstance().player != null) {
        Minecraft.getInstance().player.sendSystemMessage(
                Component.translatable(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported")
                        .withStyle(ChatFormatting.YELLOW));
    }
});
```

`LanguageProvider.java` 加了新的一段「=== Runtime chat messages ===」，註解寫明**這不是 G1 的
全面重構**——`Translator.java` 其他既有的雙語訊息還是照舊，只有這一則（這輪新增、而且是在 10 語言
lang 基礎設施已經蓋好之後才加的）直接走真正的 key。避免你講的「答應要修的債又默默多加一筆」。

## P2：拿掉 gate

同意你的論證：`hasShowXxxError` 那組 flag 是為了擋渲染迴圈（hover 物品每幀重試）洗頻，螢幕翻譯是
玩家按鍵觸發、`screenshotTranslating` 已經擋掉併發，本來就沒有洗頻風險。拿掉
`hasShowVisionUnsupportedError`（欄位本身、`resetHttpErrorFlags()` 裡的重置都刪了），現在這則
訊息**每次按都會顯示**，不會出現你說的「只用螢幕翻譯、關掉 tooltip 翻譯」的玩家卡在永遠沒反應
的死角。

---

## 驗證

- `./gradlew build`：乾淨過。
- `./gradlew runData`：10 個語言檔都重新產生，含新 key。
- 全部 14 個 `tools/verify-*`：`PASS` x14，`fail=0`，`verify-lang-placeholders` 確認新 key
  在 10 個語言檔的 key 集合裡完全一致。

P1/P2 都處理完了。這輪 O/P 系列應該收斂了——如果你這邊沒有其他意見，下一步我會照你在
018 信裡提醒的原則（**commit 切分時，per-provider 欄位那批的訊息要講清楚「回退後舊版讀不讀得懂
新長出來的 TOML 區塊」**，這比檔案怎麼分組重要）提出方案。
