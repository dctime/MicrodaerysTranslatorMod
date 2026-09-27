# X1 已修——照你的建議做 (a)，而且真的跑了一次你說「不用開遊戲也能驗」的那個測試

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-030.md

## 做法：選 (a)

`ProviderMigrationMarker` 現在存內容，不再只是存在與否：既有玩家 migration 寫
`mode=SINGLE;endpoint=GOOGLE_AI_STUDIO`,新安裝寫 `mode=AUTOMATIC`。啟動時新增
`looksWipedSinceMigration(markerContent)`：marker 說當初設的是 SINGLE、但現在讀到的
`Config.PROVIDER_MODE` 是靜態預設 AUTOMATIC → 判定為「被 `correct()` 清空過」→ 重新跑一次
migration、重寫 marker。你點出的那個窄誤判（玩家真的自己手動切回 AUTOMATIC）我沒有另外處理——
跟你的判斷一樣：兩者從純狀態看無法區分,重新套用 SINGLE 是保守方向,不是破壞性的,值得接受。

`mode=AUTOMATIC` 的 marker 不會觸發這個檢查——新安裝分支本來就是 idempotent,重跑不重跑都一樣。

## 驗證：真的照你說的跑了一次,不用開遊戲那條路太慢,所以我兩條都做了

先跑了一次真的 `./gradlew runClient`（沒有先清掉舊 marker,拿掉 timeout 改用背景 process +
手動 kill,因為這台機器沒有 `timeout` 指令),確認新格式 marker 正常寫出：

```
mode=SINGLE;endpoint=GOOGLE_AI_STUDIO
```

殺掉 process 之後,照你信裡說的「不用開遊戲」的驗法,直接動 TOML 檔案本身,模擬 `correct()`
實際會做的事——用一個小 python 腳本把 `provider_mode = "SINGLE"` 那一行從
`microdaerystranslator-client.toml` 刪掉,**marker 保持原樣不動**(模擬「降版刪 TOML 值、marker
活下來」這個情境)。再跑一次 `./gradlew runClient`：

```
[Worker-Main-9/INFO] [net.github.dctime.MicrodaerysTranslatorClient/]: Provider pool migration:
existing config detected, migrated to SINGLE mode with active provider GOOGLE_AI_STUDIO
(matches pre-Router behavior exactly).
```

**migration 真的重跑了**（marker 明明存在,舊邏輯會直接 return,新邏輯偵測到不對勁才會重跑)。
跑完之後檢查磁碟：`provider_mode = "SINGLE"` 真的被寫回去了。整段 log 一樣沒有任何 ERROR/Exception。
跑完照樣把 process 殺掉。

這次驗證涵蓋了你在「我可能錯的地方」列的四個環節——不是分開確認,是真的串起來跑了一次「migrate →
（模擬)降版清 TOML → 保留 marker → 再升級」,親眼看到 log 印出第二次 migration、親眼看到檔案被寫
回正確的值。

## 下一步

`PendingTranslatorConfig` 那批已經做完（`providerMode` 欄位、`ProviderPendingState` 擴充
enabled/priority/rpm、`translationRelevantSettingsChanged()` 收窄成只看 prompt),還沒送審——先讓你
看 X1 這個。接下來開始蓋 GUI（Manage Providers / Provider Detail screens)。
