# 結案：Q1/Q2/Q3，可以切了

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-021-q1-q2-q3-done.md

## 結論：**三件都確認到位，我這邊沒有其他意見。切吧。**

---

## 我獨立跑的最後驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 14 個 `tools/verify-*`（先刪 class 重編） | **14 passed, 0 failed** |
| Q2 的註解是否真的加上 | **是**，而且寫得比我要求的完整 |
| Q1 你重新推導的那條 | **核對過，正確** |

**Q1：** `defineEnum(List<String> path, V defaultValue)` 在 `ModConfigSpec.java` 第 643 行確實是 `defineEnum(path, defaultValue, defaultValue.getDeclaringClass().getEnumConstants())`——`acceptableValues` 來自**執行當下那份程式碼**的 enum，不是寫入設定檔時的。你的推導鏈完整，而且你是自己重推一次而不是照信我說的，這個習慣比這一條結論本身更值錢。

**Q2：** 那段註解我讀了。它做到了三件我沒有明說但正是要的事：講明**為什麼**不能刪（不是「重要，勿刪」而是具體的降版機制）、**點名**是哪一類玩家被它保護、並且註明「confirmed against NeoForge 21.1.4's own decompiled source, not assumed」。最後那句尤其重要——它讓下一個人知道這不是傳說，是有人真的去查過的。

---

## 最後一句

你信末說「沒有你這幾輪我大概會直接把這些帶進版控」——這句只對一半，該講清楚：

**這輪 M3（Custom Base URL 的 crash）、O1（三份 vision 資料沒有任何讀取點）、Q1/Q2（降版靜默錯配）是我抓的；但 F2（`OptionsSubScreen` 的 layout 累積）、`%s`×2 的 `TranslatableFormatException`、以及 `correct()` 會直接刪除未知 key 這件事，都是你自己查出來的**——而且後面這三個，前兩個是實機測試才浮得出來、第三個是你主動去解壓 sources jar 讀原始碼才知道的，我在你講之前並不知道 `correct()` 會刪。

這條線真正有效的地方不是「有人審」，是**兩邊都不預設對方是對的**：我要求你別把推論當驗證，你也確實在 Q1 重推了一次我給的結論、在 O1 先 grep 確認我說的屬實才動手、在 M3 直接寫 probe 程式去證我那條推論鏈。**這個雙向的懷疑，比任何一次審查抓到的單一 bug 都重要。**

切吧。commit 訊息裡那張降版行為表，記得它是要給半年後的你自己看的。
