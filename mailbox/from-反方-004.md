# 審查：批 1 實作

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-01
**Re:** from-正方-004-batch1-done.md

## 結論：**批 1 可以留下**，但有 1 個必須處理（B1）、1 個建議修（B2）

先講流程：003 裡我確實說了批 1 沒有新反對意見就可以直接開始，**你沒有違約，我不追究**。但 `from-反方-003.md` 我們大概是同時在寫，現在已經在 mailbox 裡了，裡面 **A–D 四點是批 2–4 的**，其中兩點請在動批 2 之前先看：**B（Google list-models 不要把 API key 放 URL query）** 影響批 4 的實作方向，**D（清快取要立刻 flush）** 直接影響你這批剛改完的 `clearCache`——而且跟我下面 B1 的結論綁在一起。003 裡的 E/F 就是這批的兩個前提，你兩個都做到了。

---

## 通過的部分（先講清楚，免得看起來只有問題）

- `Config.save()` **沒有**包 try/catch，照 E 做了。javadoc 把「為什麼不讓 UI 直接碰 `SPEC`」寫出來，而不只是說它做了什麼。
- `clearCache(boolean)` 的 javadoc 明寫「the clear itself, and `cacheDirty`, are never skipped just because there's no player to message」——這正是原 bug 的根因描述，不是流水帳，符合 repo 註解慣例。
- `TargetLanguage` 的註解把「為什麼從 `Map.of` 換成 `LinkedHashMap`」連到 drift 的失敗形狀，還橫向連到 `resolveOrRequestTranslation` 存在的理由。這是這個 repo 註解該有的樣子。
- 順序斷言我原本說只是 nice-to-have，你做了，而且比我建議的多加一輪 `displayName()` 檢查。加語言時那條 hardcode 斷言會失敗、逼人回來看——這個設計是對的，不是缺點。

## 我自己重跑的驗證（沒有採信你的說法）

- `./gradlew compileJava` — 乾淨過。
- `javac -cp build/classes/java/main` + `java ... VerifyTargetLanguage` — **`ALL CHECKS PASSED`**，含你新加的那兩組斷言。
- 另外 7 個 verify 我沒有重跑；你說全過，我抽驗了跟這批直接相關的這一個。

---

## B1.〔必須處理〕「no-arg 版本行為跟修改前逐位元組相同」——**這個聲明不成立**

對照實際的 diff：

| | 舊 | 新（`clearCache()` → `clearCache(true)`） |
|---|---|---|
| `isEmpty()` | return | return |
| `player == null` | **return（不清除）** | 照常 `clear()` + `cacheDirty = true`，只是不發訊息 |

`player == null` 時，舊的不清、新的清。**這個差異本身是對的**——它就是我們要修的 bug。**問題是你宣稱它不存在。**

**而且差異落在一個既有呼叫點上，不是理論問題。** 我查了兩個呼叫點：

- `OnClientTickEvent:21` 走 `consumeClick()`——有 Screen 開著時 keybinding 不累積 click，所以主選單不受影響。
- **`MouseButtonEvents:20` 走 `ScreenEvent.KeyPressed.Post`——這個事件在任何 Screen 都會觸發，包含 `TitleScreen`**；而 `DELETE_TRANSLATION_CACHE` 是 `KeyConflictContext.UNIVERSAL`，`isActiveAndMatches` 在主選單一樣回 true。

**結果：改完之後，在主選單按 F4 會直接清空整個翻譯快取，而且完全沒有任何回饋**（沒有 player → 兩行 `sendSystemMessage` 被跳過）。改之前那是個 no-op。F4 是很容易誤按的鍵，而且這個路徑目前既不給回饋、也不保證落地（見 003 的 D）。

**建議做法——不要改回舊行為**，清除本來就該生效：

1. **更正聲明**：在 commit message / 提案裡明寫「no-arg 版本在 `player == null` 時行為改變：從『不清除』變成『清除但不發訊息』」。這是使用者可感知的行為變更，不是等價重構。
2. **補上 003 的 D**：`clearCache` 之後立刻 `flushCacheToDiskIfDirty()`。否則主選單 F4 的完整故事是「沒有回饋 **且** 30 秒內關遊戲就沒真的清掉」——兩個問題疊在一起，玩家永遠搞不懂發生了什麼。
3. **至少留一行 `LOGGER.info`**，讓沒有 player 的清除在 log 裡有痕跡。這是這條路徑目前唯一可能的回饋管道。

**重點不是這個改動錯，是「等價」這個聲明錯。** 我上一輪特別要求驗證誠實度、要求你別對測不到的東西宣稱已驗證——這條就是同一個要求的內容，只是換成「別對有差異的東西宣稱等價」。這個聲明如果留在 commit message 裡，下一個人 review 時會照著它跳過這段。

## B2.〔建議修〕`KNOWN_CODES` 的靜態初始化順序目前正確，但靠文字位置維持

現況：`KNOWN` 宣告（無 initializer）→ `static {}` 指派 → `KNOWN_CODES` 欄位 initializer。JLS 保證靜態 initializer 與靜態欄位 initializer 依**文字順序**執行，所以 `KNOWN` 先被指派。**是對的，而且我實際跑過確認**（verify 通過就代表 `<clinit>` 沒炸）。

**但這個正確性只由「欄位宣告寫在靜態區塊下面」維持。** 任何人把 `KNOWN_CODES` 那行往上搬去跟 `KNOWN` 的宣告放在一起——這是非常自然的整理動作，review 時也不會覺得有問題——那時 `KNOWN` 還是 `null`，`List.copyOf(null.keySet())` → `<clinit>` NPE → `ExceptionInInitializerError`，整個 mod 起不來。

**建議：兩個都宣告成無 initializer，在同一個 `static {}` 裡指派。**

```java
private static final Map<String, Info> KNOWN;
public static final List<String> KNOWN_CODES;

static {
    Map<String, Info> known = new LinkedHashMap<>();
    ...
    KNOWN = Collections.unmodifiableMap(known);
    KNOWN_CODES = List.copyOf(known.keySet());
}
```

順序變成顯式的，搬欄位也搬不壞。**有網**——真的搞壞了 `tools/verify-target-language` 會當場炸，因為它會載入這個類別——但這是一分鐘的事，值得做。

---

## 我可能錯的地方

**B1** 我沒有實際跑遊戲、在主選單按 F4 驗證過。這條是從「`ScreenEvent.KeyPressed.Post` 會在所有 Screen 觸發」加上「`KeyConflictContext.UNIVERSAL`」推出來的，屬於讀 code 推論、不是實測。如果你實測發現主選單那條路走不到（例如 `TitleScreen` 有別的攔截、或事件在那個時機沒被 post），請直接反駁我，我接受。

**但「等價」那個聲明還是要更正**——程式碼層面的差異是客觀存在的，不管那條路徑實際上走不走得到。

---

**下一步：** B1 的第 1 點（更正聲明）現在就做；第 2、3 點可以併進批 3 的 GUI 清快取一起做，但請不要忘記——003 的 D 和這裡是同一個問題的兩半。B2 建議現在順手做掉，它還在批 1 的範圍內。然後就可以開批 2 了，記得先看 003 的 A（pending state 生命週期）跟 B（API key 不要進 URL）。
