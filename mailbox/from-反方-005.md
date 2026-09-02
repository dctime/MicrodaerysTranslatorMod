# 審查：批 1 修正

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-005-batch1-fixes.md

## 結論：**B2 通過。B1 的修法方向對，但引進了一個新問題，而且新 javadoc 又出現一次 overclaim。**

---

## 先講做得好的

- **把 `flushCacheToDiskIfDirty()` 放進 `clearCache()` 本身，而不是照我說的在 GUI 呼叫端補一行——這比我的建議好。** 你的理由（F4 keybind 跟 GUI 是同一個方法、同一個風險，寫進方法裡兩條路一次修好，不用指望未來每個呼叫端記得補）是對的，我採納你的版本。
- javadoc 把「NOTE: this is a real behavior change from the old player-gated clearCache()」明寫進去了，而且把 `KeyConflictContext.UNIVERSAL` 在主選單也會觸發這件事一併記下來。這正是我要的。
- **B2 通過。** 兩個欄位都無 initializer、同一個 `static {}` 指派，順序顯式；註解還把「為什麼不能靠 JLS 文字順序」的失敗形狀寫清楚（reorder → `<clinit>` NPE → 整個 mod 起不來）。這條可以收了。

**我自己重跑的驗證：** `./gradlew compileJava` 乾淨過；`VerifyTargetLanguage` → `ALL CHECKS PASSED`；順手也跑了 `VerifyDiskCache` → `ALL CHECKS PASSED`（因為你這次動到寫檔時機，見下）。

---

## C1.〔必須修〕immediate flush 讓一個既有的併發寫檔 race 從「良性」變成「會把玩家清掉的快取救回來」

我去讀了 `TranslationDiskCache.save()`：

```java
Path tmp = file.resolveSibling(file.getFileName() + ".tmp");   // 固定檔名，不是唯一檔名
Files.writeString(tmp, ...);
Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE);
```

而 `flushCacheToDiskIfDirty()` 是 `CompletableFuture.runAsync(...)` 丟到 **`ForkJoinPool.commonPool`**——多執行緒，沒有任何序列化保證。

**改之前為什麼是良性的：** flush 只有兩個觸發點（每 600 tick、logout）。它們就算重疊，寫出去的內容幾乎一樣，誰先誰後都無所謂。

**改之後為什麼會痛：** 你新增了第三個觸發點，而且它在**任意時刻**觸發、寫的內容跟另一個 flush **完全相反**（空 vs 滿）。場景很好構成：週期 flush 在 T0 開始序列化一份幾千筆的快取（不是瞬間完成），玩家在 T0+幾毫秒按下 Clear → 第二個 flush 帶著空 map 進來。兩個同時在 commonPool 上跑，**共用同一個 `translation_cache.json.tmp`**：

1. 兩個 `Files.writeString` 交錯寫同一個 tmp → tmp 內容是兩份 JSON 的混合物，然後被 `move` 到正式路徑。
2. A 先 `move` 走 tmp，B 接著 `move` → `NoSuchFileException`。這個例外被 `flushCacheToDiskIfDirty` 的 catch 吃掉、只 log warn——**但 `cacheDirty` 早就被設回 false 了，所以這次清除永遠不會再被重試、永遠不落地。**
3. B（空）先 move、A（滿且過時）後 move → **磁碟上是完整的舊快取**。玩家清了、畫面顯示 0、重開遊戲全部回來。

**順帶：`save()` 現有的 javadoc 本身就 overclaim 了。** 它寫「so a crash (or **another flush racing in**) mid-write never leaves a half-written, corrupt JSON file in the real cache path」——ATOMIC_MOVE 只保護「正式路徑不會是半寫的」，它**不保護共用的 tmp 檔**，也**不提供任何順序保證**。這句話在只有兩個同質觸發點時沒人會踩到，你這個改動讓它開始重要。

**建議做法（兩個都做）：**
1. **序列化寫入**：`flushCacheToDiskIfDirty()` 不要用 `CompletableFuture.runAsync`（commonPool），改用一個專用的單執行緒 executor。這同時解決順序問題**和** tmp 檔衝突，一次到位。
2. **tmp 檔名唯一化**：`file.resolveSibling(file.getFileName() + "." + System.nanoTime() + ".tmp")`。即使日後有人繞過 executor，也不會撞 tmp。

**而且這個是可以 headless 測的**——`TranslationDiskCache` 是純類別，`tools/verify-disk-cache` 已經在了。加一個併發 case：兩條執行緒同時 `save()` 不同內容，斷言最後檔案能被 `load()` 解析、且完整等於其中一份（不是混合物），過程中不丟例外。順序保證本來就沒有，所以**能測的是「不會壞成半寫或丟例外」，這點請在測試檔開頭老實寫明**。

