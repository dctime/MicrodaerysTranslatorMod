# 早期意見：Router 架構（你說不用回，但有兩件事值得在動工前講）

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-024-router-architecture.md

R1/R2/R4 都確實接進設計了，不重複講。**R3 你的反提案比我原本的建議好**，這點要明說：attempt 嚴格循序 → 任一時刻一個 job 最多佔一個 global slot → 用「每次 attempt 各自 acquire/release」就已經滿足那個性質，而配對範圍縮到單次 attempt。我原本說的「acquire 一次跨 N 次 attempt」是把配對窗口拉到最大，剛好是我自己標成「整個改動裡最危險的一句」的那種形狀。你選了風險小的那條，理由也成立。

以下兩件是架構信本身帶出來的，一件是你找到的 bug 比你描述的更嚴重（S1），一件是新設計裡的新風險（S2）。

---

## S1.〔你找到的 statusCode 問題，實際上更嚴重，而且是上一輪合併進去的迴歸〕

你寫「429/5xx 如果剛好 parse 不出例外就會完全不做任何處理」。我去讀了 `handleHttpResponse` 現在的樣子——**不是「不做處理」，是「被當成成功處理」**：

```java
try {
    translatedText = adapter.parseTranslationResponse(responseText);
} catch (Exception e) {
    handleHttpError(resp.statusCode(), text, isScreenShot);   // 只有這裡會走錯誤路徑
    return;
}

resetHttpErrorFlags();                                        // <-- 清掉錯誤抑制旗標
if (!isScreenShot) RETRY_ATTEMPTS.remove(text);               // <-- 把退避次數歸零
...
if (translatedText == null || translatedText.isBlank()) return;  // 靜默結束
```

而 `OpenAiCompatibleAdapter.parseTranslationResponse` 是**刻意不丟例外**的：

```java
if (!root.has("choices")) return null;
```

**所以一個 OpenAI 相容 provider 回 429（body 是 `{"error": {...}}`）的完整後果是：**

1. parser 看到沒有 `choices` → **回 null，不丟例外**；
2. catch 不會進 → `handleHttpError` 不會被呼叫 → **沒有退避、沒有聊天訊息**；
3. `resetHttpErrorFlags()` 照常執行 → **把之前累積的錯誤抑制狀態清掉**；
4. `RETRY_ATTEMPTS.remove(text)` 照常執行 → **指數退避的計數被歸零，等於記錄成「這次成功了」**；
5. `translatedText == null` → 靜默 return。

**結果是被限流的 provider 會被當成健康的**：退避永遠不會啟動，mod 繼續用滿 `MAX_REQUESTS_PER_MINUTE` 的速率打過去，玩家只看到「有些東西翻不出來」。

**而且這是上一輪（`4df36bb`）帶進來的迴歸**：舊的 Gemini/Mistral parser 是直接 `getAsJsonObject()` 硬取，欄位不在就丟例外，所以錯誤路徑靠例外驅動是通的；新的 `OpenAiCompatibleAdapter` 寫成防禦性的回 null（本身是好習慣），**卻靜默拆掉了那條依賴例外的錯誤路徑**。8 個 provider 走這個 adapter。

**建議：這條不要等 Router。** 它影響的是已經合併、已經 push 的程式碼，而 Router 這輪範圍很大、時間會拉長。`if (resp.statusCode() / 100 != 2) { handleHttpError(...); return; }` 放在 parse 之前，是一個獨立的小 commit，跟 Router 沒有耦合，而且之後 Router 的 `FailureClassifier` 本來就要接管這段——先修不會白做，只是把「看狀態碼」這件事提前到現在。

## S2.〔新設計的新風險〕`PROVIDER_POOL_MIGRATED` 這個旗標，撐不過它唯一需要撐過的情境

你的 migration 是「一次性、會真的寫回硬碟」，靠 `PROVIDER_POOL_MIGRATED` 這個 TOML key 記住跑過了。

**但那個旗標本身就是一個新的 TOML key**，而我們上一輪才一起確認過（`ModConfigSpec.correct()` 第 271–282 行）：**舊版不認得的 key 會被無條件刪除。**

```
1. 玩家升級 → migration 跑 → SINGLE + 只有 legacy provider enabled + PROVIDER_POOL_MIGRATED=true
2. 玩家自己進 Manage Providers，開了另外三家、改成 AUTOMATIC     ← 他刻意設定的狀態
3. 因為任何理由降版一次 → correct() 刪掉 provider_mode、所有 enabled/priority/rpm
   ——連同 PROVIDER_POOL_MIGRATED 一起刪掉
4. 再升級回來 → 旗標不見了 → migration「第二次」跑
   → 強制寫回 SINGLE + 只有 legacy provider enabled
```

**第 4 步把玩家在第 2 步刻意做的設定整個蓋掉，而且是靜默的。** 「一次性」這個性質恰好在最需要它的那條路徑上失效——降版正是玩家最可能經歷一次的異常情境，也正是 migration 最不該重跑的時候。

**建議：把「已 migrate」這個事實存在 NeoForge 管不到的地方。** 這個 mod 已經有自己的檔案機制——`TranslationDiskCache` 就寫在 `config/microdaerystranslator/translation_cache.json`。同一個目錄放一個 marker（或直接在那個 JSON 旁邊多一個小檔案），`correct()` 永遠不會碰它，降版也不會消失。

其他兩條路我評估過但不建議：
- **從資料反推「已設定過」**（例如「有任何 provider enabled=true」）——那些 key 降版時一起被刪，反推不出來。
- **接受重跑並記錄**——重跑的後果是靜默覆蓋玩家的設定，不是可接受的降級。

---

## 我可能錯的地方

**S2 的第 3 步我假設玩家真的會降版。** 如果你的判斷是「這個 mod 的玩家幾乎不會降版，不值得為它多一個檔案」——那是合理的產品判斷，我不會硬擋。**但那樣的話，上一輪 commit 訊息裡那張降版行為表就要一併說明「而且再升級回來時，你自己調過的 provider 設定會被重置」**，不能只寫「新區塊會被刪」。兩者選一個，不要兩邊都不處理。

---

架構其餘部分我沒有意見，照你的節奏寫、分批送審。**S1 建議獨立先修**（它影響已經 push 的程式碼），S2 是動工前的一個決定。
