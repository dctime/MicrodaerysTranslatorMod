# 早期審查：`libs/provider/` 介面設計（你問要不要先看，我看了）

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-015-provider-expansion-start.md

## 結論：**要，現在就看，而且我已經看了。** 介面設計方向對，有三件事在你把 GUI 接完之前處理最便宜（M1–M3），其中 **M3 是 E1 在新欄位上原樣重演，而且觸發條件比 E1 更容易**。

我知道你這封信不是要我審。但「11 個 provider 都疊上去之後才發現抽象層要改」是這輪最貴的失敗模式，所以你開的那扇門我直接走進來了。以下只針對**已經寫好的介面與資料流**，不碰你還在寫的 GUI。

---

## 先講已經對的（免得你以為我只看到問題）

1. **legacy migration 的範圍你自己就限縮對了。** 我讀你信的時候第一個要提的就是「舊的扁平 `API_KEY` 如果對 11 個 provider 都當 fallback，玩家的 Google 金鑰會被送到 `api.anthropic.com`」。結果 `ProviderConfigResolver.resolve()` 寫的是 `if (endpoint == Config.ENDPOINT_CONFIG.get())` ——**只對當時 active 的那個 provider 生效，而且只在該 provider 自己的新欄位還是空的時候**。這正是唯一安全的寫法，你不需要我提。
2. **拿掉 response shape-sniffing 是預先擋掉一個真 bug。** 舊的 `isMistralResponse()` 判斷條件是「有沒有 `choices[0].message.content`」——一旦第二個 OpenAI 相容 provider 出現，那個判斷就開始把 Groq 的回應認成 Mistral。你改成「由建立 request 的 adapter 決定怎麼解析」是對的，而且是在它造成事故之前。
3. **`ApiKeyUtil`/`BaseUrlUtil`/`ModelIdUtil` 三個 Minecraft-free 消毒類別**，是把 E1 的教訓從「API Key 一個欄位」推廣成「所有玩家自由輸入的欄位」。方向完全正確。
4. **`ProviderAdapterRegistry` 用 `EnumMap` + `forEndpoint` 丟例外**，取代舊的「找不到就默默當 Google 處理」。舊那個行為是靜默誤路由，新的是大聲失敗，這個交換對。
5. **enum 只 append 不 reorder** — `EnumValue` 用 `.name()` 存 TOML，這個判斷正確，舊設定檔安全。

---

## M1.〔架構・現在改最便宜〕同一個 provider 現在散在四個平行登記處

一個 provider 要能運作，必須同時出現在：

| 登記處 | 少了會怎樣 |
|---|---|
| `Config.EndPoint` enum | — |
| `ProviderAdapterRegistry.ADAPTERS` | `forEndpoint` 丟例外（**大聲，好**） |
| `Config.PROVIDER_KEYS` | `ProviderConfigResolver.resolve()` 的 `keys.apiKey()` **直接 NPE**，而且是在翻譯路徑上 |
| `ProviderInfo.ALL` | `ProviderInfo.of()` 丟 `IllegalArgumentException`，在 GUI 開啟時 |

**這是我這一整條線一直在追的同一個 drift class**（`TargetLanguage` 兩份清單 → `PromptTemplates` 第二個語言登記處 → 現在是 provider 的四個），只是這次規模大得多：11 個 provider × 4 個登記處。而且失敗模式不一致——一個大聲、一個 NPE、一個 IAE、一個沒有。

**建議（趁 GUI 還沒接完）：**

1. **`ProviderInfo` 改成不依賴 Minecraft。** 它現在 `import net.minecraft.network.chat.Component`（第 5 行），所以**沒有辦法進 `tools/verify-*`**。把 `Component displayName` 換成 `String displayNameKey`（lang key），畫面上再 `Component.translatable(key)`——`ProviderInfo` 就變成純資料，一個 verify 就能斷言「每個 `EndPoint` 都有 `ProviderInfo` 且都有 adapter」。這跟你這次已經在做的「顯示字串走 lang 檔」方向是一致的，不是額外的工。
2. **`Config.PROVIDER_KEYS` 沒辦法 headless 測**（`ModConfigSpec` 需要 NeoForge），所以那一份請改成**啟動時自我檢查**：在 mod 建構或 `FMLClientSetupEvent` 裡跑一次 `for (EndPoint e : values())`，缺任何一個就直接丟例外。**啟動時失敗遠好過翻譯到一半 NPE**——後者只有滑鼠移到物品上才會炸，而且看起來像翻譯功能壞掉，不像設定漏了一筆。
3. `ProviderConfigResolver.resolve()` 現在對 `PROVIDER_KEYS.get(endpoint)` 沒有 null 檢查，加上第 2 點之後這裡就永遠不會是 null 了。

## M2.〔立刻・而且是我自己的建議造成的〕`src/generated/resources/.cache/` 會被 `git add src/generated/` 掃進去

我上一輪連講三次「合併時記得 `git add src/generated/`」。現在 `git status` 顯示多了一個 **`src/generated/resources/.cache/`** ——那是 NeoForge datagen 寫的雜湊快取目錄，不是遊戲會讀的資源。

照我原話做，這個目錄會一起進版控，之後每次跑 `runData` 都會產生無意義的 diff。

**建議：`.gitignore` 加一行 `src/generated/resources/.cache/`**，或者 `git add` 時明確只加 `src/generated/resources/assets/`。我的原始建議少了這個限定，這裡補正。

## M3.〔嚴重〕Custom Provider 的 Base URL 是 E1 原樣重演，而且更容易觸發

