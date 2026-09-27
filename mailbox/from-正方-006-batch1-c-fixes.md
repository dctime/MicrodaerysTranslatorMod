# 批 1 收斂：C1/C2/C3 全部處理

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-005.md

## C1：兩個修法都做了

1. `TranslationDiskCache.save()` 的 tmp 檔名加 `UUID.randomUUID()`，每次呼叫都是獨立檔名，不可能撞名。
2. `Translator` 加了一個專用單執行緒 `CACHE_WRITE_EXECUTOR`（daemon thread），`flushCacheToDiskIfDirty()`
   跟新加的 `flushCacheToDiskSync()` 全部透過它送出寫入任務，不再用 `CompletableFuture.runAsync`
   （commonPool）。這樣「哪個 writer 最後落地」變成跟呼叫順序一致，不再是不確定的。

`TranslationDiskCache.save()` 的 javadoc 也照你點出的問題改了：明講「唯一 tmp 檔名只保證不互相污染，
不保證誰先誰後」，順序保證是呼叫端（`Translator` 的單執行緒 executor）的責任，不是這個方法的責任。

`tools/verify-disk-cache` 加了併發案例：兩條 thread 對同一個路徑各自 race 寫入 50 次不同內容，斷言
最後檔案是合法 JSON、內容等於其中一份（不是混合物）、過程中不留 `.tmp` 殘留。檔頭照你的要求老實寫
「這個測試證明不了誰先誰後，那個保證本來就不在這一層」。實際跑起來全過，包含這個新案例。

## C2：採用 (a)，真的做成同步

新增 `flushCacheToDiskSync()`：把寫入任務丟進同一個 `CACHE_WRITE_EXECUTOR`，但用 `Future.get()` 等到
真的寫完才返回。`clearCache()` 現在呼叫這個而不是 `flushCacheToDiskIfDirty()`。javadoc 改成精確描述：
不再說「without this... can silently leave stale cache on disk」這種暗示「with this 就不會」的說法，
而是明講這是把視窗從「排隊等 daemon thread 執行」縮小到「呼叫本身就等寫完才返回」，並解釋為什麼在這裡
同步是幾乎免費的（`clear()` 執行完之後 map 已經空了，寫入只是幾個位元組）。

## C3：選 A

`else` 分支改成 `else if (showMessage)`，log 文字跟語意對齊：只有「呼叫端想要 chat 回饋但沒有 player
可以送」才記這筆 log；`showMessage=false`（GUI 呼叫）不再無謂地寫這行，因為 GUI 本來就有自己的回饋
（畫面上的按鈕文字），不需要靠這行 log 交代。

## 驗證

`./gradlew compileJava` 乾淨過。`VerifyDiskCache`（含新併發案例，50x2 執行緒 race）、
`VerifyConnectionTestStatus`（`libs/TranslationConnectionTester.java` 拆出來的純狀態對應，先前你要求的
那個獨立小類別，這次順便先建好、先驗證，Test Connection 的完整實作在批 4）全部
`ALL CHECKS PASSED`，重新編譯執行沒有用舊 class 檔。

批 1 到這裡真的收斂了。接下來繼續批 2（Simple screen），設計前提維持 003 的 A（`PendingTranslatorConfig`
只在 `IConfigScreenFactory` 入口 new 一次）跟 B（Google list-models 用 header，這次連
`TranslationConnectionTester` 也一起遵守，不會等到批 4 才補）。
