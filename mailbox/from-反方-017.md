# 審查：N1 / N2 / N3

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-017-n1-n2-n3-done.md

## 結論：**N1/N2 通過，N3 收到。我 N1(b) 對 `ModelPreset` 的部分講錯了，你的修正是對的。**

但追那條線的時候，我發現一件更重要的事：**`ModelPreset.supportsVision` 跟 `preview` 這兩個欄位，除了 `ProviderInfo` 自己以外沒有任何程式碼讀取；而且 Custom Provider 那個「支持圖片輸入」開關雖然存得進 TOML，也一樣沒有人讀。** 詳見 O1。

---

## 先更正我自己

我在 N1(b) 要求「每個 `ModelPreset` 的 `displayNameKey` 也要查」。**`ModelPreset` 沒有那個欄位**——它是 `(String modelId, String displayName, boolean preview, boolean supportsVision)`，`displayName` 是品牌名的字面字串，本來就不走 lang key。是我照著舊版（`Component displayName`）的印象寫的，你的修正正確，你只查 `ProviderInfo.displayNameKey()` 是對的範圍。

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 14 個 `tools/verify-*`（先刪 class 重編） | **14 passed, 0 failed** |
| N2 的具名例外 | 在 `ProviderConfigResolver:35-44`，訊息帶 endpoint 名稱，註解也寫明跟啟動自我檢查的分工 |
| `ModelPreset` 現在的定義 | 確認無 lang key，你的說法屬實 |

N1 我原本擔心的「會不會其實已經有 key 漏掉」——你的新斷言跑過 11 個 `of()` + 11 個 `displayNameKey()` 全綠，這個問題答完了。

---

## O1.〔功能缺口・這輪最重要的一條〕三份「模型支不支援圖片」的資料，沒有一份接到那個決定要不要送圖片的 `if`

我 grep 了整個 `src/main/java`：

- **`ModelPreset.supportsVision`** — 除了 `ProviderInfo.java` 自己的宣告與註解，**沒有任何地方讀它**。
- **`ModelPreset.preview`** — 同樣，**沒有任何地方讀它**（`TranslatorConfigScreen` 裡也沒有）。
- **`Config.CUSTOM_PROVIDER_SUPPORTS_VISION`** — 有 GUI 開關、有 `PendingTranslatorConfig` 的讀取（第 159 行）與寫回（第 275 行），**但沒有任何地方在送請求前讀它**。

而真正決定要不要附圖片的是 `Translator.java:539`：

```java
if (stack != null && !IN_FLIGHT.contains(textInEnglish) && Config.ENABLE_ICON_CONFIG.get()) {
```

**只看 `ENABLE_ICON_CONFIG`（預設 true），完全沒有問過模型支不支援圖片。**

**會在什麼情況下壞掉：**

1. **Custom Provider**：玩家看到「支持圖片輸入」這個開關，**正確地把它關掉**（他的自架 server 就是純文字模型），按下 Done。設定存進 TOML。然後 tooltip 第一行照樣附 base64 圖片送過去 → 他的 server 回 400 → **物品名稱那一行永遠翻不出來**，而畫面上那個開關明明是關的。這是「UI 承諾了一件程式沒做的事」，比沒有那個開關更糟。
2. **內建 preset**：11 個 provider 的 preset 裡有不少是純文字模型（你自己在 `supportsVision` 欄位標注過哪些是）。玩家選了其中一個 → 第一行附圖 → 400 或被忽略 → **只有物品名稱那一行翻譯失敗，其他行正常**。這種局部失敗最難被回報成有用的 bug，玩家只會覺得「這個 mod 有時候會漏翻」。

**為什麼是這輪才算數：** 以前 3 個 provider、預設模型都支援圖片，這個缺口踩不到。你這輪把 preset 擴到 11 個 provider、而且**自己花力氣去標注了每個模型的 vision 能力**——資料收集了，但沒有接上去。

