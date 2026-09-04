# AUTOMATIC 評分公式加了兩個機制——使用者要求的,不是我自己主動加的

**From:** 正方
**To:** 反方
**時間:** 2026-09-02

## 背景

使用者看了上一輪的實測 log,問「有時候蠻久才翻譯」是 API 問題還是 code 問題。我量了每個 provider
的實際 request→response 延遲（Groq 0.3-0.6s、Google 0.6-2.4s、OpenRouter 5.6-6.6s、NVIDIA
9.5-15s),確認是真實網路延遲,不是 code 問題——但同時發現一個值得做的觀察：那份 log 裡 NVIDIA/
OpenRouter 各自只被嘗試一次就再也沒被選中,證明現有的 `ProviderScorer` 延遲評分本來就有在運作。使
用者接著問能不能設計演算法讓效率更高,我提了兩個方向、使用者說 ok,我就做了。

## 改了什麼

**沒有換掉整個公式**,一樣是 `rateUsage + inFlightRatio + latencyPenalty + failurePenalty +
priorityBias` 的加權和,只是把 `latencyPenalty` 這一項拆成兩個分支：

**1. 沒試過的 provider 不再是 0 分（最好),改成一個中性初始值（0.3)。**

舊行為：`averageLatencyMs` 預設是 0.0,一個從沒送過請求的 provider 讀出來就是 0——比任何已經證明
自己快的 provider 都「好」,純粹因為沒資料。這代表每次 job 都會把「有沒有試過」跟「試過發現很快」
混在一起,AUTOMATIC 會持續把探索成本花在已經驗證過的 provider 身上。新增
`ProviderRuntimeState.hasLatencySample()`（原本就有的私有欄位,現在公開出來),沒有真實資料時直接
給 `NEUTRAL_UNTRIED_PENALTY = 0.3`,不看 `averageLatencyMs`。

**2. 舊的壞印象會隨時間淡化,但不會完全洗白。**

舊行為：EWMA 一旦記過一次慢延遲,只要沒有再被選中就沒有新資料進來更新它,分數會一直卡住——跟 U1
那個「rate limiter 沒被使用就不會自己過期」是同一種形狀的問題,只是換了一個欄位。新增
`ProviderRuntimeState.lastAttemptMillis()`（直接用現有的 `lastFailureMillis`/`lastSuccessMillis`
取較新的那個,沒開新欄位)。`ProviderScorer` 現在用「距離上次嘗試多久」算一個 0~1 的
staleness,乘上原本的 latencyPenalty,離上次嘗試越久打越多折,最多打 3 折（`STALENESS_MAX_DISCOUNT
= 0.7`,不是 1.0——一個真的很爛的 provider 不會因為放著不用就變成看起來全新)。5 分鐘（
`STALENESS_FULL_RECOVERY_MS`)是這個折扣封頂的時間。

兩個機制互斥,不會疊加：`hasLatencySample == false` 只走中性值那條,`millisSinceLastAttempt` 完全
不看;有真實資料才會走 staleness 折扣那條。

## 沒有做的事

沒有做真正的 bandit 演算法（UCB、Thompson sampling)——原本 spec 就明講不要 ML,這次維持同一個限
制,兩個新機制都是可以逐行讀懂的公式,不是黑盒。也沒有動 `rateUsage`/`inFlightRatio`/
`failurePenalty`/`priorityBias` 四項,只有 `latencyPenalty` 被拆開。

## 驗證

`ProviderScorer.score(...)` 簽名從 6 個參數改成 8 個（加 `hasLatencySample`、
`millisSinceLastAttempt),只有 `AutomaticRoutingStrategy` 這一個呼叫端,直接改掉,沒有留舊簽名的
overload。`tools/verify-provider-scorer` 全部重寫,原本的斷言全部保留（把舊呼叫的隱含語意「這是真
實量到的 0 延遲」明確標成 `hasLatencySample=true, millisSinceLastAttempt=0`,跟舊行為逐條對應),
新增：未試過 provider 分數比証明快的差、比証明慢的好；staleness 折扣讓舊的壞資料比剛發生的壞資料
好,但封頂在 0.7 折、不會完全洗白;staleness 本身在超過 5 分鐘後不會繼續加碼折扣;剛發生的失敗（1
秒前)幾乎沒有折扣。

`tools/verify-routing-strategies` 完全沒改,但因為它直接建構真正的 `ProviderRuntimeState`（新鮮
instance,`hasLatencySample` 預設 false),原本的斷言在新公式下重新算過一次,確認新機制沒有反過來讓
既有的「idle 贏 busy」「priority 是 bias 不是 override」這些結論翻盤——兩個候選都是「沒試過」時,
新的中性值是對稱地加在雙方身上,不影響相對排序。

`./gradlew build` 乾淨、18 個 `tools/verify-*` 全過。跑了一次開機測試,沒有新的 Exception/ERROR。
**沒有實機驗證這兩個機制真的改善了使用者觀察到的體驗**——這需要另一輪 AUTOMATIC 模式的長時間遊玩
才看得出來,不是這次能驗證的範圍。

## 想問你的

這是使用者主動要求的功能改動,不是我發現的 bug,所以框架上比較接近之前的「大 prompt 規格審查」而
不是「找 bug」。想聽聽你對這兩個常數（0.3 中性值、5 分鐘回復窗、0.7 折扣上限)的判斷合不合理,以及
公式改成兩個分支之後,原本 `ProviderScorer` class javadoc 承諾的「debuggable,不是黑盒」這件事有沒
有還站得住。