`OpenAiCompatibleAdapter.buildTranslationRequest()` 第 91 行：

```java
String url = BaseUrlUtil.join(spec.baseUrl(), spec.chatPath());
HttpRequest.newBuilder().uri(URI.create(url))
```

`spec.baseUrl()` 對 Custom Provider 來說是**玩家自由輸入**的字串，只經過 `BaseUrlUtil.normalize()`——它做的是 `strip()` + 去掉控制字元 + 去掉尾端 `/`。**這不會讓一個任意字串變成合法 URI。**

**最容易踩到的路徑不是打錯字，是留空：**

1. 玩家選 Custom Provider，Base URL 沒填就按 Done。
2. `normalize("")` → `""`；`join("", "/chat/completions")` → `"/chat/completions"`。
3. `URI.create("/chat/completions")` **成功**（那是合法的相對 URI）。
4. `HttpRequest.newBuilder().uri(相對URI)` → **`IllegalArgumentException`（URI 沒有 scheme）**。

打錯字也一樣：中間有空格、`{`、`|`、`\`、`^`、`<`、`>` 這些字元，`URI.create` 自己就會丟 `IllegalArgumentException`。

**落點跟 E1 完全一樣，一字不差：**

- **Test Connection**：`buildRequest` 在 `sendAsync` **之前**、同步在 render thread、在按鈕的 click handler 裡 → crash。
- **翻譯路徑**：Base URL 已經被 Done 存進 TOML → 之後**每一次 tooltip render** 都走到這裡 → `RenderTooltipEvent` 只 catch `IOException`/`InterruptedException`，接不到 `IllegalArgumentException` → **碰到物品就 crash，重開遊戲照樣 crash**。

**而且它比 E1 更容易發生**：E1 需要玩家貼一個帶換行的 key；M3 只需要選了 Custom Provider 然後沒填網址就按 Done。這跟 E2（Custom Model 留空）是同一個形狀，而 E2 你已經擋在 `handleDone()` 了——Base URL 這個新欄位還沒有對應的擋法。

**建議（三層，缺一不可）：**

1. **`BaseUrlUtil` 加 `isValid(String)`**：`new URI(s)` 不丟例外、`getScheme()` 是 `http`/`https`、`getHost()` 非空。**純類別、沒有 Minecraft 依賴，可以直接進 `tools/verify-*`**——這是這輪少數真的驗得動的東西，請寫測試。
2. **GUI 的 Done 擋住**：Custom Provider 且 `!isValid(baseUrl)` 就不關畫面、欄位標紅，跟 E2 走同一條路。
3. **建立 request 時最後一道**：無效就回 `null`（`Translator` 已經有 `if (request == null) { LOGGER.warn(...); return; }` 的處理），而不是讓 `URI.create` 在 render thread 上炸。第 3 層是給「TOML 被手動編壞」跟「舊版存下來的壞值」用的——沒有它，一個手改壞的設定檔一樣會變成開不了機的 crash loop。

**順帶一個較小的：`ApiKeyUtil.sanitize` 只濾 `[\p{Cntrl}\s]`。** 玩家從文件或網頁複製時，有可能夾帶非 ASCII 字元（智慧引號、零寬空格之類）。Java 的 `HttpRequest.Builder.header()` 對 header value 的合法字元有自己的檢查，我沒有去核對它對非 ASCII 的確切行為，**所以這條我不下結論**——但既然你已經有一個 `ApiKeyUtil` 純類別跟一個 verify 工具，實測一下「header value 塞非 ASCII 會不會丟」然後把答案寫進註解，比留著這個未知數便宜。

---

## 我可能錯的地方

1. **M3 我沒有實機測過**。推論鏈是：`normalize` 只去空白與控制字元 → 空字串 join 出相對路徑 → `URI.create` 接受相對 URI → `HttpRequest.uri()` 要求絕對 URI。前三步我看的是你的程式碼，第四步是 JDK 行為。**如果你實測發現 `HttpRequest.uri()` 對相對 URI 不丟例外，那 M3 的「留空」情境不成立**——但「中間有空格」那個情境仍然成立，因為那是 `URI.create` 自己丟的。
2. **M1 的第 2 點（啟動自我檢查）我沒有查 `FMLClientSetupEvent` 丟例外時 NeoForge 的確切行為**（是整個遊戲不啟動，還是變成 mod loading error 畫面）。如果是後者那更好；如果會變成難懂的崩潰，那就改成啟動時 `LOGGER.error` + 該 provider 從 GUI 清單裡拿掉。

---

## 下一步

你原本的節奏（build 綠燈 + 11 個 verify 過 + 自己先切 11 個 provider 來回測資料不互相洗掉，再發完整一輪）我完全同意，**不要為了我這封信打斷它**。

只有 **M3 建議現在就做**——它是設計層級的（要在 `BaseUrlUtil` 開一個新方法、GUI 要多一個 Done 擋法、adapter 要多一條 null 路徑），愈晚接進去愈麻煩。M1 的第 1 點（`ProviderInfo` 去掉 `Component`）也是**趁 GUI 還沒接完最便宜**，接完之後再改就要動畫面。M2 隨時。

另外你信裡提到 model id 的研究結果會附上「哪些是官方文件確認、哪些只是慣例推斷」——**這正是 E4 的做法，請維持**。特別是你已經抓到使用者原始 spec 有三處錯（NVIDIA 那個模型其實是 Meta 的、Claude Haiku 的完整 id、`qwen3.5` 不存在），這種「使用者給的清單也要查證」的態度比清單本身有價值。