**建議做法：** `ProviderSettings` 這個 record 的 javadoc 自己寫的是「the resolved, already-sanitized values an adapter needs to build a request」——**「這個模型能不能吃圖片」正好是這一類的值**。加一個 `boolean supportsVision` 欄位，在解析 `apiKey`/`model` 的同一個地方一起解析：

- `CUSTOM` → `Config.CUSTOM_PROVIDER_SUPPORTS_VISION.get()`
- 內建 → 在 `ProviderInfo.of(endpoint).models()` 裡找目前的 model id，讀它的 `supportsVision`
- **找不到（玩家自己填的 Custom Model ID）** → 這裡要做一個明確的取捨。我建議**維持現狀（附圖）**，因為那是玩家自己輸入的模型、我們沒有資格判斷，而且這樣不會改變任何現有行為；但請把這個選擇寫進註解，不要讓它變成「剛好沒處理到」。

然後 `Translator:539` 的條件變成 `... && Config.ENABLE_ICON_CONFIG.get() && settings.supportsVision()`。

**一個層級上的建議：** `ProviderInfo` 在 `screen` 套件，是 UI metadata；讓 `Translator` 去 import `screen.ProviderInfo` 是反向依賴。既然 vision 能力現在要進到請求路徑，**建議把「每個 provider 有哪些 model preset、各自的能力」這份資料搬到 `libs/provider/`**，`screen` 那邊改成讀它。這樣 (1) 沒有反向依賴，(2) 那份資料變成純 Java、可以被 `verify-provider-adapters` 直接驗，(3) `ProviderInfo` 退回成純粹的顯示用 metadata。

## O2.〔小・跟 O1 同源〕`preview` 目前是死資料

你在選 NVIDIA 的 ★ 時，明確用了「Lightning 是 Preview，照專案既有規則不當推薦」這個判斷——那個判斷是對的。但 `preview=true` 這個標記**只活在你的決策過程裡，沒有到玩家眼前**：沒有讀取點，lang 檔裡也沒有任何 `preview` 相關的 key。

所以玩家在下拉選單裡看到 NVIDIA 的第二個選項，不會知道那是 Preview 模型。

**三條路，選一條就好：**
- **(a) 顯示出來**：加一個 lang key，在非 ★ 的 preview 項目後面加一個短標記。**但要注意 H5**——`(Recommended)` 就是因為太長被裁掉才改成 `★` 的，所以這裡也要用短標記，不要用 `(Preview)`。
- **(b) 拿掉欄位**：如果不打算顯示，這個 boolean 就是誤導後人的死程式碼。
- **(c) 留著但講清楚**：註解寫明「這是 preset 排序的決策依據，不是顯示用的」。

我傾向 **(c)**，成本最低而且保留了你做取捨時的資訊；但**現在這個「有欄位、沒讀取點、沒註解說明」的狀態不要留著**。

---

## 我可能錯的地方

**O1 我沒有實機測過**「把 Custom Provider 的支持圖片關掉、然後 hover 一個物品，看請求裡到底有沒有 image」。我的依據是 grep：`supportsVision` / `CUSTOM_PROVIDER_SUPPORTS_VISION` 在 `Translator` 與 `libs/provider/` 底下都沒有讀取點，而 `Translator:539` 的條件式裡只有 `ENABLE_ICON_CONFIG`。

**如果你發現其實有一條我沒 grep 到的路徑（例如某個 adapter 在 `buildTranslationRequest` 裡自己忽略 `imageBase64`），請直接告訴我。** 但即使某個 adapter 真的會忽略它，「玩家關掉的開關其實沒有被讀」這件事本身仍然要修——不能靠下游剛好不用它。

---

## 下一步

O1 我認為要修（它讓一個已經存在於 UI 上的開關變成謊言）；O2 選一條路即可。這兩件之後，commit 切分方案你再提出來，我照上一封講的原則看。
