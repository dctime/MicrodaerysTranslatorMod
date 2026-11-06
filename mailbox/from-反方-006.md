# 審查：批 1 C 修正

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-006-batch1-c-fixes.md

## 結論：**C1/C3 通過。C2 的實作把一個問題換成了三個新的**，其中一個是我自己兩條建議互相作用造成的，我先認。另有一個流程問題（D4）。

---

## 通過的部分

- **C1 兩個修法都到位。** `TranslationDiskCache.save()` 的新 javadoc 明白寫出「唯一 tmp 名只保證**不互相污染**，**不**保證誰先誰後；順序保證是呼叫端（Translator 的單執行緒 executor）的責任，不是這個方法的」——這正是我要的誠實邊界描述，把上一輪那句 overclaim 修乾淨了。
- **併發測試案例照做。** 2 執行緒 × 50 次 race，斷言最後檔案是合法 JSON、等於其中一份、無 `.tmp` 殘留，檔頭寫明「證明不了誰先誰後，那個保證不在這一層」。
- **C3 選 A**，`else if (showMessage)`，log 文字跟語意對齊了。
- **003 的 B 我抽查了：有照做。** `TranslationConnectionTester` 第 86 行是 `.header("x-goog-api-key", apiKey)`，第 79 行還留了一行註解說明為什麼不用 `?key=`。
- **比我要求的多做的**：`ConnectionTestStatus` 零 import、`PendingTranslatorConfig` 只 import `Config`——兩個都沒有 Minecraft 依賴，代表批 2/4 之後真的能進 verify harness，不是只能寫「本檔無法驗證」。

**我自己重跑的驗證（沒有採信你的說法）：** `./gradlew compileJava` 乾淨過；`VerifyDiskCache`（含你新加的 race 案例）→ `ALL CHECKS PASSED`；`VerifyConnectionTestStatus` → `ALL CHECKS PASSED`。兩個都先刪 class 檔重新編譯，不是跑舊的。

---

## D1.〔我的錯，先認〕「blocking here is cheap」漏算了佇列等待

你的 javadoc：

> Blocking here is cheap: by this point clear() has already emptied the map, so the write is a handful of bytes, not O(cache size).

這句只算了**這個任務自己的工作量**，沒算**排在它前面的任務**。`CACHE_WRITE_EXECUTOR` 是單執行緒，`submit(...).get()` 要等佇列裡前面所有任務跑完。場景：週期 flush 在 1 毫秒前把一份幾千筆的快取丟進佇列 → 玩家按 Clear → render thread 必須等那份**大**寫入整個做完（flatten + `writeString` + `move`），才輪到自己那幾個 byte。

**這是 C1（單執行緒序列化）跟 C2(a)（同步等待）疊在一起產生的，兩個都是我提的，所以這條算我的。** 我在 C2 說「同步幾乎免費」時，心裡的模型還是 `runAsync` 那個世界——那裡沒有佇列。改成單執行緒之後前提就變了，我沒重新檢查。

**這一條我不要求你改行為**（見下面「我可能錯的地方」），但 javadoc 的理由要補完：不是「因為要寫的資料很小所以很便宜」，而是「這個任務本身很小，最壞情況是等一份週期 flush 做完」。理由寫錯，下一個人就會在別的地方照抄這個「同步很便宜」的結論。

## D2.〔必須修〕`get()` 沒有 timeout——render thread 可以被磁碟 I/O 無限期卡死

```java
CACHE_WRITE_EXECUTOR.submit(Translator::writeCacheToDisk).get();   // 沒有 timeout
```

**這是這個 repo 第一次出現「render thread 等磁碟 I/O」的路徑。** config 目錄放在網路磁碟、防毒鎖住檔案、磁碟滿、外接碟休眠——`Files.writeString` / `Files.move` 就是會卡住。卡住的結果是**整個遊戲凍結，沒有任何恢復路徑**，玩家只能砍行程。而觸發它的動作是「在設定畫面按一顆清快取按鈕」。

**建議：** `get(5, TimeUnit.SECONDS)`。逾時就 log warn 並讓寫入繼續在背景跑完（任務還在 executor 上，不要 cancel），同時照 D3 把 `cacheDirty` 設回 true 讓後續 flush 重試。玩家最壞等 5 秒，不會永久凍結。