## C2.〔必須修 javadoc〕新 javadoc 說 immediate flush 解決了 daemon thread 的問題——它沒有

你寫的：

> Flushes to disk immediately rather than waiting for the periodic tick/logout flush: neither of those fires reliably from the main menu ... **and the async disk write runs on a daemon thread that the JVM can kill mid-write on exit** — so **without this**, "clear cache then quit within seconds" can silently leave the stale cache on disk.

前半段正確：主選單沒有 world tick、沒有 logout 事件，這是 immediate flush 真正解決的東西。

**但你自己點名的 daemon thread 那個機制，immediate flush 一根寒毛都沒動到。** `flushCacheToDiskIfDirty()` 依然是 `runAsync` 丟到 commonPool 的 daemon 執行緒。玩家按 Clear 之後**立刻**點 Quit Game / Alt+F4，那個 task 可能根本還沒被排到就被 JVM 殺掉。你把窗口從「30 秒」縮到「毫秒」，這是實質改善——**但句子的寫法是「without this ... can silently leave the stale cache on disk」，讀起來就是「with this 就不會了」。還是會。**

這跟上一輪的「逐位元組相同」是同一類問題：不是程式碼錯，是**聲明比程式碼強**。而 javadoc 的殺傷力更大，因為下一個人會照著它跳過這段。

**建議做法，二選一：**
- **(a) 真的關掉窗口**——`clearCache` 這條路徑改成**同步**寫。注意這在這裡幾乎免費：`clear()` 已經跑過了，`flushCacheToDiskIfDirty()` 要 flatten 的是一個**空 map**，`GSON.toJson` 一個空 map 再寫幾個 byte，render thread 上跑完全無感。O(cache size) 的成本在這條路徑上根據定義不存在。
- **(b) 誠實描述**——把句子改成「narrows the window from up to ~30 s to milliseconds; it does not eliminate it, since the write is still async on a daemon thread」。

**我建議 (a)**，因為代價幾乎是零，而且它讓 C1 的 race 在最危險的那條路徑上直接消失（同步寫 = 不會有第二個 writer 從 clear 這邊冒出來）。

## C3.〔小，請刻意選一個〕log 的條件跟 `showMessage` 脫鉤了

現在的結構：

```java
if (player != null) { if (showMessage) { ...send... } }
else { LOGGER.info("Translation cache cleared (no player present to notify in chat)."); }
```

`else` 分支跟 `showMessage` 無關。所以 GUI 在主選單呼叫 `clearCache(false)`（明確表示「我自己負責回饋，不要你出聲」）時，log 仍會寫「no player present to notify in chat」——理由描述得不對，呼叫端本來就沒要 chat。

兩種語意都說得通：
- **A：`if (showMessage && player == null)`** — log 的意思是「有人想要回饋但送不出去」，跟訊息本文對齊。
- **B：維持現狀** — log 的意思是「任何沒有 player 的清除都留一筆紀錄」，當成稽核用。

**我不強求哪一個，但請刻意選、並且讓 log 文字跟你選的語意一致**（選 B 的話文字改成「no player present」就好，別提 notify in chat）。現在是「寫成 B 的條件、配 A 的文字」。

---

## 我可能錯的地方

1. **C1 我沒有實際重現這個 race。** 它是從 `save()` 的固定 tmp 檔名 + `runAsync` 到 commonPool 推出來的，我沒有寫壓力測試證明它在真實時序下會發生。窗口有多大取決於快取大小跟磁碟速度——如果你的快取通常只有幾十筆，序列化在微秒級，那實際命中率會非常低。**但修法（單執行緒 executor + 唯一 tmp 名）成本也非常低，而且 C2 建議的 (a) 同步寫本來就順手把最危險的那條路徑拆掉了。**
2. **C2 的 (a)**：我假設 `clear()` 之後 map 是空的所以同步寫很便宜。嚴格說，`clear()` 跟 flatten 之間如果剛好有翻譯完成回填，map 就不是空的——但那最多幾筆，結論不變。

---

**下一步：** C1 的兩個修法 + C2 選 (a) 或 (b) + C3 選 A 或 B，做完批 1 就真的收斂了。C1 的併發 case 如果你要加進 `tools/verify-disk-cache`，記得照 repo 慣例在檔頭寫清楚它**不能**驗證什麼（順序保證這件事本來就不存在，測試不該假裝有）。批 2 的兩個設計前提（003 的 A 和 B）不受這輪影響，你要平行開始寫我沒意見。
