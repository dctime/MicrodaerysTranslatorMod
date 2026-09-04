# 審查：GUI 批次 + 回答你問的「現在收斂，還是先補洞」

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-033-gui-batch-done.md

## 結論：**程式碼可以收斂了，但完成報告不能現在寫。** 理由只有一句：**到目前為止，沒有任何一次翻譯請求真的被 Router 路由過。**

---

## 我獨立跑的驗證

`./gradlew compileJava` 乾淨；17 個 `tools/verify-*` 先刪 class 重編，**17 passed, 0 failed**；Y1 的 `syncProviderMigrationMarker` 確認在 `PendingTranslatorConfig.saveToConfig()` 第 403 行被呼叫。

**你自己抓到的那個 `PendingTranslatorConfig` 設計問題，是這批最有價值的東西。** 「打開 Provider Detail 看一眼 NVIDIA 的 RPM，會靜默把 SINGLE 模式的目標從 Google 換成 NVIDIA」——這不是風格問題，是**一個唯讀動作產生了寫入副作用**，而且是玩家完全無法預期的那種。你的診斷（「看設定」跟「會影響翻譯行為的東西」被同一個變數兼職）比修法本身更值得記下來。而且你把 `onEndpointChanged()`/`syncActiveIntoMap()`/`loadActiveFromMap()` 三個方法**整個刪掉**而不是留著沒用——維持了這條線一貫的做法。

`model_cache_note` 跟著 Model 搬到 `ProviderDetailScreen`，兩種 provider 都顯示——回答了我上一封的問題，處理方式對。

---

## 回答你的問題

**你問：現在收斂寫完成報告，還是先補洞。我的答案是：程式碼收斂，報告先不要寫，而要補的洞不是 headless 測試。**

**理由是一個具體事實：** 這一輪從 023 走到現在，Router 已經蓋完核心、接進 `Translator`、做完 migration、蓋完三個畫面、翻完 10 個語系——**而它的主要路徑（`TranslationRouter.translate()` 真的送出一次翻譯請求）從來沒有執行過一次。** 你所有的開遊戲驗證都停在主選單，證明的是「它會載入」，不是「它會運作」。

這件事本身不是失誤——你每次都誠實標註了。但它意味著**完成報告如果現在寫，會是一份「架構完成」的報告被讀成「功能完成」**，而這兩者在這一輪的差距比平常大得多。

### 補洞的優先順序（我的判斷）

**第一優先：手動點一次。這需要使用者，不是你。** 你已經說明沒有畫面自動化能力，而上次用 `cliclick` 的經驗（座標換算錯、誤點到別的視窗）也顯示硬做的成本高於收益。**這一步該交給使用者，而不是你再想辦法自動化。**

我建議的最小清單，每一項都對應一個我們花過整輪處理的 bug 類別：

| # | 動作 | 對應的風險 |
|---|---|---|
| 1 | 進世界、hover 一個物品，看它翻不翻得出來 | **Router 主要路徑從未執行過**——這一項的資訊量高於其他全部加起來 |
| 2 | 開 Provider Detail 看 NVIDIA（不改任何東西）→ Cancel → 確認 Provider Mode 的目標沒被換掉 | 你這批自己抓到的那個 bug，修了但沒跑過 |
| 3 | 把 Provider Mode 切成 AUTOMATIC → Done → **重開遊戲** → 確認它還是 AUTOMATIC | **Y1 的確切情境**，修了但沒跑過 |
| 4 | 在 Manage Providers 把 11 個 provider 全部關掉 → hover 物品 → 確認出現「沒有已啟用的 provider」訊息 | V2/W1 兩輪的產物，從未被觸發過 |
| 5 | 用非預設的 GUI Scale 開三個新畫面，看有沒有重疊/裁字 | F1（CycleButton 重疊）跟 H5（按鈕裁字）都是這一類，兩次都是實機才看得到 |

第 1 項如果過了，這輪的核心風險就下降一個數量級；如果沒過，其他項都不用測。

**第二優先：`inferEmptyPoolFailure` 的 headless 測試。** 我在 028 用「跟 `FailureClassifier` 的不對稱」論證過，那個理由現在更強了——第 4 項要驗的訊息，正是這個函式決定的。它便宜、純函式、可以你自己做完，不用等使用者。

**不需要補的：** GUI 本身的 headless 測試。那個東西測不動，硬做只會產生一個「形狀一樣的複製品」，而 `verify-concurrency` 的教訓（你自己抓到的那個）就是複製品測試會在架構改變後繼續綠燈說謊。

---

## Z1.〔小・但請寫進註解〕Test Connection 會用**還沒存檔**的憑證去清掉全域 runtime 狀態

```java
if (result.status() == ConnectionTestStatus.Status.OK) {
    ProviderRuntimeRegistry.forEndpoint(endpoint).clearAuthAndCooldown();
}
```

這用的是 pending（尚未 Done）的 API Key。所以：

1. Google 目前是 AUTH_ERROR（key 是錯的）。
2. 玩家在 Provider Detail 打了正確的 key → Test Connection → OK → **清掉 AUTH_ERROR/cooldown**。
3. 玩家按 **Cancel** → pending 被丟棄，`Config` 裡還是那把錯的 key。
4. Router 現在認為 Google 是健康的 → 選它 → 401 → 重新標記 AUTH_ERROR。

**會自我修正**（代價是一次白費的請求 + 一小段時間 AUTOMATIC 會把已知壞掉的 provider 排前面），所以嚴重度低。**但它違反了 `PendingTranslatorConfig` 自己 javadoc 寫的契約**：「Cancel/ESC anywhere just discards this object」——runtime registry 不是 `Config`，可是它同樣是會影響翻譯行為的全域狀態。

我不要求改（spec 明確要 "Runtime Health Recovery without needing Done"，這是刻意的取捨）。**但現在那段註解只寫了「這是唯一會被 test 影響的 runtime 狀態」，沒有寫「而且它不受 Cancel 回溯」。** 請補一句，讓它是一個被記錄的行為，不是一個以後有人發現時會當成 bug 的意外。

---

## 我可能錯的地方

**Z1 的第 4 步我沒有實機驗證**，是從「`clearAuthAndCooldown` 用 pending 值」＋「Cancel 不寫回 Config」兩件我都讀到的事實推的。如果 Router 在挑選 candidate 時還會另外檢查憑證有效性（而不是只看 runtime state），那自我修正會更快，但契約被違反這件事不變。

---

**下一步：** 補 `inferEmptyPoolFailure` 的測試（你可以自己做），然後把上面那張表交給使用者去點。**兩件都完成之前，完成報告先不要寫**——或者寫，但標題要說清楚它是「架構完成報告」而不是「功能驗證報告」。
