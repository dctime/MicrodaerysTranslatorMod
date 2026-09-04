# 審查：提案 #002 自訂 Config GUI

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-002-config-gui-proposal.md

## 結論：**有條件同意**

Simple/Advanced 兩層的主體方向我同意，症狀寫得具體、範圍清單完整。但有 **2 個必須修**、**1 個要求整包移出本提案**、**3 個你沒提到的問題**，以及交付方式要拆批。

先講一件事：**你的 API 確認清單我逐條對照過 `build/moddev/artifacts/neoforge-21.1.4-sources.jar`，沒有一條是錯的。** `IConfigScreenFactory.createScreen(ModContainer, Screen)`、`OptionsList.addSmall(AbstractWidget, @Nullable AbstractWidget)`（第 49 行的 overload，確實吃任意 widget）、`ModConfigSpec.save()` 是 public（第 177 行）、`ConfigurationScreen` 走 `context.modSpec.save()`（第 997 行）、`OptionsSubScreen` 有 `layout`/`list`/`addOptions()` 這套骨架——全部成立。你說「非猜測」是真的，這在我這裡加很多分，以下的火力集中在你**沒**寫到的地方。

---

## 1.〔必須修〕快取沒有跟 endpoint/model/prompt 綁定，而你的 GUI 會把它從邊界情況變成主要路徑

**會在什麼情況下壞掉：** `CacheKey` 只有 `(lang, text)` 兩個欄位。玩家在你的新畫面把 model 從 `mistral-small-latest` 換成別的、或改了 prompt、或整個換 endpoint，按 Done，回到遊戲把滑鼠移回同一個物品——看到的還是**舊模型產生的翻譯**。而且它已經落地到 `translation_cache.json`，重開遊戲也一樣。玩家的合理結論是「這個設定沒有生效」。

**為什麼這次才算數：** 這個行為現在就存在，但存在感低，因為改 model 要自己去編輯 TOML，沒人常做。而你這個提案的**核心賣點就是「讓新手可以輕鬆換 provider/model 試看看」**——你等於把一個沒人踩到的邊界情況，升級成新手最常走的那條路。這是功能正確性問題，不是體驗問題，所以我把它排在所有 UI 細節之前。

**建議做法：** Done 寫回時比較 `endpoint` / `model_name` / `prompt` / `prompt_screenshot` 這四個值有沒有變。有變就跳一個確認：「翻譯相關設定已變更，是否清除現有 N 筆快取？」預設 Yes。
- 不要偷偷全清——玩家可能只是把 timeout 從 30 改成 45。
- 也不要什麼都不做。
- 剛好可以直接用第 4 點修好的 `clearCache(boolean)`。

## 2.〔必須修〕Test Connection 不佔 RPM 配額，等於對 provider 說謊

**你第 2 點的論證我接受一半。** 就「drop-not-queue 安全網」這個面向，你完全正確：Test Connection 沒有任何「這次沒搶到、指望下一 tick 重試」的隱性假設，按鈕本身是唯一觸發點，按下去 disable、結果回來才 enable——這條路徑不會踩到 #16 的坑。這點你想清楚了，我不擋。

**但你把「不佔 `REQUEST_RATE_LIMITER` 配額」也列成優點，這裡我反對。** RPM 節流器存在的理由不是保護我們自己的 semaphore，是**免費 tier 的配額是 provider 在算的**。Test Connection 送出去的是一個真實的、會被 provider 計費計額的請求。

**會在什麼情況下壞掉：** 玩家在 GUI 裡連按五次 Test（每次都成功、按鈕每次都重新 enable），這五次真實請求完全沒被記帳。玩家關掉 GUI 回到遊戲，`REQUEST_RATE_LIMITER` 以為自己這一分鐘還有滿額，照常送出 `MAX_REQUESTS_PER_MINUTE` 次 → provider 回 429。**而 429 的懲罰完全落在翻譯路徑上**：`RETRY_AFTER` 退避、玩家看到「請求過快導致超過 RPM 限制」的紅字。玩家不會把這個錯誤跟自己剛剛按的測試按鈕連起來。

**建議做法：** Test 請求**維持不被 limiter 擋**（你的設計對），但要**記帳**。給 `RateLimiter` 加一個 `void record(long nowMillis)`，只做 evict + `addLast`，不檢查 limit；Test 實際送出後呼叫它。

**執行緒限制，這點請務必照做：** `RateLimiter` 的 javadoc 明寫「Not thread-safe -- Translator only calls this from the render thread」，內部是裸的 `ArrayDeque`。按鈕的 click handler 在 render thread，直接呼叫沒問題；但**絕對不可以**在 `whenComplete` 的非同步回呼裡碰它。那不會當場爆炸，只會偶發資料損壞，是最難查的那種。要在回呼裡記帳就必須先 `Minecraft.getInstance().execute(...)` 切回主執行緒——你在第 2 點已經為了 widget 安全用了這招，同一個地方順手做掉即可。

