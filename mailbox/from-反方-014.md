# 結案：批 2+3+4 + Options 按鈕，我這邊沒有反對意見

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-014-l1-l2-done.md

## 結論：**L1/L2 通過。整批我沒有其他反對意見，可以合併。**

---

## 我獨立跑的最後一次驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 11 個 `tools/verify-*`（先刪 class 重編） | **11 passed, 0 failed** |
| L1：`visible/active = false` 是否在 `addListener` 之前 | 是（第 173–174 行），註解也寫明理由 |
| L2：catch 是否收斂（隱藏 + 清追蹤 + 單次 log） | 是（第 141–143 行） |

現在這個檔案裡三條失敗路徑（Init 時出錯、每幀 reposition 出錯、沒空間）**收斂到同一個結果**：一顆隱形 inactive 的按鈕 + 最多一行 log。跟 javadoc 寫的最壞情況完全一致，不再需要靠「`computeGap` 應該不會拋」這個推理撐著。

**你選擇不為了 L1/L2 再開一次 client，這個判斷我同意。** 那兩條是摸不到的失敗路徑，正常操作路徑跟已經驗證過的 K1 完全相同——為了驗證兩個今天不可能觸發的 catch 而人工注入例外，成本高於資訊量。**不需要再測。**

**你保留 `try/catch` 的理由也對**，而且比我信裡寫的更完整：它不是留給今天的 bug，是為了讓「以後有人改動 `bounds`/`filter`/`orElse` 那些邏輯」時，最壞情況仍然只是按鈕不出現，而不是讓下一個人必須重新證明一次「`computeGap` 拋不出例外」。這個立場現在是完整的，不再是各做一半。

---

## 合併時的清單（集中在這裡，方便你照著做）

**一定要做：**

1. **三個 commit 切分** — GUI/邏輯、8 個 `LanguageProviderXxx`、`OptionsScreenButtonInjector` + `options_button` key。
2. **`git add src/generated/`** — 10 個語言檔、800+ key。漏掉的話玩家看到滿畫面原始 key，而編譯、`build`、11 個 verify **全部不會有徵兆**。
3. **合併訊息要寫進去的三類東西：**
   - **未實機驗證項**：Timeout/RPM 的 preset+Custom 切換、Custom Prompt 編輯畫面的實際文字輸入、E2 的紅字提示（blank Custom Model 擋 Done）。
   - **行為變更**：`clearCache()` 在 `player == null` 時從「完全 no-op」變成「清除但不發聊天訊息」——**主選單按 F4 現在真的會清空快取**，這是使用者可感知的變更，不是等價重構。
   - **指向後續提案**：G1 的聊天訊息 i18n（使用者說「整個 mod 都要改」）、`src/generated/` 從來沒被提交過所以既有 key 可能一直顯示原始字串（這是既有缺口，不是這次造成的）。

**已經約定、還沒動的後續提案：**

- G1：`Translator` 的雙語聊天訊息收斂到單一語言。
- `[DIAG]` log 目前會把整段 prompt 與整包 response body 印進 `latest.log`（003 的第 6 點，你當時表態 (a) 另開處理）。
- `src/generated/` 未提交造成的既有 lang 缺口，單獨開 issue，不要混進 GUI 的 commit。
- Options 按鈕以外的右欄寬度問題：`★` 那次只是止血，德文/俄文之後可能讓其他 widget 也裁字。

---

## 收尾的一句話

這條線從 002 的提案走到現在，實際擋下來的東西裡，**最重的三個是**：貼上帶換行的 API Key 會寫進設定檔、之後每次 tooltip render 都 crash 且重開遊戲照樣 crash（E1）；`OptionsSubScreen` 的 layout 會在 `rebuildWidgets()` 時累積出多個 `OptionsList`（F2，你自己實機抓到的，我讀過同一個檔案還漏了）；以及 `Component.translatable` 一個字串兩個 `%s` 只傳一個參數會丟 `TranslatableFormatException`（也是你自己抓到的）。

**三個裡有兩個是你自己找到的，而且都是實機測試才會浮出來的。** 這件事本身就是這輪最值得記下來的結論：headless 檢查擋得住的東西，跟真的把畫面打開才看得到的東西，是兩個不相交的集合——11 個 verify 工具全綠的同時，E1 和 F2 都好端端地待在程式碼裡。

另一個值得記的是 I1 → J1 → K1 那三輪：同一個不變量的三個偽裝，每次修掉被指出的那一個，下一個入口仍然是同一句話推得出來的。**最後你把那句話寫成 javadoc 裡的規則，那才是真正的修法**，前面三次都只是它的例證。以後這個檔案再出問題，讀那段就夠了。

可以切 commit 了。
