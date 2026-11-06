# 早期意見：Multi-Provider Router（規劃階段，不是審查）

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-023-multi-provider-router-start.md

你這封是進度通知不是送審，我不會要求你改流程或順序。**但你要動的那三個資料結構，現在的 key 都是為「單一 provider」設計的**——這件事在你畫 request flow 的時候知道，比在寫完 Router 之後知道便宜太多，所以先講。以下都對著現在 `main` 上的 `Translator.java` 行號。

---

## R1.〔最重要〕429 的退避是記在**文字**上，不是記在 **provider** 上

```java
private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();      // 178
private static final Map<String, Integer> RETRY_ATTEMPTS = new ConcurrentHashMap<>(); // 179
...
int attempt = RETRY_ATTEMPTS.merge(text, 1, Integer::sum);                             // 794
RETRY_AFTER.put(text, System.currentTimeMillis() + RetryPolicy.backoffDelayMs(attempt)); // 795
```

`scheduleRetryBackoff(text)` 是從 429 的處理路徑呼叫的。**但 429 是「這個 provider 這一分鐘打太多了」，跟那段文字沒有任何關係。**

**放進 Router 之後會直接壞掉：** 文字 X 送去 Groq → Groq 回 429 → 現在的程式碼在 **X** 上設 4 秒退避 → **Router 接下來連 DeepSeek 都不能為 X 試**，即使 DeepSeek 完全空閒。這正好是 PRIORITY/AUTOMATIC 模式存在的理由，而現有的資料結構會把它擋掉。

而且反過來也錯：Groq 真的被 429 了，**其他文字**照樣會被送去 Groq，因為退避只綁在 X 上。

**建議：** 退避／失敗計數／冷卻搬成 **per-provider**（`Map<EndPoint, ...>`），文字這一層只保留「這段文字現在有沒有 job 在跑」。你信裡列的 runtime state（cooldown / failure count / latency）本來就是 per-provider 的設計，**只是要記得舊的這兩個 map 是要被取代掉、不是並存**——並存的話會變成兩套互相不知道對方存在的退避邏輯。

## R2.〔會咬人〕`IN_FLIGHT` 的 key 跟 cache 的 key 不一致

```java
private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();  // 176，key 是 raw text
private record CacheKey(String lang, String text) {}                          // cache 的 key 是 (lang, text)
```

你信裡寫「同一段文字只能建立一個 translation job」——**但「同一段文字」在 cache 那邊的定義是 `(lang, text)`，在 `IN_FLIGHT` 這邊只是 `text`。**

今天影響很小（語言很少在請求飛行中途變），但 Router 會把一個 job 的存活時間從「一次 HTTP 往返」拉長到「最多 N 個 provider 依序試」。窗口變寬之後：玩家在 job 飛行中切換遊戲語言（`follow_game_language` 開著），`IN_FLIGHT` 仍然擋住那段文字，而它擋掉的是**另一個 cache entry** 的請求——結果是新語言的那筆永遠不會被送出，直到舊 job 結束。

**建議：job 的 key 直接用 `CacheKey`。** 這也讓「job 完成時要寫回哪個 cache entry」變成不需要思考的事——現在是靠 `keyFor(text)` 在完成時重新解析一次當下的語言，如果語言中途變了，寫回去的位置跟當初決定要翻的位置就不是同一個。

## R3.〔最危險的一段編輯〕全域 semaphore 的 acquire/release 配對

```java
if (!CONCURRENCY_LIMIT.tryAcquire()) return;   // 694 附近
...
} finally {
    CONCURRENCY_LIMIT.release();               // 709 附近
```

你信裡說「fallback 不能重複佔用 global slot」——方向對。但這意味著 **acquire 一次、跨越 N 次 attempt、release 一次**，而現在的 release 在單次請求的 `whenComplete` 的 `finally` 裡。

這段程式碼上面就有一行既有註解：

> acquire/release must stay paired 1:1 with this exact ordering; a mismatch here isn't caught by any automated test — review this finally block by eye before changing it.

**這句話在你這輪會變成整個改動裡最危險的一句。** 漏 release 一次，`Semaphore(4)` 就永久少一格；漏四次，整個翻譯功能靜默死掉，而且沒有任何錯誤訊息、重開遊戲才會好。

**建議：** Router 的 job 狀態機（acquire → attempt 1..N → release，含中途 return / 例外 / timeout 的每一條路徑）**應該有一個 headless 測試**。`tools/verify-concurrency` 已經示範過怎麼測這種東西——它不呼叫 `Translator`（載不動），而是用**同一組 JDK primitive、照同樣的順序**重現那個狀態機，並在檔頭老實寫「這證明的是設計意圖，不是 `Translator` 的 bytecode」。你的 Router 如果把 job 狀態機抽成一個不依賴 Minecraft 的類別，就可以測到真的那一份，比 `verify-concurrency` 當初的處境好。

## R4.〔你已經想到，補一個下游影響〕`translationRelevantSettingsChanged()`

你說得對：AUTOMATIC 模式下同一段文字本來就可能被不同 provider/model 翻，所以「provider/model 換了」不能再自動跳「要不要清快取」。

**補一個你沒提到的下游：** Advanced 畫面上那行說明文字（`model_cache_note`，大意是「更換模型後舊翻譯快取不會自動失效」）在多 provider 之後**語意會變成誤導**——它現在暗示「一段文字對應一個模型的輸出」，而新模型下這個前提本身就不成立了。那是 10 個語言檔裡的一個 key，改文案要一起改 10 份，**建議跟 Router 同一批處理，不要留到之後**。

---

## 我可能錯的地方

R1/R2/R3 都是從現在 `main` 上的程式碼讀出來的，**不是從你的設計文件**——你信裡描述的 per-provider runtime state 有可能本來就打算整組取代掉 R1 講的那兩個 map。**如果是這樣，R1 就只剩下一句提醒：記得是「取代」不是「並存」。** R2/R3 則不受影響，因為那兩個是既有結構、你信裡沒有提到要改。

---

照你原本的節奏走，規劃完再發完整架構信。**這封不需要回。** 唯一希望你在畫 request flow 時就決定的是 R2（job 的 key 是 `text` 還是 `CacheKey`）——那個決定會滲透到 Router 的每一個介面，晚改很痛。
