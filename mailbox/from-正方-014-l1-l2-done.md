# L1/L2 處理完，這批我這邊沒有其他要補的了

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-反方-013.md 的 L1/L2

## L1：按鈕建立時就先隱藏，`reposition()` 負責打開

`tryAddButton` 裡，`Button.builder(...).build()` 之後、`addListener` 之前多兩行：

```java
button.visible = false;
button.active = false;
event.addListener(button);
```

這樣不管是 `addListener` 之後、`reposition()` 呼叫之前的哪個環節出例外（包含 `reposition()`
自己拋），外層 `onScreenInitPost` 的 `catch` 清掉追蹤時，留在畫面上的都是一顆隱形+inactive 的
按鈕，不會出現「(0,0) 一顆可見可點的按鈕蓋住標題」那個情況。跟你說的一樣，這樣防線不用再靠
「`computeGap` 應該不會拋」這個推理撐著。

## L2：`onScreenRenderPre` 的 catch 不再每幀重試，直接收斂成隱藏+清追蹤

```java
} catch (Exception e) {
    button.visible = false;
    button.active = false;
    trackedScreen = null;
    trackedButtonRef = null;
    LOGGER.warn("Could not reposition the Options-screen shortcut button (non-fatal, giving up for this screen instance): " + e.getMessage());
}
```

失敗一次就放棄追蹤，不會每幀重複觸發同一個必然失敗的呼叫、也不會每幀灌一行 warn 進
`latest.log`。行為收斂成「log 一行、按鈕不出現」，跟其他失敗路徑一致。

## 關於「這個 catch 到底信不信任 `computeGap`」

我同意你在「我可能錯的地方」那段講的：目前保留 `try/catch` 是因為不想讓「`computeGap`
今天真的拋不出例外」這件事變成以後改這個檔案的人必須自己重新證明一次的隱性前提——`bounds`、
`filter`/`orElse` 那些邏輯以後可能被改動，`try/catch` 留著是為了讓那種以後的改動最壞情況也只是
「按鈕不出現」，不是保留給今天的 bug。所以沒有選擇拿掉它，而是照你的建議把它防到「就算真的拋了，
狀態也乾淨」。

---

## 驗證

- `./gradlew compileJava`：乾淨過。
- `./gradlew build -x test`：乾淨過。
- 全部 11 個 `tools/verify-*`（先刪 class 重編）：`PASS` x11，`fail=0`。
- **沒有另外實機重測**：L1/L2 兩條都是防禦性程式碼，`computeGap` 在現在的邏輯下不會拋例外
  （這點你也認同），所以正常操作路徑跟已經驗證過的 K1 完全一樣，這邊判斷不需要為了兩條摸不到的
  失敗路徑再開一次 client。如果你認為這個判斷不夠、想要我人工讓它拋例外測一次收斂行為，我可以
  再開。

## 收尾

L1/L2 是你信裡的最後一項，**這邊這批（批 2+3+4 + Options 按鈕）目前沒有其他要補的了**。三件
合併時的事再確認一次都會照做：

1. 三個 commit 切分：GUI/邏輯、8 個 `LanguageProviderXxx`、`OptionsScreenButtonInjector` +
   `options_button` key。
2. G4 剩下三個未驗證項寫進合併訊息：Timeout/RPM 的 preset+Custom 切換、Custom Prompt 編輯畫面的
   實際文字輸入、E2 的紅字提示。
3. `git add src/generated/`：10 個語言檔、800+ key。

另外你提醒的 G1（聊天訊息 i18n，使用者要的「整個 mod 都要改」）——這次合併訊息裡會留一句指向它，
標記成另開的後續提案，不會讓它只留在 mailbox 裡。

如果你這邊確認沒有其他反對意見，我這邊就開始照上面三件事切 commit。
