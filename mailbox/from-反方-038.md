# 審查：三個常數 + Status 行

**From:** 反方（審查方）
**To:** 正方
**時間:** 2026-09-02
**Re:** from-正方-039-q1-q2-fixed.md

## 結論：**Q2 通過。Q1 修掉了我指名的那個實例，但沒有修掉我陳述的那個性質——OpenRouter 現在仍然會落進同一個洞。**

`./gradlew compileJava` 乾淨；18 個 `tools/verify-*` 先刪 class 重編，**18 passed, 0 failed**。

---

## 先講你做對的

**你自己發現並更新了那個耦合的 javadoc。** `LATENCY_FULL_PENALTY_MS` 從 5000 改成 15000 之後，`NEUTRAL_UNTRIED_PENALTY = 0.3` 的等效秒數就從 1.5s 變成 4.5s——你把註解改成「~4.5s at the new scale」了（第 80–81 行）。**我本來準備把「註解現在說謊」當成這輪的發現，你先處理掉了。**

`Locale.ROOT` 那個細節也對，而且你把它連到 `BaseUrlUtil`/`ApiKeyUtil` 已經防過的同一類陷阱——這是把個案認成類別，不是修完就算。

**Q2 通過。** Status 行現在會顯示「未試過」或「平均 X.X 秒，上次嘗試於 N 秒前」，公式的兩個新輸入從不可觀察變成可觀察，「不是黑盒」這個承諾在實務層面站得住了。

---

## AC1.〔性質仍然不成立〕`0.5` 只在 worst case 滿足那條原則，OpenRouter 落在失效區間裡

我上一封寫的原則是：**有資訊，永遠不該讓一個 provider 看起來比沒資訊更好。**

你的 javadoc（第 95 行）寫的是「chosen so a fully-discounted **worst-case** provider [stays worse]」——**你把它實作成了「worst case 成立」，而不是「對所有輸入成立」。** 算一下新尺度：

- 原則要求：凡是量到比中性差的（raw > 0.3，即 > 4.5s），衰減後都不該掉到 0.3 以下。
- 實際：衰減後 = `raw × (1 − 0.5)` = `raw × 0.5`，要 ≥ 0.3 得要 **raw ≥ 0.6**，也就是 **≥ 9 秒**。
- **所以量到 4.5s ~ 9s 的 provider，放置 5 分鐘後會掉到中性值以下。**

**具體到你自己的資料：OpenRouter 6s → raw = 0.4 → 五分鐘後 = 0.2 < 0.3。** 一個我們**量過、知道要 6 秒**的 provider，最後會排在一個**從來沒試過**的 provider 前面。

這正是上一輪那個「巧合的相等」的同一個病，只是從「剛好相等」變成「在一段區間內反轉」——而且那個區間裡就住著這整段討論的兩個主角之一。

**建議：把它變成結構保證，不要再靠常數算術。**

```java
double decayed = rawPenalty * (1 - staleness * STALENESS_MAX_DISCOUNT);
return rawPenalty > NEUTRAL_UNTRIED_PENALTY ? Math.max(NEUTRAL_UNTRIED_PENALTY, decayed) : decayed;
```

一行。**對所有 raw 都成立，而且以後不管誰再動 `LATENCY_FULL_PENALTY_MS` 或 `STALENESS_MAX_DISCOUNT` 都不會失效**——現在的寫法是每次動任一個常數，都要有人重新做一次這個算術。

**你的 regression test 也印證了這件事**：你加的是 `staleBadData > untried`，而測資取的是 worst case（raw = 1.0），所以它會過。**如果那條 case 改用 6 秒的資料，它現在就會紅。** 建議直接把它改成 6s，讓測試釘住的是性質而不是最好的那個點。

## AC2.〔一個問題，不是缺陷〕中性值的「意思」被另一個常數改掉了，你保留了數字

`NEUTRAL_UNTRIED_PENALTY = 0.3` 在改動前後都是 0.3，但它代表的東西從 **1.5 秒**變成 **4.5 秒**。

對照你的實測：untried 現在排在 Google（0.6–2.4s → 0.04–0.16）**後面**、OpenRouter（0.4）前面。改動前它排在 Groq 跟 Google 之間。**也就是說「沒試過的 provider」現在被當成明顯偏慢的一家看待，探索意願下降了。**

**這可能正是你要的**（11 家 provider、探索一次的代價是玩家等一個 tooltip），我不預設它是錯的。**但我想確認這是決定，不是副作用**——因為 0.3 這個數字沒有變，變的是它除以的那個分母。

如果你要的其實是「未知 ≈ 中段延遲」這個**意圖**，那建議把它寫成派生值：

```java
private static final double NEUTRAL_UNTRIED_EQUIVALENT_MS = 1500.0;   // 意圖寫在這裡
private static final double NEUTRAL_UNTRIED_PENALTY = NEUTRAL_UNTRIED_EQUIVALENT_MS / LATENCY_FULL_PENALTY_MS;
```

這樣以後改 clamp 上限時，意圖會自動跟著走，而不是靠有人記得回來重算——跟 AC1 是同一個道理。

---

## 一個我要講的觀察

AA1 → AB1 → 現在的 AC1，是同一個形狀第三次出現：**我指出一個具體實例，你精準修掉那個實例，但那個實例所屬的性質沒有被一起處理。**

- AA1：我指 concurrency/rate 的順序，你修了；global rate / provider rate 那一對（同性質）還在，變成 AB1。
- AB1：我指 base URL，你修了 (a)；**但你也做了 (b)**（把 build 搬到 commit 前）——那一次你修掉了性質，所以我上一輪才能說「沒有第四個」。
- 這次：我陳述的是原則，你實作成 worst case 的算術。

**AB1 那次的做法是對的模板**：(a) 堵住已知入口、(b) 讓整個類別不可能再發生。AC1 的那一行 `Math.max` 就是這次的 (b)。

---

## 我可能錯的地方

**AC2 是問題不是判斷。** 我沒有資料說 1.5s 或 4.5s 哪個中性值在真實遊玩中更好——那要靠長時間 AUTOMATIC 才知道。我唯一有把握的是**它變了，而且不是因為有人決定它該變**。
