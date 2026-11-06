# 審查：Commit 切分方案

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-020-commit-split-proposal.md

## 結論：**回退風險你查對了，而且比我問的更嚴重——但你的風險描述同時漏了一件更壞的、也漏了一件已經在保護玩家的。切分方案的順序要對調（Q3）。**

---

## Q1.〔核對通過，並補上你漏掉的另一半〕`correct()` 不只刪除，還會靜默重設

**你引的那段我核對過，一字不差**——`ModConfigSpec.java` 第 271–282 行，`ittr.remove()` 在第 277 行。TOML 裡存在但 spec 不認得的 key 會被**無條件刪除**。你沒有猜，這條成立。

**但同一個方法第 243–254 行還有另一條路，你的信裡沒提：**

```java
ValueSpec valueSpec = (ValueSpec) specValue;
if (!valueSpec.test(configValue)) {
    Object newValue = valueSpec.correct(configValue);
    configMap.put(key, newValue);          // <-- 換成預設值
```

**key 存在於 spec、但值不合法時，會被換成預設值。** 這對這次的回退情境是直接命中的：

- 玩家在新版選了 `GROQ`（或任何 8 個新 provider 之一），TOML 裡 `endpoint = "GROQ"`。
- 回退到舊版：`endpoint` 這個 **key 在舊 spec 裡存在**（所以不會走刪除那條），但 `"GROQ"` 不是舊 enum 的合法值 → `valueSpec.test` 失敗 → **被換成舊版的預設值 `MISTRAL`**。
- 玩家沒有收到任何提示，`endpoint` 就從 Groq 變成 Mistral。

所以回退的完整後果是「新區塊被刪 **＋** `endpoint` 被靜默改掉」，不只是前者。

## Q2.〔你的風險描述要修正〕`saveToConfig()` 其實已經在保護一半的玩家，而你沒把它算進去

你信裡寫「玩家會發現全部 provider 的 key 都要重打一次」。**這句對一半。** `PendingTranslatorConfig.saveToConfig()` 第 286–287 行：

```java
Config.API_KEY.set(ApiKeyUtil.sanitize(apiKey));
Config.MODEL_NAME.set(ModelIdUtil.sanitize(resolvedModel()));
```

**每次 Done 都會把「當下 active provider」的 key/model 同步寫回舊的扁平欄位**，而那兩個 key 新舊版都認得、回退時不會被刪。所以真正的後果要分兩種玩家講：

| 玩家在新版最後使用的 provider | 回退之後 |
|---|---|
| **原本就有的三個**（Google / Ollama / Mistral） | `endpoint` 合法不會被重設，扁平 `api_key`/`model_name` 又是同步過的 → **完全可用**。只損失其他 provider 存著的 key |
| **8 個新的之一**（Groq / DeepSeek / ...） | `endpoint` 被靜默重設成 `MISTRAL`，而扁平 `api_key` 裡是那個新 provider 的 key → **變成「用 Mistral 的 endpoint 送 Groq 的 key」，401，而且畫面上完全看不出為什麼** |

**這個版本比你信裡的描述準確得多**，而且第二列那個「靜默錯配」比「key 被清掉」更難自己查出來——key 被清掉至少玩家知道要重打。**請把這張表放進 commit 訊息，取代原本那句話。**

**另外一件必須做的事：** 第 286–287 行那兩行現在是**回退存活的唯一機制**，但程式碼裡沒有任何地方說明這件事。在 per-provider 欄位存在之後，它們看起來就是「多餘的舊欄位寫入」——**下一個做清理的人會很合理地把它們刪掉**，然後上面那張表的第一列就跟第二列一樣了，而且不會有任何測試變紅。**請在那兩行上面加註解，寫明它們是刻意保留的向下相容路徑，不是遺留冗餘。**

## Q3.〔切分順序要對調〕照你現在的順序，Commit 1 的 `verify-lang-placeholders` 會是紅的

你把 `en_us.json` 放 Commit 1、其他 9 個語言檔放 Commit 2。但 `verify-lang-placeholders` 這輪驗的正是**10 個語言檔的 key 集合必須完全一致**（這是 H3/H4 我請你升級成這個規則的）。

Commit 1 之後：`en_us.json` 多了 `provider.*`、`custom_provider.*`、`translator.vision_unsupported`、`test_connection.invalid_base_url` 等約 20 個 key，其他 9 個檔沒有 → **那個檢查在 Commit 1 這個點上是失敗的**，要到 Commit 2 才會綠。任何人 bisect 到那個 commit、或在那個 commit 上跑一次完整檢查，看到的都是紅的。

**建議：把兩個 commit 的順序對調。**

- **Commit 1 = 全部 10 個 `LanguageProviderXxx.java` + 全部 10 個 `lang/*.json`**（純資料）
- **Commit 2 = 其他全部**（`Config`、`libs/provider/`、`Translator`、GUI、verify 工具、`.gitignore`）

**我查證過這樣可行**：`git diff src/main/java/net/github/dctime/datagen/` 這輪**沒有新增任何 `Config.` 的引用**（新加的都是字面字串 key），所以 10 個 datagen 檔可以對著 HEAD 的 `Config` 編譯通過。而且：

- Commit 1 之後：10 個語言檔彼此一致 → `verify-lang-placeholders` 綠；`verify-provider-adapters` 還不存在 → 不會失敗。多出來的 key 沒有程式碼引用，只是暫時沒人用。
- Commit 2 之後：全部綠。

**兩個 commit 都是綠的**，而且沒有失去任何回退粒度——語言資料那批本來就沒有 TOML 風險。

**順帶一個用詞修正：** 你把 Commit 2 描述成「9 個新語言的 GUI 翻譯」。那 9 個 `LanguageProviderXxx.java` **不是新的**——它們在 `1fd81af` 就已經進版控了，這輪只是**修改**它們、加進這輪的新 key。commit 訊息要照實寫，否則以後查「這 9 個語言是什麼時候加的」會查到錯的 commit。

---

## 其餘同意的部分

- **不再往下細拆核心功能，我同意。** 理由跟上次批 2+3+4 一樣：硬拆會產生編不過的中間狀態，而編不過的 commit 沒有回退價值。你這次主動說明了這個差異（上次三塊彼此獨立、這次是一體的），判斷正確。
- commit 訊息要放的四類內容（回退風險、model id 與使用者 spec 的差異、未實機驗證清單、指向後續提案）我都同意，加上 Q1/Q2 的修正即可。
- 收尾清單沒有問題。

---

## 我可能錯的地方

**Q1 那條「`endpoint` 會被重設成 `MISTRAL`」我沒有實際做一次降版測試。** 依據是讀 `ModConfigSpec.correct()` 第 246–251 行加上 `Config` 裡 `defineEnum(ENDPOINT_CONFIG_PATH, EndPoint.MISTRAL)` 的預設值。如果 NeoForge 對 `EnumValue` 的 `test()` 有我沒看到的寬鬆處理（例如保留原字串等待未來版本），這條要修正——**但即使如此，Q2 那張表的第一列仍然成立**，而那是要寫進 commit 訊息的主要內容。

---

## 下一步

Q3 對調順序、Q2 的表格與註解、Q1 補進風險說明。這三件之後就可以切了，我不需要再看一輪——**但如果你在寫 commit 訊息時發現 Q2 那張表跟實際行為對不上（例如 `saveToConfig()` 在某條路徑上其實不會寫扁平欄位），那就回來跟我說**，因為那張表是要給未來的自己看的，寫錯比不寫更糟。
