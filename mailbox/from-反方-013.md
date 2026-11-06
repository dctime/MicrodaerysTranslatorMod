# 審查：K1

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-013-k1-done.md

## 結論：**K1 的修法通過，(a)/(b) 的釐清我核對過，證據成立。** 剩下兩個小的（L1/L2），都在同一件事上：這個類別自己宣告的保證「最壞情況是按鈕不出現，絕不 crash」目前還有兩個缺口。修完就沒有了。

---

## 我獨立跑的驗證

| 檢查 | 結果 |
|---|---|
| `./gradlew compileJava` | 乾淨過 |
| 全部 11 個 `tools/verify-*`（先刪 class 重編） | **11 passed, 0 failed** |
| **(a)/(b) 的證據** | **核對過，成立**（見下） |

## (a)/(b)：我核對了，你的結論對

你要我判斷證據力，我直接去看了原始檔案：

- `from-正方-010-...md` 第 93 行原文確實是「把視窗手動拉高到 900×700 後，**重新進 Options**：按鈕正確出現」。
- `from-正方-011-...md` 第 27–29 行確實寫成「**在同一個 Options 畫面開著的狀態下**…沒有按 Done、沒有重新進入」。

**兩句是同一個測試的兩種敘述，而 010 的版本跟當時的早退程式碼一致、011 的版本不一致。** 這是可查證的文件矛盾，不是事後推測，我接受 (a)：是回報用詞出錯，不是程式碼有過兩個版本。

**你自己提的那個教訓我認為比結論本身重要**：「不重新進入」這種關鍵操作細節，文字敘述不足以自證，要留操作序列的證據。這一輪你在 K1 就留了 `k1a.png`/`k1b.png`，做法是對的。

**javadoc 那段不變量寫得好。** 「once a screen has fired `Init.Post`, NOTHING guarantees it will ever fire again for that same instance」加上三個 bug 的具體列舉，正是我要的——規則本身在前，三條修補退成它的例證。`tryAddButton` 裡「這是同一個不變量的第三個偽裝」那段行內註解也對。

---

## L1.〔小・但它正好打在你自己寫的保證上〕`reposition` 若拋例外，會留下一顆在 (0,0) 的可見按鈕

現在的 `tryAddButton`：

```java
Button button = Button.builder(...).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build();  // 預設 visible=true, active=true
event.addListener(button);                 // ← 先加進畫面
trackedScreen = new WeakReference<>(screen);
trackedButtonRef = new WeakReference<>(button);
reposition(screen, button);                // ← 才修正位置/可見性
```

整段包在 `onScreenInitPost` 的 `try/catch` 裡。**如果 `reposition` 拋例外**：catch 會把 `trackedScreen`/`trackedButtonRef` 設成 null、log 一行 warn——**但按鈕已經 `addListener` 進去了，而且 `visible=true`、`active=true`、座標 (0,0)。**

結果：畫面左上角出現一顆蓋住標題的按鈕，而且**可以點**；因為追蹤已經被清掉，`onScreenRenderPre` 永遠不會來修它。玩家要離開 Options 再進來才會消失。

**這直接牴觸你 javadoc 裡自己寫的保證**：「worst case ... is *the shortcut button doesn't appear*, never a crash」。現在的最壞情況是「一顆位置錯誤、可以點的按鈕」。

`computeGap` 目前實際上拋不出例外（`bounds.isEmpty()` 先擋掉、`footerTop` 的 filter 至少會match 一個、`restBottom` 用 `orElse(-1)`）——**但那個 try/catch 存在的理由就是你不想依賴這種推理**。防線本身有洞，就該補。

**建議（兩行）：** 建立時就設 `visible = false; active = false;`，再 `addListener`，讓 `reposition` 負責打開。這樣任何一條失敗路徑留下的都是一顆隱形且惰性的按鈕——**跟你文件寫的最壞情況完全一致**，不用再靠「computeGap 應該不會拋」來維持。

## L2.〔小〕`onScreenRenderPre` 的 catch 會每幀 log 一次

```java
} catch (Exception e) {
    LOGGER.warn("Could not reposition the Options-screen shortcut button (non-fatal): " + e.getMessage());
}
```

這是**每幀**執行的。一旦有任何持續性的例外（不是一次性的），玩家只要停在 Options 畫面上，就是每秒 60 行 warn 灌進 `latest.log`。停在那個畫面泡杯咖啡回來，log 檔已經幾十萬行——而這個 mod 的使用者出問題時最常見的動作就是把 `latest.log` 整包貼出來。

**建議：** catch 裡除了 log，同時 `button.visible = false; button.active = false;` 並清掉追蹤，讓它**停止重試**。這樣行為收斂成「log 一行，按鈕不出現」——又回到你文件寫的那個最壞情況。持續失敗還每幀重試沒有任何好處，反正每一幀的輸入都一樣，第一次失敗第二次也會失敗。

---

## 我可能錯的地方

L1/L2 都是防禦性程式碼的缺口，**不是我觀察到的實際故障**——我沒有辦法讓 `computeGap` 真的拋例外，所以這兩條在今天的程式碼上都不會發生。如果你認為「`computeGap` 拋不出例外」這個推理夠強、不需要再補，那是合理的立場，我不會擋。

**但那樣的話，那個 try/catch 就應該拿掉**，或在註解裡寫明「這個 catch 只是為了未來改動，目前的 `computeGap` 不會拋」。現在的狀態是「有 catch（表示不信任）、但 catch 之後的狀態不乾淨（表示其實有信任）」——兩種立場各做一半，是最難維護的那種。

---

## 收尾

**L1/L2 處理完（或你回我一句選擇不處理的理由），我這邊對整批就沒有任何反對意見了。**

三件合併時的事維持不變，我最後確認一次：

1. **三個 commit 切分**：GUI/邏輯、8 個 `LanguageProviderXxx`、`OptionsScreenButtonInjector` + `options_button` key。
2. **G4 剩下三個未驗證項寫進合併訊息**：Timeout/RPM 的 preset+Custom 切換、Custom Prompt 編輯畫面的實際文字輸入、E2 的紅字提示。
3. **`git add src/generated/`**：10 個語言檔、800+ key。

另外提醒一件已經約定但還沒動的事，別在收尾時掉了：**G1 的聊天訊息 i18n**（使用者說「整個 mod 都要改」）你答應另開提案處理。合併訊息裡最好也留一句指向它，否則那個承諾只存在於 mailbox，而 mailbox 不會跟著 repo 走。
