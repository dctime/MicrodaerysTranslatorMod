# 審查：S1 / S2

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-025-s1-s2-resolved.md

## 結論：**S1 的修法正確、S2 的決定正確。** 但工作區跟你信裡的描述有兩處對不上（T1/T2），其中一個是預設值被改掉了、信裡沒提。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 14 個 `tools/verify-*`（先刪 class 重編） | **14 passed, 0 failed** |
| S1 的 status code 檢查是否在正確位置 | **是**，在 `parseTranslationResponse` 之前，註解還點名了引入迴歸的 commit `4df36bb` |
| `ProviderMigrationMarker` 是否 Minecraft-free | **是**，`net.minecraft`/`neoforged` 出現 0 次 |

S1 的註解寫得好——它不只說「檢查狀態碼」，還把「為什麼以前不需要、什麼時候開始需要」寫進去了（parser 從丟例外改成回 null）。下一個看到這段的人不會想把它當成多餘的防禦。

**S2 你沒有拿「玩家會不會降版」來討價還價，直接做掉**——這個判斷對。那種「機率可能很低」的風險，如果修法成本接近零，爭論成本本身就比修它貴。

---

## T1.〔對不上〕你說 S1 是「獨立 commit（未推）」，但 `git log` 裡沒有那個 commit

```
4df36bb 翻譯供應商從 3 個擴充到 11 個，Provider adapter 架構重構   ← 仍是 HEAD
```

`git status` 顯示 `Translator.java` 是 **modified、未 commit**，而且旁邊躺著同樣未 commit 的 `Config.java`（見 T2）。

**所以現在的實際狀態是：S1 的修正跟 Router 的在製品在同一個髒工作區裡。** 我建議 S1 獨立，正是為了讓它跟 Router 解耦；照現在的樣子，之後一次 commit 下去，這個修正就會被埋進 Router 那個大 commit 裡——而它是一個影響**已經 push 出去的程式碼**的行為修正，值得自己一個 commit、自己一行訊息。

不用解釋為什麼（很可能只是「已改好待 commit」寫成了「已 commit」）。**要的是：在開始長出 Router 的大量檔案之前，先把這一個檔案 commit 掉。** 拖愈久愈難拆。

## T2.〔信裡完全沒提〕`Config.java` 的兩個**預設值**被改了

```diff
-            .defineEnum(ENDPOINT_CONFIG_PATH, EndPoint.MISTRAL);
+            .defineEnum(ENDPOINT_CONFIG_PATH, EndPoint.GOOGLE_AI_STUDIO);

-            .define(MODEL_NAME_PATH, "mistral-small-latest");
+            .define(MODEL_NAME_PATH, "gemini-3.5-flash-lite");
```

**先講結論：這個改動本身是合理的**，我查過 `ProviderInfo` 的 Google preset，`gemini-3.5-flash-lite` 正是第一順位（★ 推薦）那一個。所以新玩家第一次開設定畫面會看到「★ Gemini 3.5 Flash Lite」，而不是掉進「Custom...」——這剛好修掉一個 E3 類型的體驗問題。既有玩家不受影響（`ModConfigSpec` 只在 key 不存在時才寫預設值，他們的 TOML 早就有 `endpoint` 了）。

**但有三件事要處理：**

1. **這是產品決策，不是 Router 的水管工程。** 它改變**每一個新安裝**的預設行為。信裡完全沒提，我是從 `git diff` 發現的——這是 D4 之後第三次「工作區有信裡沒描述的東西」。
2. **我沒辦法從程式碼判斷它是刻意的還是測試殘留。** 你這輪一直在拿 Google 的真實 key 做實機測試，把預設值改成 Google 正好是那種「測試時順手改、然後忘記」的東西。**如果是刻意的，它該有自己的 commit 跟一行理由；如果是殘留，現在還來得及。** 請明確回答一句是哪一種。
3. **跟 `4df36bb` 的降版行為表核對過了，目前沒有矛盾。** 那張表寫「降版時 endpoint 被重設成 `MISTRAL`」——那講的是**舊 jar 的**預設值，舊 jar 沒變，所以表還是對的。但這是巧合不是設計：**以後任何人再動這兩個預設值之前，都要回去看那張表一眼**，因為那張表現在是玩家降版時唯一的說明書。這句建議寫進那兩行的註解裡。

---

## 一個 S1 帶來的下游影響（不需要現在處理）

status code 檢查修好之後，**以前靜默失敗的情況現在會真的跳訊息了**——走的是 `handleHttpError` 的 default 分支，也就是那句雙語的「Translation failed! HTTP Status Code: N / 翻譯失敗! HTTP 回傳碼: N」。

這是好事（本來就該讓玩家知道），但它同時代表 **G1 的可見度變高了**：以前這些訊息很少出現，玩家不太會注意到中英夾雜；修好之後 8 個 OpenAI 相容 provider 的每一次 4xx/5xx 都會跳。**把這一點加進 G1 提案的動機說明**——它從「風格問題」變成「常常看得到的風格問題」。

---

## 我可能錯的地方

**T2 第 2 點我沒有辦法自己判斷意圖**，所以那是一個問題不是指控。如果是刻意的，我沒有意見——`gemini-3.5-flash-lite` 對齊 ★ preset 是合理的選擇。

---

**下一步：** T1 先把 S1 單獨 commit 掉，T2 回我一句「刻意／殘留」。Router 那批照你的節奏寫完再整批送審，我不需要中間再看。
