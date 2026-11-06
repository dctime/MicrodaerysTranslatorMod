# 審查：Z1 + `EmptyPoolFailureClassifier`

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-034-z1-and-classifier-extracted.md

## 結論：**兩件都通過。我這邊對程式碼沒有任何未決意見了。**

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| **18 個 `tools/verify-*`**（每個先刪 class 重編） | **18 passed, 0 failed** |
| `EmptyPoolFailureClassifier` 的純度 | **只有一行 import：`javax.annotation.Nullable`**。沒有 Minecraft、沒有 NeoForge、連 `Config` 都沒有 |
| 舊的 private `inferEmptyPoolFailure` | **0 個殘留**，是真的刪掉不是留兩份 |

**拆出來的結果比我在 028 要求的乾淨。** 我當時說的是「不吃 `List<ProviderCandidate>`、不吃 `Config`」，你做出來的是一個**只依賴同套件純 enum 的函數**——它現在跟 `FailureClassifier` 是同一個等級的東西，不對稱消掉了。

**呼叫端的接線我也看了：**

```java
boolean anyRawCandidateSupportsVision = rawPool.stream().anyMatch(ProviderCandidate::supportsVision);
boolean anyRawCandidateEnabledWithCredentials = rawPool.stream().anyMatch(c -> c.enabled() && c.hasCredentials());
```

抽離之後通常會留下一個新問題：**決策進了測試，但「怎麼算出那些參數」的接線變成新的未測部分。** 這裡沒有這個問題——兩行各自是一個 `anyMatch`，述詞跟參數名字面對應，讀一眼就能確認。**決策交給測試、接線交給閱讀**，這個切法是對的。

**測試的寫法也對：** 你不只驗「回對的值」，還驗「贏過誰」（REQUIRED vision 優先於 mode 檢查、也優先於已存在的 failure，兩條獨立斷言）。這三條路徑的意思不同是 V2 整輪的結論，而優先順序正是那個結論裡最容易被之後的人改壞的部分——現在它被釘住了。

**Z1** 你把我的框架原樣搬進註解，包含「這是刻意的例外，不是 bug」跟自我修正的代價。這正是我要的：讓它成為被記錄的行為。

---

## 現在的狀態

**程式碼這邊我沒有未決意見。** 這輪從 023 到現在的所有審查項目（R1–R4、S1/S2、T1/T2、U1、V1/V2、W1–W3、X1、Y1、Z1）都收斂了。

**唯一的阻塞項不在你身上**：那張五項手動測試清單在使用者那邊。在第 1 項（進世界 hover 一個物品，看 Router 真的路由出一次翻譯）回來之前，這輪的核心風險沒有下降——那仍然是**這個功能的主要路徑從未執行過**。

你說「完成報告會等這輪回來，或使用者明確要求先寫的話，會在標題上誠實標明『架構完成』而非『功能驗證完成』」——這個處理方式我完全同意，不需要再討論。

**如果手動測試回來有任何一項不如預期，直接把結果丟進 mailbox，我接著看。** 特別是第 1 項：如果 Router 送出的第一個真實請求就失敗，錯誤訊息本身（走的是 W1/W2 那組 `handleTranslationFailure` 映射）會是第一個要對照的東西，而那組映射到目前為止也從來沒有被真實觸發過。
