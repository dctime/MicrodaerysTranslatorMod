# 審查：V1 / V2 + 回答你問的「值不值得為測試性再拆一層」

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-028-v1-v2.md

## 結論：**V1/V2 都通過。你問的那個拆分——我的答案是「值得，但理由不是覆蓋率」。**

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 17 個 `tools/verify-*`（先刪 class 重編） | **17 passed, 0 failed** |
| V1：`sendAsync` 是否真的在 try 內 | **是**，`imageForAttempt` → `buildTranslationRequest` → `sendAsync(...).whenComplete(...)` 整條在同一個 try |

**你關於「不會雙重清理」的論證我核對過，成立**：`.whenComplete(...)` 是同一個運算式鏈的一部分，`sendAsync` 同步丟例外時 callback 從來沒被註冊，所以 catch 跑清理不可能跟 `whenComplete` 的清理撞在一起。這一點值得寫在那個 catch 上面（現在沒有），因為下一個人看到「try 裡面有一個會註冊 callback 的呼叫、catch 又在做 release」，第一個念頭就會是「這會不會 release 兩次」。

**V1 你選「不信任」那條、而且明說跟上一輪 `computeGap` 的判斷一致**——立場一致比立場本身重要，這樣下一個人才推得出你在別處會怎麼做。

**V2 的三分法我同意**，包括 SINGLE 模式刻意排除在 `NO_ELIGIBLE_PROVIDER` 之外的理由（SINGLE 的「沒 key」根本不走空池，它會真的送出去吃 401）。而且你把「型別上先定義好、訊息留到整合批」講清楚了，這個切法對。

---

## 回答你的問題：值得拆，但理由是**一致性**，不是覆蓋率

你問「把三條判斷邏輯抽成吃 plain boolean 的純函數值不值得」。我的答案是值得，但我不是因為「所有東西都該被測」——那個理由太廉價，而且會讓你以後每次都得問我。

**真正的理由是這個 repo 自己已經有一個一模一樣的東西：`FailureClassifier`。**

- `FailureClassifier`：吃狀態碼／`Throwable`，回 `ProviderFailureType`。**純函數、無 Config 依賴、有 `verify-failure-classifier`。**
- `inferEmptyPoolFailure`：吃 job + rawPool + context，回 **同一個 `ProviderFailureType`**。**不純、吃 `Config`、沒有測試。**

**兩個函式在做同一件事、產出同一個型別，卻一個測得到一個測不到——差別只在其中一個順手收了會拖進 `Config` 的參數。** 那個差別是意外，不是設計。而它需要的資訊其實只有四個純量：

```
(VisionRequirement, boolean anyRawCandidateSupportsVision,
 boolean anyRawCandidateEnabledWithCredentials, @Nullable ProviderFailureType lastFailure)
```

**這不是「再拆一層」，是把四個值先算出來再傳進去**——呼叫端仍然是 `inferEmptyPoolFailure`，只是它變成一個可以直接放進 `verify-failure-classifier` 隔壁的純函數。

**為什麼這個特別值得：** V2 整輪的結論就是「這三條路徑意思完全不同、不能都回 null」。**這段程式碼現在是那個結論唯一的載體**，而它是這輪唯一沒有測試撐著的決策點。分支選錯的後果不是 crash，是玩家收到錯的訊息或收不到訊息——正好是最難被回報的那種。

**但我不會擋。** 如果你評估「五行分支、讀得懂、整合批馬上會再動它」而決定先不拆，那也是合理的——**條件是等整合批把它接到玩家訊息之後補上**，因為那時候它就從「內部分類」變成「使用者看得到的行為」。

---

## 一個很小的順帶

現在 catch 對三種來源（`imageForAttempt`／`buildTranslationRequest`／`sendAsync` 同步丟）一律 `recordFailure(MALFORMED_RESPONSE)`。前兩個貼切，第三個不太對——`RejectedExecutionException` 之類是我們自己這端的基礎設施問題，不是「對方回了個爛回應」。

目前無害（`recordFailure` 只寫 job 層的 `lastFailureType`，不碰 provider 健康度）。**但 V2 剛剛才確立這個型別將來要對應到玩家看得到的訊息**，所以到整合批時，這條會讓玩家看到一句在講回應格式的訊息，而真正的原因跟回應無關。整合批處理即可，現在不用動。

---

**下一步：** 開始 `Translator` 整合。那批是這輪第一次讓 Router 從死碼變成有呼叫點，我會特別看 `IN_FLIGHT` 的加入／移除跟 `result` future 的完成是不是在所有路徑上都配對——那是 V1 漏掉會直接體現的地方。
