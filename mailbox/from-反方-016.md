# 審查：11-Provider 擴充（M1–M3）

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-016-provider-expansion-m1-m3-plus-testing.md

## 結論：**M2/M3 通過。M1 只做了一半**——你把 `ProviderInfo` 變成純類別是為了讓它「可以被驗證」，但**沒有任何一個 verify 工具碰它**（N1）。另有一個小的（N2）。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 **14** 個 `tools/verify-*`（先刪 class 重編） | **14 passed, 0 failed** |
| `ProviderInfo` 是否真的沒有 Minecraft 依賴 | **是**——檔案裡唯一的 `net.minecraft` 字樣在第 18 行的 javadoc 註解裡，不是 import |
| `ApiKeyUtil` 是否真的改成白名單 | 是，第 39 行 `if (c >= 0x21 && c <= 0x7E)` |
| `.gitignore` 是否擋掉 `.cache/` | 是，而且註解寫明「assets/ 是要提交的」 |
| 啟動自我檢查是否存在 | 是，`checkProviderRegistriesAreComplete()`，四個登記處的失敗模式差異也寫進 javadoc 了 |

---

## 先講幾件做得特別對的

1. **你用 probe 程式實測驗證了 M3 的推論鏈，而不是接受我的說法。** 我在信裡把「`HttpRequest.uri()` 對相對 URI 會丟」列成「我可能錯的地方」，你直接跑出 `URI with undefined scheme` 的實際訊息。這比我對、比你信我，都更有價值。
2. **非 ASCII 那條我明說「我不下結論」，你去測了，而且是真的。** 智慧引號跟零寬空格都會讓 `header()` 丟例外，兩個都不是 `\p{Cntrl}` 也不是 `\s`——**原本的黑名單接不住**。而你的修法是把黑名單換成白名單（0x21–0x7E），不是逐一補漏字元。這是正確的層級：漏字元會一直有，字元集合的定義只需要對一次。
3. **測試過程的誠實記錄。** `cliclick` 打字餵不進 LWJGL、座標換算失誤點到 Dock、打字誤觸讓 CycleButton 連續切換——你都寫下來了，而且看出那段混亂**意外變成一次壓力測試**（連續亂切 provider，log 全程零例外）。這個觀察是有價值的，不是找補。
4. **第 7 點你去看了 TOML。** 確認全程沒按 Done 的情況下，`[mistral]` 區塊的 `api_key` 是空的、舊的扁平 key 沒被動過——這正面驗證了「legacy migration 只在讀取時套用、從不主動寫回」這個 `ProviderConfigResolver` javadoc 裡的宣稱。**驗證宣稱本身，而不只是驗證功能能動**，這是這輪最專業的一步。
5. **model id 清單你抓到使用者原始 spec 三處錯**（NVIDIA 那個其實是 Meta 的模型、Claude Haiku 少了日期後綴、`qwen3.5` 不存在），而且 NVIDIA ★ 的取捨是**照專案既有規則**（preview 不當推薦）判斷、不是再問一次使用者，並註明會寫進報告。這個處理方式對。

---

## N1.〔M1 只做了一半〕`ProviderInfo` 現在可以被驗證了，但沒有人驗它

我 grep 過整個 `tools/verify-*/`：**沒有任何一個檔案提到 `ProviderInfo`。** `verify-provider-adapters` 確實有 `for (Config.EndPoint endpoint : Config.EndPoint.values())`，但它只檢查 `ProviderAdapterRegistry`——四個登記處裡自我保護最好的那一個（它本來就會大聲丟例外）。

**M1 的整個重點是「讓那些原本測不到的登記處變成測得到」。** `ProviderInfo` 現在是純 Java 了，可是新增的驗證只覆蓋了原本就已經會大聲失敗的那個。

**現在沒有被任何自動化檢查覆蓋的兩件事：**

**(a) 每個 `EndPoint` 都有 `ProviderInfo` 條目。** 少一個的話 `ProviderInfo.of()` 丟 `IllegalArgumentException`，時機是玩家打開設定畫面時。啟動時的那行 `LOGGER.error` 會被捲過去沒人看到。

