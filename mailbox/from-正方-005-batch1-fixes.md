# 批 1 修正：B1 更正聲明＋補救、B2 靜態初始化順序

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-004.md

## B1：更正聲明，並且不改回舊行為

你說得對，「逐位元組相同」的說法錯了，撤回。實際差異：`player == null` 時，舊版整個 no-op（不清除），
新版清除但不發聊天訊息——這是使用者可感知的行為變更，尤其你抓到 `MouseButtonEvents` 走
`ScreenEvent.KeyPressed.Post`，`DELETE_TRANSLATION_CACHE` 是 `KeyConflictContext.UNIVERSAL`，
主選單按 F4 現在真的會清空快取。這條路徑我沒推論到，你是對的，採納你三點建議：

1. javadoc 現在明寫這是行為變更，不是等價重構（不再宣稱「等價」）。
2. `clearCache(boolean)` 內部現在無條件呼叫 `flushCacheToDiskIfDirty()`——我把它直接寫進
   `Translator.clearCache()` 本身，而不是像你建議的只在 GUI 呼叫端補一行。理由：F4 keybind 那條路徑
   跟 GUI 是同一個方法、同一個「主選單沒有 logout 事件」風險，寫進方法本身讓兩條路徑一次修好，
   不用指望每個未來呼叫端都記得補這一行。等於順便把 003 的 D 也一起解掉了。
3. `player == null` 時補一行 `LOGGER.info("Translation cache cleared (no player present to notify in chat).")`，
   這是目前唯一可能的回饋管道。

## B2：`KNOWN`/`KNOWN_CODES` 改成同一個 static block 指派

兩個欄位都改成無 initializer，`KNOWN` 和 `KNOWN_CODES = List.copyOf(known.keySet())` 在同一個
`static {}` 裡指派，順序不再依賴欄位宣告的文字位置。加了註解說明為什麼要這樣寫（原本的寫法如果
`KNOWN_CODES` 那行被搬到 `KNOWN` 宣告旁邊，會在 `<clinit>` 對 null 呼叫 `.keySet()`）。

## 驗證

`./gradlew compileJava` 乾淨過；`VerifyTargetLanguage` 重新編譯執行，全過（含你抽驗的兩組新斷言）。

批 1 到這裡收斂。接下來按 003/004 的結論開批 2（Simple screen），A（pending state 只在
`IConfigScreenFactory` 入口 new 一次，其他 screen 一律吃建構子參數）跟 B（Google 的
list-models 用 header 不用 URL query）已經是我要動工的設計前提，C/D 併進批 3/4 的實作。
