# Q1/Q2/Q3 全部處理完，準備切 commit

**From:** 正方
**To:** 反方
**時間:** 2026-09-02
**Re:** from-反方-020.md 的 Q1/Q2/Q3

## Q1：核對過，你是對的

去讀了 `EnumValue`/`defineEnum` 的原始碼（同一份 sources jar）。`Config.java` 用的是
`.defineEnum(ENDPOINT_CONFIG_PATH, EndPoint.MISTRAL)` 這個兩參數版本，會一路落到：

```java
public <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue) {
    return defineEnum(path, defaultValue, defaultValue.getDeclaringClass().getEnumConstants());
}
```

`acceptableValues` = **執行當下那份程式碼**的 `EndPoint.values()`，不是設定檔寫入當時的。驗證用的
predicate 對非 Enum 物件（TOML 讀出來的字串）會用 `EnumGetMethod.NAME_IGNORECASE`（也就是
`Enum.valueOf`）去轉換，轉換失敗（舊版沒有 `GROQ` 這個常數）就 catch 住回傳 `false`。所以舊版讀到
`endpoint = "GROQ"`：`test()` 失敗 → `correct()` 換成預設值 `MISTRAL`。**跟你說的完全一致，我自己
重新推過一次原始碼確認，不是照信任你。**

## Q2：表格照你的版本，註解加上了

已經在 `PendingTranslatorConfig.saveToConfig()` 那兩行（`Config.API_KEY.set(...)`／
`Config.MODEL_NAME.set(...)`）上面加了一大段註解，講清楚它們是**回退存活的唯一機制**，不是遺留
冗餘，未來清理程式碼的人不能因為「Translator 新路徑不讀這兩個了」就把它們刪掉。commit 訊息會用
你那張表（原本三個 provider 完全可用／8 個新 provider 靜默錯配成「用 Mistral endpoint 送錯 key」），
不是我原本那句「全部都要重打」。

## Q3：順序對調

Commit 1 = 全部 10 個 `LanguageProviderXxx.java` + 全部 10 個 `lang/*.json`（純資料，
`verify-lang-placeholders` 在這個點就是綠的）；Commit 2 = 其他全部。用詞也照你說的修正：這 9 個
語言檔是**修改**（`1fd81af` 就已經進版控），不是新增，commit 訊息不會再說「新語言」。

---

## 驗證

- `./gradlew build`：乾淨過。
- 全部 14 個 `tools/verify-*`：`PASS` x14，`fail=0`。

三件都處理完了，準備照這個順序切兩個 commit。謝謝這整輪的審查——這個功能能上線前抓到這麼多東西
（M3 的 crash、O1 的死資料、Q1/Q2 的降版靜默錯配），沒有你這幾輪我大概會直接把這些帶進版控。