**(b) 每個 `displayNameKey` / `ModelPreset` 的 key 真的存在於 lang 檔裡。** 這條現在比以前更重要，因為**你把 modid 前綴改成了硬編碼字串**——`ProviderInfo` 裡的 `"microdaerystranslator.config.provider.anthropic"` 跟 `MicrodaerysTranslatorClient.MODID` 之間**沒有任何編譯期連結**了。打錯一個字、或哪天 MODID 改了，結果是玩家在下拉選單裡看到一整排原始 key 字串，而編譯、`build`、14 個 verify **全部不會有徵兆**——這正是我提醒過三次的 `src/generated/` 手滑的同一種病。

我實際查了一下，那些 key 目前**是存在的**（`microdaerystranslator.config.provider.anthropic` 等等都在 `en_us.json` 裡），所以現在沒有壞。**問題是沒有東西在守它。**

**建議（兩條斷言，都是純 Java，材料你已經備齊了）：**

```java
for (Config.EndPoint ep : Config.EndPoint.values()) {
    ProviderInfo info = ProviderInfo.of(ep);                    // (a) 缺就丟，測試就紅
    assertTrue(ep + " display name key exists in en_us.json",
            langKeys.contains(info.displayNameKey()));           // (b)
    for (ModelPreset p : info.models())
        assertTrue(..., langKeys.contains(p.displayNameKey()));  // (b)
}
```

`verify-lang-placeholders` 已經在解析那些 JSON 檔了，`langKeys` 是現成的；`ProviderInfo` 現在沒有 Minecraft 依賴，可以直接 import。**這兩條加上去，M1 才算收完。**

## N2.〔小・一行〕啟動時 log 了，但真正炸的時候還是一個沒有訊息的 NPE

`checkProviderRegistriesAreComplete()` 對缺登記的情況是 `LOGGER.error` + 繼續跑。你選這個而不是丟例外的理由（我自己說了沒查證 NeoForge 對 `FMLClientSetupEvent` 例外的行為）我接受。

**但那行 log 在實務上會被錯過**——mod 載入階段的 log 幾百行，沒有人在遊戲正常啟動的情況下去讀它。真正會被看到的是後面那個崩潰，而 `ProviderConfigResolver.resolve()` 目前是：

```java
Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(endpoint);   // 缺登記 -> null
String apiKey = keys.apiKey() != null ? ... ;                          // -> 沒有訊息的 NPE
```

**建議：** 加一個明確的 null 檢查，丟一個**訊息裡有 endpoint 名稱**的例外。一行，把「神秘 NPE」變成「自我解釋的錯誤」，而且跟 `ProviderAdapterRegistry.forEndpoint` 的失敗風格一致（那個本來就是大聲丟）。啟動的 log 留著當早期預警，這行當實際發生時的說明。

## N3.〔不是缺陷・是給完成報告的一個建議〕preset model id 的實地驗證管道，你已經內建好了

你老實列出「11 個 provider 裡有 10 個沒有用真實 key 打過真正的網路請求」——這個誠實我接受，而且我沒有辦法幫你驗證那些 id。

**但你這輪剛好把驗證工具做進產品裡了**：Test Connection 打的是 list-models，`modelAppearsInConnectionTestResponse` 回 false 就顯示黃字提示。**那正是「我們的 preset id 是不是過時了」的檢查器。**

**建議寫進完成報告：** 告訴使用者「當你替某個 provider 填入真實 API Key 之後，按一次測試連線；如果出現黃色的『清單中找不到模型』，代表我們內建的 preset id 對那個供應商已經過時，請回報」。這樣那 10 個沒驗證過的 provider 就有了一條**使用者自己走得動**的驗證路徑，而不是停在「我們沒測過」。

---

## 我可能錯的地方

**N1 (b) 我只抽查了 `provider.*` 那組 key 存在於 `en_us.json`，沒有逐一比對 `ProviderInfo` 裡所有的 `ModelPreset` key。** 如果某個 preset 的 key 其實已經漏了，那現在就已經是壞的、不只是「沒有守衛」。加上那兩條斷言會順便回答這個問題——這也是我建議加它們的另一個理由。

---

## 下一步

N1 的兩條斷言請補上（M1 收尾）、N2 一行、N3 寫進報告。這三件之後我對這輪沒有其他意見。

commit 切分你說下一封信提出來，我到時候看。提前講一個原則：**這輪的 `libs/provider/` 是新抽象層、`Config` 的 per-provider 欄位是資料遷移、GUI 的 Custom Provider 面板是新畫面、model preset 清單是資料**——這四類的回退風險完全不同（尤其資料遷移那一塊，玩家的設定檔會被動到），建議切分時以「回退時會不會動到玩家的 TOML」當第一條分界線，而不是以檔案位置。
