# 審查：回覆 #003

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-003-response.md

## 結論：**批 1 同意動工**（含兩個批 1 範圍內的前提，E/F），批 2–4 有 **4 個新問題**（A–D）

你七點全採納、第 2 點主動接受更好的替代方案、第 6 點給了可追蹤的承諾而不是含糊帶過——這輪回覆品質比提案本身高。但你採納後的**新設計本身**帶進了新問題，尤其第 2 點換成 list-models 之後多出兩個，其中一個是我提的替代方案造成的，我自己講。

---

## A.〔批 2/3・必須先講清楚〕快照與 pending state 的生命週期沒定義

你寫「`PendingTranslatorConfig` 在畫面開啟時快照這四個值」。**「畫面開啟」是哪個畫面？** 你手上會有 Simple、Advanced、`CustomPromptEditScreen`，現在又多一個 `ConfirmScreen`，玩家會在這幾個之間來回。

**會在什麼情況下壞掉：** 如果每個 screen 各自在自己的 `init()` 或建構子裡快照一次，那 Simple →（改了 model）→ Advanced → 返回 Simple 之後，快照被重設成「改過之後的值」。按 Done 時 pending vs 快照永遠相等，**第 1 點那個清快取確認框永遠不會跳出來**。不報錯、不 crash、沒有任何徵兆——就是你剛採納的那個功能靜靜地不存在。

**先修正我自己一個錯誤前提，免得你照著錯的東西做：** 我原本要跟你說「`init()` 會在視窗 resize 時重跑，所以 state 放 `init()` 會被清掉」。**這是錯的**，我去看了 1.21.1 的 `Screen.init(Minecraft,int,int)`：

```java
if (!this.initialized) { ... this.init(); ... }
else { this.repositionElements(); }
```

resize 走的是 `repositionElements()`，`init()` 不會重跑。所以單純拉視窗**不會**弄丟玩家未存檔的編輯。**但 `rebuildWidgets()` 會真的重跑 `init()`**（`clearWidgets()` → `init()` → 再 post 一次 `Init.Post`），而 config 類畫面在切換狀態（例如切 provider 要換掉 model 下拉選單的內容）時呼叫 `rebuildWidgets()` 是非常常見的寫法。所以風險是真的，只是入口不是 resize。

**建議做法：**
- `PendingTranslatorConfig` **只在 `IConfigScreenFactory` 那個進入點 `new` 一次**，建構時就把四個值的快照存進去。
- Simple / Advanced / PromptEdit 全部用**建構子參數**接同一個實例，不各自建立。
- 任何 screen 的 `init()` 裡**不准**出現 `new PendingTranslatorConfig(...)`，也不准重新快照。

請把這三條明寫進提案。理由是它屬於「寫錯不會有任何錯誤訊息」那一類，靠 code review 當下看不出來，只有玩家回報「我換了 model 但沒問我要不要清快取」才會發現。

## B.〔批 4・必須修〕Google 的 list-models 你用 `?key={apiKey}`，把金鑰放進 URL

這跟你自己第 6 點的保密承諾直接衝突，而且跟既有 code 不一致：**`Translator.setupRequest()` 用的是 `x-goog-api-key` header**（`Config.java` 的註解甚至寫了「可以用 ?key=... 也行」，但實作選了 header）。

**會在什麼情況下壞掉：** Java HTTP client 的例外訊息**經常帶著完整 URI**（connect timeout、IO 失敗、`CompletionException` 包裝之後尤其）。你的 `whenComplete` 只要有任何一行把 throwable 或 `throwable.getMessage()` 丟進 log——**完整 API key 就落進 `latest.log`**。而那正是新手出問題時會整包貼到 issue / Discord 的檔案。你在第 6 點花了一整節說明你不會洩漏，然後在第 2 點的實作把 key 放進最容易被例外訊息帶出來的位置。

**建議做法：**
1. Google 的 list-models 一樣用 `x-goog-api-key` header，跟既有 code 一致（順便：三個 provider 就都是「key 只在 header」，規則單一，之後誰來改都不會破）。
2. 明訂 Test Connection 的 `whenComplete`/catch **不准**印 `throwable` 或 `throwable.getMessage()`，只能印你自己 switch 出來的固定文案，最多加 `throwable.getClass().getSimpleName()`。

## C.〔批 4〕「model 不在清單裡」只能當提示，不能當錯誤——這是我提的方案帶來的副作用，我自己講

我上一輪推你去用 list-models，順帶說「還能抓出 model 打錯字」。這句我講得太滿，補上限制：

1. **Google 的 list-models 是分頁的**（`nextPageToken`），單次呼叫不保證回完整清單；而且只回這把 key / 這個 tier 看得到的模型。玩家的 model 落在第二頁 → 你顯示「Model not found」→ 玩家把一個**本來能正常運作**的設定改壞。這種 false negative 比不檢查更糟。
2. **Ollama `/api/tags` 回的名字帶 tag**（`llama3:latest`）。玩家填 `llama3`，字面比對就是不相等，每個人第一次按都會看到「找不到模型」。

**建議做法：把兩件事分開呈現。**
- **主要結果 = 連線/授權**：HTTP 200 → 「Connection OK」，這是按鈕的核心價值。
- **次要提示 = model 比對**：對不上就另起一行黃字「清單中找不到 `{model}`（清單可能不完整）」，**不要**把整體狀態判定成失敗。
- Ollama 比對前先切掉 `:` 之後的 tag；Google 記得剝 `models/` 前綴（這點你已經想到了）。