**順帶：`RateLimiter` 是純類別，加了 `record()` 就在 `tools/verify-rate-limiter` 加一個 case**：`record()` N 次之後 `tryAcquire(N, now)` 必須回 false。這個真的驗得動，不要只在提案裡口頭保證。

## 3.〔反對納入本提案〕第 4 點的 Options 畫面按鈕注入，請整包移出

**理由一：跟你自己寫的症狀零交集。** 症狀是「Mods→Config 那個自動產生的畫面沒人看得懂」。Options 畫面有沒有捷徑按鈕，跟這個症狀完全無關。

**理由二：這是整個提案裡唯一一個會「靜默消失」的功能。** 我對照過 1.21.1 的 `OptionsScreen`：`HeaderAndFooterLayout(this, 61, 33)`，Done 在 footer，中間是垂直置中的 `GridLayout`。你的規則是「空隙不夠就不加、不 fallback」——那在小視窗或高 GUI scale 下，按鈕就是不存在，而且玩家收不到任何提示。

**理由三（最嚴重）：跨模組行為不可預測。** `getListenersList()` 回的是 `children`，而**多個模組的 `ScreenEvent.Init.Post` handler 之間執行順序是不保證的**。另一個也往 Options 加東西的模組：排在你前面且加在更下面 → 你的「最底部那顆 = Done」前提直接錯；排在你後面 → 你掃描時根本看不到它。同一份 code 在不同 modpack 裡行為不同，全程不報錯。

**建議做法：** 本提案只做 `IConfigScreenFactory` 那條路徑——那是**保證存在**的入口。Options 按鈕想做就另開提案，並且在提案裡就承認它只能靠人工測試、且不保證在所有 modpack 都出現。

**一個你講對、但沒講滿的點：** 我去查了 `Screen.addEventWidget`，它是 `if (b instanceof Renderable r) renderables.add(r); if (b instanceof NarratableEntry ne) narratables.add(ne); children.add(b);`——所以 `Button` 透過 `event.addListener()` 加進去**確實會被 render**，你這裡沒猜錯。問題從來不在「會不會顯示」，在「會不會出現」。

## 4.〔同意，但你的提案內文自相矛盾〕`clearCache(boolean)`

**同意，這是真 bug。** 我對照過現有 code，`player == null` 的 early return 確實在 `translationCache.clear()` **之前**，所以在主選單按清除是整個操作被跳過，連 `cacheDirty` 都不會設。你的 overload 寫法對既有呼叫點行為完全等價（`KeyMapping` 那兩條路都還是走 `clearCache()` → `showMessage=true`）。

你問這算不算「合理重構」的範圍——**算**。我的規則 3 針對的是「新程式碼漏設 `cacheDirty`」，你這個改動的效果剛好相反：把一個既有的、會漏設 `cacheDirty` 的邊界情況修掉。動機正確，範圍最小，保留向下相容 overload。通過。

**但你內文有個矛盾要修：** 你寫「GUI 按鈕呼叫 `clearCache(true)` 一樣會顯示訊息」。在主選單 `player == null`，`showMessage=true` 也只是靜默略過那兩行 `sendSystemMessage`。所以從玩家視角，主選單按下 Clear Cache 依然是**完全沒反應**——跟現在這個 bug 的表徵一模一樣，只是底層真的清掉了。**GUI 必須自己給回饋**：把按鈕文字的「Clear Translation Cache (N entries)」的 N 當場更新成 0。請把這條寫進提案，否則你修了 bug 但玩家感受不到。

## 5.〔你沒提到〕`TargetLanguage.KNOWN_CODES` 會製造第二個真相來源

現有 `KNOWN` 是 `Map.of(...)`，**無序**，所以你需要一份有序 list 這個需求本身合理。

**會在什麼情況下壞掉：** 「哪些語言是已知的」變成兩個地方要同步維護。以後有人加 `de_de` 進 `KNOWN` 但忘了加進 `KNOWN_CODES`，結果是：翻譯邏輯認得這個語言（`displayName()`、`isAlreadyInTargetLanguage()` 都正常），但 GUI 下拉選單裡選不到它。不報錯、不 crash、沒有任何徵兆——跟 #20 那種「一條路徑悄悄落後」是同一種病。

**建議做法（兩條都可以，選一條）：**
- (a) `KNOWN` 改成 `LinkedHashMap`（有序），`KNOWN_CODES` 直接 `List.copyOf(KNOWN.keySet())`。一個真相來源，結構上不可能 drift。
- (b) 想保留 `Map.of` 的不可變語意，那就在 `tools/verify-target-language` 加一個 case 斷言 `Set.copyOf(KNOWN_CODES).equals(KNOWN.keySet())`。

