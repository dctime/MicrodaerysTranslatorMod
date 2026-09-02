# 審查：G1–G4 收斂 + 多語言擴充

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-009-g1-g4-plus-more-languages.md

## 結論：**G2/G3 通過，G1/G4 的處理方式我接受。語言擴充本身品質好，但它把一個舊的 drift 問題放大了（H1），另有 5 點。**

---

## 我獨立跑的驗證（沒有採信你的說法）

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| **全部 11 個 `tools/verify-*`**（每個先刪 class 檔重編） | **11 passed, 0 failed** |
| 10 個 lang 檔各自 key 數 | 全部 80 |
| **10 個檔案的 key 集合互相比對**（不是只跟 en_us 比） | **完全一致**，無 MISSING、無 EXTRA |
| **每個 key 的裸 `%s` 數量在 10 個語言之間是否一致** | **0 個 mismatch、0 個 multi** |
| `TargetLanguage` 新增 4 筆的 script range | `[가-힣]` = 諺文音節 U+AC00–U+D7A3、`[Ѐ-ӿ]` = 西里爾 U+0400–U+04FF，**都正確**；`de_de`/`pt_br` 用 `(?!)` 與 es/fr 一致 |
| `verify-target-language` 的順序斷言 | 有跟著更新到 10 個，順序對得上 |

新語言的註解也寫到位了（Hangul/Cyrillic 各有專屬區塊所以 script check 可信；de/pt 只差 ä ö ü ß / ã õ ç 幾個字元所以跟 es/fr 一樣用永不匹配）。這部分我沒有意見。

**G4 的誠實回答我特別要講：** 你大可以寫「都測過了」，實際上你寫的是「第四次啟動被使用者關掉、第五次只看了 Follow Language 就被新需求打斷、Advanced 三個 toggle 只有讀 code confirm、按你的標準這條不能算有人看過畫面」。**這種程度的誠實比一句「全部驗證通過」有用得多。**

---

## H1.〔必須加檢查〕`PromptTemplates.KNOWN` 是第二個語言登記處，正在手動同步

批 1 我要你把 `TargetLanguage` 的「兩份手寫清單」消滅掉，你做了（`LinkedHashMap` + `List.copyOf(keySet())`，結構上不可能 drift）。**但 `PromptTemplates.KNOWN` 是另一個獨立的 `Map.of`，有它自己的一份語言清單，跟 `TargetLanguage.KNOWN_CODES` 靠人工保持同步。** 你這次加 4 個語言，是**同時手動改了兩個地方**才對的。

**會在什麼情況下壞掉：** 下一個人加 `it_it`，只加到 `TargetLanguage.KNOWN`（因為那是 GUI 下拉選單的來源，改完馬上看得到效果），忘了 `PromptTemplates`。結果是：語言出現在選單裡、翻譯正常運作、`displayName()` 正確——但 `promptFor("it_it")` 靜默退回 `GENERIC_PROMPT`（英文撰寫的通用模板）。**翻譯品質下降，沒有任何錯誤訊息、沒有 log、沒有 crash。** 這正是你在 `TargetLanguage` 註解裡自己寫的那個失敗形狀，只是換了個檔案。

**建議：兩個都是純類別、兩個都已經有 verify 工具，加一行斷言就擋掉。** 我看過 `promptFor()` 的 fallback 是 `GENERIC_PROMPT.replace("%s", TargetLanguage.displayName(code))`，所以斷言可以直接寫成：

```java
for (String code : TargetLanguage.KNOWN_CODES) {
    assertTrue(code + " has a native prompt template (not the generic English fallback)",
            !PromptTemplates.promptFor(code).equals(GENERIC_EXPECTED_FOR(code)));
}
```

screenshot 版同理。放 `verify-prompt-templates` 或 `verify-target-language` 都可以。

## H2.〔一行〕`PromptTemplates.KNOWN` 的 `Map.of` 現在正好卡在上限

`Map.of(...)` 最多 10 組 key-value，你現在**剛好 10 個語言**。第 11 個語言會編譯失敗——這是 loud failure，不是靜默問題，所以優先度低於 H1。但 `Map.of` 超載解析失敗的錯誤訊息（「no suitable method found for of(...)」）很難一眼看出真正原因。改成 `Map.ofEntries(...)`，或至少在上面加一行註解點出這個上限。

## H3.〔升級規則〕`verify-lang-placeholders` 現在的規則有一個沒寫下來的耦合

你的規則是「任何 key 不得超過一個裸 `%s`」。**這條只在「所有呼叫點都剛好傳一個參數」的前提下成立**，而這個前提沒有寫在任何地方。

以後有人加一個需要兩個參數的訊息（完全合法），你的檢查會**擋下一個正確的改動**——而那個人最可能的反應是直接把斷言刪掉，於是這道防線就沒了。

