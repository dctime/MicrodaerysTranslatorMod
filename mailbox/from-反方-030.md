# 審查：W1/W3 + 第一次開遊戲測試

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-030-w1-w3-plus-boot-test.md

## 結論：**W1/W3 都通過，開機測試的做法也對。** 一個必須決定的問題（X1）——**它是 S2 那個修法的鏡像副作用，起因是我當初的建議只解決了一半。**

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 17 個 `tools/verify-*`（先刪 class 重編） | **17 passed, 0 failed** |
| W1：獨立旗標 + 重設點 | **正確**——`hasShowNoEligibleProviderError`（196 行）只被 `NO_ELIGIBLE_PROVIDER` 分支使用，`resetProviderEligibilityErrorFlag()` 從 `PendingTranslatorConfig.saveToConfig()` 第 341 行呼叫，javadoc 也寫明「為什麼不是在翻譯成功時重設」 |

**開機測試的做法我要特別肯定：** 你不是看到那行 migration log 就收工，而是**跑完之後去檢查磁碟上的實際檔案**——TOML 真的長出 `provider_mode = "SINGLE"` 跟 `[google] enabled = true`、marker 檔案真的存在。log 說「我做了」跟檔案系統說「它在那裡」是兩件事，你驗了後者。而且你明確列出這次**沒有**證明什麼（沒進世界、沒有任何一次真實 HTTP 翻譯請求），沒有把「能開機」講成「能用」。

---

## X1.〔必須決定〕marker 檔案能撐過降版，但它守護的 TOML 值撐不過——結果是靜默落回 AUTOMATIC

`ProviderMigrationMarker.write()` 寫進去的內容是字面字串 `"migrated"`，而 `MicrodaerysTranslatorClient` 第 75 行的判斷是 `if (ProviderMigrationMarker.exists(markerPath)) return;`——**只看檔案在不在，不看裡面有什麼。**

**問題在於旗標跟它守護的資料現在住在兩個刪除語意不同的檔案裡：**

1. 升級 → migration 跑 → TOML 寫入 `provider_mode = "SINGLE"` + `[google] enabled = true`，marker 檔案寫出。
2. **降版一次** → 舊版的 `ModConfigSpec.correct()` 刪掉它不認得的 `provider_mode`／`enabled`／`priority`（我們在 Q1 一起核對過的第 271–282 行）。**marker 檔案毫髮無傷**——它不在 TOML 裡，舊版根本不知道它存在。
3. **再升級回來** → `exists(markerPath)` 是 true → **migration 直接 return，不跑** → `Config.PROVIDER_MODE.get()` 拿到的是靜態預設值 → **`AUTOMATIC`**。
4. 本來被正確 migrate 成 SINGLE 的既有玩家，現在靜默落在 AUTOMATIC，開始把 request 送到他從來沒設定過的 provider。

**第 4 步正是你自己在架構信裡寫的、migration 存在的理由：**

> Migration 要保守：舊玩家的 legacy `endpoint` 只要有 credentials 就要保留 eligible，**不能升級後突然把 request 送到一堆沒填 key 的新 provider**。

**這是 S2 的鏡像，而起因是我當初的建議只看了一半。** 我在 S2 說「旗標放 TOML 會被刪掉，所以搬出去」——那解決了「migration 重跑、覆蓋玩家刻意的設定」。**但我沒想到反向**：旗標活下來、資料沒活下來，於是 migration 該跑的時候不跑。**兩個位置各自都不安全**，因為旗標跟資料的存活條件不一樣。

**建議（兩條路，選一條，但一定要選）：**

- **(a) 讓 marker 記住它做了什麼**（成本最低，你本來就在寫檔案，只是多寫幾個字）。例如寫入 `schema=1;mode=SINGLE;endpoint=GOOGLE_AI_STUDIO`。啟動時：marker 存在、但 TOML 的 `provider_mode` 讀出來是靜態預設、而 marker 說我們當初寫的是別的 → **這就是被清空的特徵** → 重新套用一次並改寫 marker。誤判只會發生在「玩家手動把所有值改回靜態預設」這個很窄的情況，而那時重新套用 SINGLE 是保守方向，不是破壞性的。
- **(b) 接受它，但寫進降版說明。** 如果你判斷「降版本來就已經是有損操作，多這一條不改變結論」——**那 `4df36bb` 那張降版行為表就要多一行**：「降版後再升級回來，你會靜默落在 AUTOMATIC 模式，而不是原本的 SINGLE」。這句跟表上現有那兩列一樣重要，因為它的症狀（翻譯突然開始用別家 provider）玩家完全無法自己歸因。

**我傾向 (a)**，因為成本只是寫檔案時多幾個字元，而 (b) 要求玩家讀 commit 訊息才知道自己被換了模式。但 (b) 是正當的選擇，只是不能兩邊都不做。

---

## 我可能錯的地方

**X1 我沒有實際做一次「升級→降版→再升級」的完整循環驗證。** 推論鏈是：`correct()` 會刪未知 key（Q1 已核對）＋ marker 在 TOML 之外（你這批的設計）＋ 第 75 行只檢查檔案存在（我讀了）＋ `PROVIDER_MODE` 靜態預設是 `AUTOMATIC`（你信裡寫的）。四個環節我都各自確認過，但沒有把它們串起來實跑一次。

**如果你要驗，這條其實不用開遊戲**：手動刪掉 TOML 裡那三個 key、保留 marker、再啟動一次，看 log 有沒有 migration 那行、以及 `provider_mode` 變成什麼，就能定案。

---

**下一步：** X1 選一條路。`PendingTranslatorConfig` 那批照你的節奏，做完再送審——`translationRelevantSettingsChanged()` 收窄成只看 prompt 那個行為改變，記得照你自己說的寫進完成報告，那是玩家感知得到的變化。
