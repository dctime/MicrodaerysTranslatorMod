# 審查：P1 / P2

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-019-p1-p2-done.md

## 結論：**兩條都通過，這輪沒有新問題。** 一個給 G1 的前瞻建議（不擋這輪）。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 14 個 `tools/verify-*`（先刪 class 重編） | **14 passed, 0 failed** |
| `hasShowVisionUnsupportedError` 是否真的全部移除 | **0 個殘留**（欄位、兩個 lambda、`resetHttpErrorFlags()` 裡的重置都清乾淨了） |
| 10 個語言檔 key 集合互相比對 | **0 problems**，各 100 key |
| 跨語言 `%s` 數量一致性 | **0 mismatch** |
| 新 key 是否 10 個檔都有 | **是** |

**新 key 的翻譯我抽查了三個語言，是真的原生撰寫、不是逐字轉英文：**

- `en_us`：`... does not support image input -- try a different model in the config screen.`
- `ko_kr`：`... 설정 화면에서 다른 모델을 선택해 보세요.`
- `de_de`：`... versuche ein anderes Modell im Einstellungsbildschirm.`

三個都把「去設定畫面換一個模型」這個可行動建議講出來了，句式各自符合該語言的習慣。這正是 P1 想要的——那則訊息是螢幕翻譯路徑唯一的輸出，玩家看得懂才有意義。

**P1 選 (a) 而且沒有順手擴大範圍**：`LanguageProvider` 新增的那段註解明寫「這不是 G1 的全面重構，其他既有雙語訊息照舊」。只把「這輪新加的、而且是在基礎設施蓋好之後才加的」那一則轉成真 key——範圍拿捏正確，既沒有多加一筆債，也沒有把 G1 提前吞進這輪。

**P2 的論證你接受得很乾淨**，而且是整個移除欄位而不是留著不用。

---

## 給 G1 的前瞻建議（不擋這輪，供你寫提案時參考）

現在有一個結構性缺口，這輪還不痛，但 **G1 一開始做就會痛**：

**沒有任何東西驗證「Java 程式碼裡引用的 lang key 真的存在於語言檔裡」。**

- `verify-lang-placeholders` 驗的是「10 個檔案彼此一致」——它抓得到「某個 key 只有 9 個語言有」，但抓不到「程式碼引用了一個 10 個檔案都沒有的 key」。
- 唯一有做這件事的是 `verify-provider-adapters` 裡我請你加的那條，而它只涵蓋 `ProviderInfo.displayNameKey()`。
- 這輪新加的 `microdaerystranslator.translator.vision_unsupported` **沒有被任何檢查守著**（它現在是對的，我查過了；但沒有人在守它）。

G1 會把 `Translator` 剩下的那幾則雙語訊息全部轉成 `Component.translatable`，一次新增大約 6 個 key。**打錯任何一個字，結果是玩家在聊天欄看到原始 key 字串，而編譯、`build`、14 個 verify 全部不會有徵兆**——跟我提醒過三次的 `src/generated/` 手滑是同一種病。

**建議 G1 順手做掉：** 把那幾則訊息的 key 收進一個小的純類別（比照 `ProviderInfo` 的做法），例如 `TranslatorMessageKeys` 裡一組 `public static final String`，然後在 verify 裡對那個類別的每個常數斷言它存在於 `en_us.json`。這樣：

1. 程式碼引用的 key 有了單一真相來源，不再是散落各處的字串字面值；
2. 檢查是純 Java、材料現成（`verify-lang-placeholders` 已經在解析那些 JSON）；
3. 以後再加訊息，忘記加 lang key 會在 verify 就紅，不是等玩家回報。

**這輪不用做**——這輪只有一個 key，而且它是對的。我提出來是因為它應該寫進 G1 提案的範圍，而不是等 G1 做完之後我再要求補。

---

## 下一步

O/P 系列收斂了，我這邊沒有其他意見。commit 切分方案你提出來，我照原則看：**第一條分界線是「回退時會不會動到玩家的 TOML」**，其中 per-provider 欄位那一批的訊息要回答「回退後舊版讀不讀得懂新長出來的區塊」。
