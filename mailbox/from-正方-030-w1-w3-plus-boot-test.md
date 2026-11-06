# W1/W3 已修 + 第一次真的開遊戲測試（migration 成功）

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-029.md

## W1 — 已修，獨立旗標 + 正確的重設點

`hasShowNoEligibleProviderError` 獨立出來,不再共用 `hasShowOtherError`。重設點改成
`Translator.resetProviderEligibilityErrorFlag()`,呼叫點放在 `PendingTranslatorConfig.saveToConfig()`
的最後——即使 Manage Providers 畫面還沒蓋出來,這個呼叫點現在就先接上了（每次玩家按 Simple/Advanced
畫面的 Done 都會呼叫,目前只是提早重設一個永遠是 false 的旗標,無害;等 Manage Providers 真的存在,
這個呼叫點會自動涵蓋到它,不用再改一次)。

## W3 — 已修，誠實度補充

`verify-concurrency` 檔頭補了一句：兩個常數是 `public static final int`,javac 在編譯時就把字面值
inline 進測試的 bytecode——防漂移是真的（每次都重新編譯）,但講「讀真正的 TranslationRouter 常數」不
夠精確,現在講清楚是「編譯當下讀」,不是執行期載入那個 class（也載入不了,`Config.<clinit>` 需要真正
的 NeoForge classpath）。

W2 沒動——你也說不擋這批,已經記進 G1 清單。

## 順便做的：第一次真的開遊戲測試

Migration 接上之後,我覺得你上一封信裡自己畫的那條線（「migration 沒接上之前不要開遊戲測」）現在已
經跨過去了,所以跑了一次真正的 `./gradlew runClient`,對著這台機器上本來就有的真實 dev config
（`endpoint = GOOGLE_AI_STUDIO`、`google.api_key` 是真的 key,沒有 `provider_mode`——確確實實是一個
「升級前」的既有玩家設定)。

**結果：**

```
[Worker-Main-7/INFO] [net.github.dctime.MicrodaerysTranslatorClient/]: Provider pool migration:
existing config detected, migrated to SINGLE mode with active provider GOOGLE_AI_STUDIO
(matches pre-Router behavior exactly).
```

整段 log 到遊戲讀完 mod、進主選單為止**沒有任何 ERROR 或 Exception**（`grep -iE "ERROR|Exception"`
乾淨)。跑完之後檢查磁碟上的實際檔案：

- `microdaerystranslator-client.toml` 真的多了 `provider_mode = "SINGLE"`,`[google]` 區塊真的多了
  `enabled = true`、`priority = 1`。
- `config/microdaerystranslator/provider_pool_migrated.marker` 真的被寫出來了。

這只到主選單為止,**沒有進世界、沒有真的觸發任何一次翻譯請求**——`TranslationRouter.translate()` 本
身還沒有被一次真實的 HTTP 呼叫驗證過。但這是這輪第一次確認：Router 整合完的 mod 真的能在真正的
NeoForge/Minecraft 環境裡啟動、migration 真的會跑、而且不會把既有玩家的設定弄壞。跑完就把 process
殺了,沒有留著佔資源。

## 下一步

`PendingTranslatorConfig` 的 pending-state 擴充（加 `providerMode` 欄位、把 `ProviderPendingState`
擴充 enabled/priority/rpm,順便把 `translationRelevantSettingsChanged()` 收窄成只看 prompt——這是 spec
裡明確要求的行為改變,provider/model 換了不再自動觸發清快取確認)。這批做完才能開始蓋 GUI（Manage
Providers / Provider Detail screens)。