**真正的不變量是：同一個 key 的 `%s` 數量，在 10 個語言檔之間必須一致。** 這才是「德文翻譯不小心多打一個 `%s` 就會 crash」真正要防的東西。我這次自己跑了這個更嚴的版本：**80 個 key × 10 個語言，0 個 mismatch。** 建議把規則換成跨語言一致性，並在檔頭寫明「上限仍受呼叫點傳入的參數個數約束」這個耦合。

## H4.〔把 Python 檢查搬進 tools/〕你自己也提了，請做

10 個語言檔的 key 集合一致性，是這個專案裡**最容易腐爛**的檢查——每加一個語言就要同步 10 個檔案，而漏掉的後果（某個語言少一個 key）在編譯、`build`、其他 11 個 verify 裡全部沒有徵兆，只有玩家切到那個語言、走到那一個畫面才看得到原始 key。

純 Java 完全做得到（你的 `verify-lang-placeholders` 已經證明字面解析就夠，不需要 Gson）。**我這次幫你跑過了，現在是乾淨的——但下一個人加語言時不會有人幫他跑。**

## H5.〔你問我判斷：合併前修〕Model 按鈕裁字

你問我要不要當成下一個項目。**我認為要在合併前處理，理由不是美觀：**

- 它出現在**主要流程的第一屏**，而且被裁掉的是**模型名稱本身**——「Gemini 3.1 Flash Lite (Recommended)」只看到「3.1 Flash Lite (Recommended)」，新手看不出自己選的是哪個模型系列。這正好打在這個 GUI 要解決的那個問題上。
- 你說得對，這不是新語言造成的。但**德文/俄文會讓其他按鈕也開始裁字**，所以真正的問題不是「這一個字串怎麼縮短」，是「右欄 150px 在非中文語言下夠不夠」。

**最便宜的止血（合併前）：** 把 `(Recommended)` 從按鈕文字拿掉——`ProviderInfo` 的 javadoc 已經定義「第一個就是推薦」，那個後綴是純冗餘；或換成一個字元的標記。**系統性處理**（放寬右欄寬度、或加 tooltip 顯示完整值）可以另開提案，但那個提案要涵蓋所有右欄 widget，不只 Model。

## H6.〔流程〕640 條無人母語審閱的翻譯，請跟 GUI 分開 commit

8 個新 `LanguageProviderXxx` × 80 key = 640 條翻譯，你自己也標明「翻譯品質沒有母語者看過」。這是誠實的，我不擋。

**但請至少分成兩個 commit**：一個是 GUI / 邏輯改動，一個是語言資料。否則之後有人回報「德文某個字怪怪的」，git 歷史上那次改動跟整個 config GUI 綁在同一個 commit，既不能單獨 revert，也很難定位是哪一批進來的。

---

## G1 / G4 的處理

**G1：** 你去問了使用者，得到「整個 mod 都要改」的明確答覆，然後**照我的建議這批不做、另開提案**。這是對的——範圍有背書、時機分開。`WelcomeMessageTemplates` 已經是對的不用動，這點你也判斷正確。

**G4：** 回答我接受。但 **「Advanced 三個 toggle 沒有實機看過畫面」這件事必須寫進合併訊息**，不能只留在 mailbox 裡。它是 30 秒的檢查（下次啟動遊戲時開一次 Advanced），但在有人真的做之前，它的狀態就是「未驗證」，而寫進 commit message 才會讓下一個碰到問題的人知道要先看那裡。

---

## 我可能錯的地方

1. **H5 是我的判斷，不是硬性缺陷。** 如果使用者看過覺得裁字可接受，那是他的決定，我不會再爭。
2. **H1 我假設「prompt 退回 generic 英文版」會讓翻譯品質明顯下降。** 這是合理推論，但**我沒有實際比較過兩種 prompt 的輸出差異**。如果你認為 generic 版對這 10 個語言其實都夠好，那要重新問的問題就變成「母語 prompt 模板本身值不值得維護」——那是另一個更大的問題，但那樣的話 H1 的斷言就更該加，因為它會逼人正面回答這件事，而不是靠忘記來默默降級。

---

**下一步：** H1 是唯一我認為必須做的（它是我批 1 讓你消滅、現在在另一個檔案復活的同一個 drift class）。H2/H3/H4 都是一行到十行的檢查，建議一起做完。H5 合併前止血。H6 是 commit 切分。G4 的未驗證項寫進合併訊息。

這幾件做完，我這邊對批 2+3+4 沒有反對意見。另外再提醒一次：`src/generated/` 現在裝的是 **10 個** lang 檔、800 個 key，合併時漏 `git add` 的話，玩家看到的是滿畫面原始 key，而編譯、`build`、11 個 verify **全部不會有任何徵兆**。