**還有一句要講在前面：** list-models 回 200 只證明「這把 key 可以列模型」，**不證明 generation 能用**——配額用完、key 設了 API 限制，都可能列得到卻生不出東西。按鈕文案跟成功訊息請不要暗示「測過就一定能翻譯」。這是我這個替代方案相對於原本 generation 測試**變弱**的地方，我上一輪沒講，現在補。

## D.〔批 3/4〕清了快取沒有立刻落地——主選單清完馬上關遊戲＝白清

**會在什麼情況下壞掉：** `clearCache()` 只做 `translationCache.clear()` + `cacheDirty = true`。真正寫檔在兩個地方：`OnClientTickEvent` 每 600 tick（約 30 秒）一次，以及 `ClientPlayerNetworkEvent.LoggingOut`。

**主選單沒有 logout 事件。** 玩家在主選單打開設定 → 按 Clear Cache（或 Done 時選 Yes 清快取）→ 看到按鈕變成 `(0 entries)` → **30 秒內關掉遊戲** → `translation_cache.json` 原封不動 → 下次啟動 `loadCacheFromDisk()` 把全部載回來。玩家以為清乾淨了，其實沒有，而且他不會再懷疑一次。

**而且比 30 秒更寬：** `flushCacheToDiskIfDirty()` 用 `CompletableFuture.runAsync`，跑在 `ForkJoinPool.commonPool` 的 **daemon** 執行緒上。JVM 一結束就直接砍掉，所以就算剛好卡在 30 秒邊界觸發了，也不保證真的寫完。

**為什麼這輪才算數：** 這條路徑是**你的 GUI 才第一次讓它可達的**。在此之前主選單根本沒有清快取的入口；keybind 那條路玩家必定在世界裡，`LoggingOut` 會補救。你新增了入口，就要負責那個入口的完整語意。

**建議做法：** GUI 呼叫 `Translator.clearCache(false)` 之後**立刻**接一行 `Translator.flushCacheToDiskIfDirty()`。一行，不動 Translator 任何既有邏輯，`flushCacheToDiskIfDirty()` 本身就是設計成「cheap to call often」的。Done → 選 Yes 那條路徑也要加。

---

## 批 1 的兩個前提

### E.〔批 1〕`Config.save()` 不要包 try/catch 吞例外

我查過 `ModConfigSpec`：`save()`（178 行）、`ConfigValue.set()`（1260 行）、`get()`（1228 行）全都是 `Preconditions.checkNotNull(loadedConfig, "Cannot ... without assigned Config object present")`，config 沒載入就直接丟。

實務上 CLIENT config 在 mod 載入階段就 loaded，而 `IConfigScreenFactory` 只能從 mod 列表進去，正常情況踩不到。**正因為踩不到，萬一真的丟了就代表有更根本的問題，不該被 try/catch 藏起來變成「按 Done 沒反應」。** 讓它直接炸，堆疊會告訴你真正的原因。（這也符合這個 repo 的慣例：不主動加沒被要求的錯誤處理。）

### F.〔批 1〕順序現在變成契約了，值一行斷言

**先聲明：第 5 點我已經接受 (a)，這不是改條件。** (a) 解決的是「集合會不會 drift」，那個問題 `LinkedHashMap` + `List.copyOf(keySet())` 確實從結構上解掉了。

但你這個改動讓**順序本身**成為新的契約——它現在是 GUI 下拉選單給玩家看的順序。`LinkedHashMap` 只保證「維持插入順序」，不保證日後沒人把 put 的順序調換、或把新語言插在中間。這個契約在你改之前不存在。

`TargetLanguage` 沒有任何 Minecraft 依賴，`tools/verify-target-language` 已經在那裡，加這個是一行：

```java
assertTrue("KNOWN_CODES order", KNOWN_CODES.equals(
        List.of("zh_tw", "zh_cn", "ja_jp", "en_us", "es_es", "fr_fr")));
```

**這是 nice-to-have，不是擋批 1 的條件**，你不加我不會擋。但批 1 是四批裡唯一測得到的一批，能測滿就測滿。

---

## 我可能錯的地方

1. **A**：我這輪修正過自己一次（resize 不會重跑 `init()`，是 `rebuildWidgets()` 會）。如果你實作時發現 `OptionsSubScreen` 那套 layout 在你的用法下還有別的重建路徑會碰到 state，請直接告訴我，我這條的邊界是從 `Screen.java` 讀出來的，不是跑出來的。
2. **C 的分頁**：我是從一般 list API 的慣例推的，**沒有實際打過那個端點**。如果你實測單次就回完整清單、或有 `pageSize` 能一次要完，那 false negative 的風險比我說的低很多——但 Ollama 的 tag 比對問題不受影響，那個一定要處理。
3. **D**：我假設「主選單清完快取後 30 秒內關遊戲」真的會發生。如果你覺得時間窗太窄不值得處理，我不同意——修法只有一行，而且 async 寫檔在 JVM 結束會被砍這件事，讓實際的時間窗比 30 秒更寬、不是更窄。

---

**下一步：** 批 1 直接動工，E 照做、F 建議照做。A–D 不用現在回我，寫進批 2/3/4 的實作裡，送審時我對著 code 看。若你對 B 或 D 有不同看法，那兩點請在動工前先回我一句——B 是安全性、D 是資料正確性，我不希望它們被推到「之後再說」。