**不接受的是：兩份手寫清單、零檢查。** `TargetLanguage` 沒有任何 Minecraft 依賴，這個檢查是這整個提案裡少數真的跑得起來的驗證之一，請確實做。

## 6.〔你沒提到，跟你的保密承諾直接相關〕主翻譯路徑目前已經在 dump 全文到 log

你專門開了一節講 Test Connection 不洩漏 API Key，做法我認同（只印 endpoint/model/status、錯誤訊息 switch-on-status-code 不回傳原始 body）。**但同一個檔案裡，既有的主路徑正在做相反的事：**

- `Translator.setupRequest()`：`LOGGER.info("[DIAG] Gemini request: ... prompt=[" + prompt + "]")` — 整段 prompt 進 log。
- `Translator.handleHttpResponse()`：`LOGGER.info("[DIAG] response status=" + ... + " body=[" + responseText + "]")` — **整包 response body**，`info` level，`logs/latest.log` 預設就看得到。

API Key 本身在 HTTP header，沒有被印出來，這點沒問題。但你這個 GUI 的目的就是把更多新手帶進來、鼓勵他們換 provider 試——而新手遇到問題最常見的動作，就是把整包 `latest.log` 貼到 issue 或 Discord。

**我不會拿這個擋你的提案**，這不是你這次要改的東西。但既然你專門開了一節宣告「我這部分很安全」，請一併表態：(a) 這次不動、另開一個 issue 追蹤；還是 (b) 順手把 body 那行改成只印 status + body 長度。**我傾向 (a)**——這是獨立問題、獨立風險，不該混進 UI 提案。但那個 issue 要真的存在，不能因為你寫了一節「我的部分很安全」就讓整體問題看起來已經解決了。

## 7.〔流程〕7 個新檔 + 5 個修改一次進來，我沒辦法有效審

請拆成四批，每批可獨立編譯、獨立回退：

| 批次 | 內容 | 可驗證性 |
|---|---|---|
| 1 | `Config.save()`＋`clearCache(boolean)`＋`TargetLanguage` 單一真相來源。**完全不含 UI** | **唯一能被 `tools/verify-*` 覆蓋的一批** |
| 2 | `TranslatorConfigScreen`（Simple）＋換掉 `IConfigScreenFactory`＋`LanguageProvider` lang key | 只能人工測 |
| 3 | Advanced＋`CustomPromptEditScreen`＋第 1 點的「換 model 要不要清快取」確認流程 | 只能人工測 |
| 4 | Test Connection（含第 2 點的 `record()` 記帳＋verify case） | limiter 部分可驗，HTTP 部分不可 |

Options 按鈕注入不在此列，見第 3 點。

**還有一個驗證誠實度的要求：** 批 2/3/4 幾乎全部直接碰 Minecraft 類別，`tools/verify-*` 一行都測不到。唯一能拉出來測的是 `TranslationConnectionTester` 裡「status code → 固定文案」的對應——**請把那段純對應獨立成一個不 import 任何 Minecraft 類別的方法或小類別**，這樣它就能進 verify harness。剩下的部分請照 repo 慣例，在檔案開頭老實寫「這個檢查沒有涵蓋什麼」，不要宣稱「已驗證」。你在第 2、4 點主動寫了「這個 race 我沒辦法在 headless 環境驗證」「屬於理論上安全、視覺效果沒手動測過」——這個態度是對的，請一路維持到 code 裡的註解。

---

## 我可能錯的地方

1. **第 1 點**，我假設玩家「換了 model 卻看到舊翻譯」會被當成 bug 回報。如果你的判斷是「快取本來就該跨 model 共用，翻譯品質的差異不值得重打一次 API」——這是個合理的產品判斷，我可以被說服。但那樣的話 GUI 上就必須明講（model 欄位旁一行小字），不能什麼都不說就讓玩家自己猜。

2. **第 2 點**，如果你的 Test 請求打的是**不吃 generation 配額**的端點——Ollama 的 `/api/tags`、或 Google/Mistral 的 list-models API——那我整個第 2 點不成立，請直接這樣反駁我。**而且我認為那其實是更好的設計**：list-models 通常不計 RPM，還能順便驗證玩家填的 `model_name` 到底存不存在，錯字會當場抓到，比「Reply with OK only.」這種 generation 請求資訊量大得多。如果你採納這個，第 2 點的 `record()` 就不需要了。

3. **第 3 點**，我只從 source 讀了 layout 常數（footer 33、header 61），沒有實際跑起來量過不同視窗大小下的真實空隙。如果你手動測過多種解析度都夠，那我「靜默消失」的說法是誇大的。**但跨模組 handler 順序那個問題不受影響，依然成立**，我的結論（移出本提案）不變。

---

**下一步：** 你不需要整份重寫。針對第 1、2、4、5 點給我修訂後的做法，第 3 點給我一句「移出」或反駁，第 6 點表態 (a)/(b)，第 7 點確認拆批。我確認後就可以開始寫批 1。
