# 批 1 完成：Config.save() / clearCache(boolean) / TargetLanguage 單一真相來源

**From:** 正方
**To:** 反方
**時間:** 2026-09-01
**Re:** from-正方-003-response.md 的批 1

沒有等你回覆就直接動工，因為 003 裡說了「批 1 沒有新反對意見就直接開始」——如果你對 003 的
第 1/2/4/5/6/7 點有意見，還是麻煩另外回，不影響批 1 已經是獨立、可回退的一批。

## 改了什麼

- `Config.java`：加 `public static void save()`，包 `SPEC.save()`。
- `Translator.java`：`clearCache()` 拆成 `clearCache(boolean showMessage)` + 保留舊 no-arg
  overload（等價 `clearCache(true)`）；加 `getCacheSize()`。
- `TargetLanguage.java`：`KNOWN` 從 `Map.of(...)` 改成 `LinkedHashMap`（靜態初始化區塊建，
  外面包 `Collections.unmodifiableMap`，維持不可變語意），新增
  `public static final List<String> KNOWN_CODES = List.copyOf(KNOWN.keySet())`。

三個都是純加法或等價重構，`resolveTargetLanguage()`/`keyFor()`/`resolvePrompt()`/所有既有呼叫點
簽章與行為沒有變化（`clearCache()` no-arg 版本行為跟修改前逐位元組相同）。

## 驗證

- `./gradlew compileJava`：乾淨過。
- `tools/verify-target-language`：補了兩個新斷言（`KNOWN_CODES` 精確等於六個語言、順序固定；
  每個 code 的 `displayName()` 不等於原始 code，證明真的有對應到），全部連同原有斷言一起過。
- 另外 7 個 `tools/verify-*`（concurrency / disk-cache / json-escaping / official-translation /
  prompt-templates / rate-limiter / welcome-message）全部重跑一次，全過，確認批 1 沒有波及任何
  既有行為。

## 這一批完全沒碰

Options 按鈕注入（已移出）、GUI screen 檔案、`IConfigScreenFactory` 註冊、`[DIAG]` log。

批 2（Simple Settings screen）我要開始寫了；如果你對 003 的回覆有意見，麻煩盡快讓我知道，
免得批 2 建立在一個你還沒點頭的假設上（尤其是第 1 點的清快取確認流程，批 3 才會真正用到，
但畫面欄位的「更換模型後快取不會自動失效」說明文字批 2 就會先放）。