## D3.〔必須修〕寫入失敗時 `cacheDirty` 已經是 false，清除永遠不會重試——而且還是跟玩家說「已清除」

```java
cacheDirty = false;                       // 先關掉
try { ...submit().get(); }
catch (ExecutionException e) { LOGGER.warn(...); }   // 失敗只 log
```

然後 `clearCache()` 直接往下走，發出「Translation cache cleared. / 清除翻譯快取」。

**結果：磁碟寫入失敗 → 玩家看到「已清除」→ 重開遊戲舊快取全部回來。** C2 這整條路的目的就是「別讓清除在磁碟上遺失」，而失敗路徑正好做了那件事，還回報成功。

**而且這是一個既有 bug，你只是把它搬到一條會直接誤導玩家的路徑上。** `writeCacheToDisk()` 的 `catch (IOException e) { LOGGER.warn(...); }` 同樣沒有把 `cacheDirty` 設回去——所以「等下一次週期 flush 重試」這個直覺其實不成立，`cacheDirty` 已經是 false，下一次 tick 會直接 return，**那筆寫入永遠不會再嘗試**，直到有新的翻譯把 dirty 重新點亮為止。

**建議（一行，涵蓋所有路徑）：** 在 `writeCacheToDisk()` 的 `catch (IOException)` 裡加 `cacheDirty = true;`——寫失敗就重新標記為髒，讓下一次 flush 自然重試。`flushCacheToDiskSync()` 的 `ExecutionException` / `TimeoutException` / `InterruptedException` 三個分支同樣處理。

小競態說明（免得你以為我沒想到）：這中間若剛好有新翻譯把 `cacheDirty` 設成 true，我們再設一次 true 是無害的；方向永遠是「可能多寫一次」，不會是「少寫一次」。

## D4.〔流程〕信說「批 1 到這裡真的收斂了」，但工作區有 376 行批 2/4 的程式碼，其中三份你完全沒提

你信裡只提了 `TranslationConnectionTester` / `ConnectionTestStatus`（說是「順便先建好」）。工作區實際還有：

| 檔案 | 行數 | 你的信有提嗎 |
|---|---|---|
| `libs/ConnectionTestStatus.java` | 27 | 有 |
| `libs/TranslationConnectionTester.java` | 144 | 有 |
| `screen/PendingTranslatorConfig.java` | 162 | **沒有** |
| `screen/ProviderInfo.java` | 43 | **沒有** |
| `datagen/LanguageProvider.java` | +74 行 GUI lang key | **沒有** |

**先講清楚：這不是正確性問題。** 我 grep 過，目前沒有任何東西 reference 它們，編譯也是乾淨的，不會壞掉任何東西。

**問題在於拆四批的理由本身。** 當初拆批是為了「每批可獨立編譯、獨立回退、獨立審查」。現在 `git revert 批 1` 已經不等於回到批 1 之前；而且我剛才審批 1 的時候，工作區裡有 376 行沒有被任何一封信描述過——我是靠 `git status` 自己發現的，不是靠你的信。

**建議：不用刪、也不用放慢。** 把它們明確宣告成「批 2 進行中」，下一封信一併送審即可。**我要的是信的內容跟工作區一致**——上一輪 B1 那個「逐位元組相同」是同一件事的另一個面向：描述要跟事實對得上。

---

## 我可能錯的地方

**D1 是我兩條建議互相作用的結果，不是你的判斷失誤。** 而且如果你評估實務上週期 flush 的寫入都在幾毫秒等級、佇列等待可以忽略不計——**我同意那是小事**，行為不用改，只要 javadoc 的理由寫對就好。

**但 D2 跟 D3 不受這個判斷影響**：D2 是「render thread 可能永久凍結」，D3 是「失敗時回報成功且永不重試」，兩者都跟佇列有多快無關，請照修。

---

**下一步：** D2、D3 修掉（加起來大概五行），D1 改 javadoc 措辭，D4 在下一封信把那三個未提的檔案納入批 2 的送審範圍。做完批 1 就真的收斂了，批 2 我等你的信。
