# N1/N2 修完，N3 收到

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-016.md 的 N1/N2/N3

## N1：`verify-provider-adapters` 加了 `providerInfoIsCompleteAndLangKeysExist()`

照你的建議實作，但有一個修正：**你原始建議裡對每個 `ModelPreset` 也查 `displayNameKey()`——`ModelPreset`
沒有這個欄位。** `ModelPreset.displayName` 是純字面字串（品牌名，例如「GPT OSS 20B」），從一開始
就不是翻譯 key（`ProviderInfo` class javadoc 裡寫的原因：這些是不翻譯的專有名詞），所以沒有 lang
key 可以查。新測試只做了兩件事，對應你說的 (a)/(b)：

```java
for (Config.EndPoint endpoint : Config.EndPoint.values()) {
    ProviderInfo info = ProviderInfo.of(endpoint);              // (a) 缺就丟例外，測試就紅
    assertTrue(..., langKeys.contains(info.displayNameKey()));  // (b) 只查 ProviderInfo 自己的 key
}
```

`langKeys` 直接讀 `src/generated/resources/assets/microdaerystranslator/lang/en_us.json`（Gson
解析，`verify-provider-adapters` 本來就已經在用 Gson 了）。你自己在「我可能錯的地方」提到「沒有
逐一比對所有 ModelPreset key」——現在確認了：**沒有東西壞，因為 ModelPreset 根本不是這個檢查的
對象**，不是漏查。

實測：11 個 provider 的 `ProviderInfo.of()` 全過、11 個 `displayNameKey()` 全部在 en_us.json
裡找到（輸出貼在下面驗證區）。

## N2：`ProviderConfigResolver.resolve()` 加了具名例外

```java
Config.ProviderConfigKeys keys = Config.PROVIDER_KEYS.get(endpoint);
if (keys == null) {
    throw new IllegalStateException("No Config.PROVIDER_KEYS entry for " + endpoint
            + " -- this is a registration bug, not a player-reachable state.");
}
```

跟 `ProviderAdapterRegistry.forEndpoint` 的風格一致（大聲丟、訊息帶 endpoint 名稱），取代原本
「拿到 null 之後呼叫 `.apiKey()` 直接 NPE、訊息裡什麼都沒有」的路徑。啟動時的 `LOGGER.error` 留著
當早期預警，這個當真的觸發時的說明——照你建議的分工。

## N3：收到，會寫進完成報告

「填了真實 Key 之後按一次測試連線，出現黃字『清單中找不到模型』就代表這個 mod 內建的 preset id
可能過時了，麻煩回報」這段會加進給使用者的完成報告裡，讓那 10 個沒有實機打過真請求的 provider
有一條使用者自己走得動的驗證路徑，不是停在「我們沒測過」。

---

## 驗證

- `./gradlew build`：乾淨過。
- 全部 14 個 `tools/verify-*`：`PASS` x14，`fail=0`，`verify-provider-adapters` 新增的
  `providerInfoIsCompleteAndLangKeysExist` 那 22 行斷言（11 個 `of()` + 11 個 `displayNameKey()`）
  全部 `OK`。

三件都處理完了。等你確認，下一步照你上一封信最後說的原則（回退時會不會動到玩家 TOML 當分界線，
不是檔案位置）提出 commit 切分方案給你看。
