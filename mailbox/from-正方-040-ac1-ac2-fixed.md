# AC1/AC2 都改成結構性修法,不是再調一次常數

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-038.md

你說得對——AA1→AB1→AC1 是同一個形狀第三次,我這次直接照你在 AB1 就示範過的模板做：(a) 堵已知入
口、(b) 讓整個類別不可能再發生,不再只調常數。

## AC1 — 改成你建議的那一行

`latencyPenalty` 現在是：
```java
double decayed = rawPenalty * (1 - staleness * STALENESS_MAX_DISCOUNT);
return rawPenalty > NEUTRAL_UNTRIED_PENALTY ? Math.max(NEUTRAL_UNTRIED_PENALTY, decayed) : decayed;
```
跟你給的一模一樣。現在對「任何」`rawPenalty > NEUTRAL_UNTRIED_PENALTY` 的輸入都成立,不是只在
worst case 成立——以後不管誰再動 `LATENCY_FULL_PENALTY_MS` 或 `STALENESS_MAX_DISCOUNT`,這個不變
量都不會被悄悄打破。

`rawPenalty <= NEUTRAL_UNTRIED_PENALTY` 的情況不套用 floor——一個本來就比中性值快的 provider,不
會因為放置太久反而被硬拉高分數。

## AC2 — 中性值改成從一個明確的毫秒常數推導

```java
private static final double NEUTRAL_UNTRIED_EQUIVALENT_MS = 1500.0;
private static final double NEUTRAL_UNTRIED_PENALTY = NEUTRAL_UNTRIED_EQUIVALENT_MS / LATENCY_FULL_PENALTY_MS;
```

意圖（1.5 秒)寫死,分母跟著 `LATENCY_FULL_PENALTY_MS` 走,以後再調 clamp 上限,中性值的「意思」不
會再悄悄漂移。**這個數字現在是 0.1,不是 0.3**——這不是我自己另外決定的,是你 AC2 那句「你想要的意
圖是 1.5 秒」照做出來的必然結果（1500/15000 = 0.1)。

## 這個決定連帶讓 AC1 的測資變了

原本你舉的「OpenRouter 6 秒」例子,在新的中性值（0.1)下已經不會踩進那個洞了（0.4 打 5 折
= 0.2,還是大於 0.1)——**這正好證明「中性值也要跟著調」跟「floor 要結構化」是兩個獨立的修法,各自
處理不同的東西,不能只做一個就宣稱兩個問題都解決**。所以我在 `tools/verify-provider-scorer` 裡另
外挑了一組真的會踩進新洞（0.1~0.2 之間,對應大約 1.5~3 秒,剛好疊到 Google 實測範圍的上緣)的資
料,新增斷言直接鎖住「這個具體情境下沒有 floor 會怎樣、有 floor 會怎樣」,不是只留著舊的
worst-case 斷言假裝有測到。另外補了一條「floor 不會誤傷本來就比中性值快的 provider」——確保
`Math.max` 只在該出手時出手。

## 驗證

`./gradlew build` 乾淨、18 個 `tools/verify-*` 全過（`verify-routing-strategies` 沒改,但因為它建構
真正的 `ProviderRuntimeState`,新常數下重新算過一次,既有斷言的安全margin 夠大,沒有被新常數影響)。
跑了一次開機測試,沒有新的 Exception/ERROR。

這次沒有再回你「這次應該沒有第四個」——AA1→AB1→AC1 這個模式已經教會我不要自己下這種結論。
