# Microdaery's Translator

Welcome to **Microdaery's Translator Mod** (NeoForge, Minecraft 1.21.1)!
This mod calls an AI API (Google AI Studio / Ollama / Mistral) to translate tooltips, Jade
overlays, Advancements, FTB Quests, and screenshots in real time.

---

## Installation

This template repository can be directly cloned to start a new mod project.

1. Go to GitHub and create a new repository using this one as a **Template Repository**:  
   👉 [GitHub Guide](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template)

2. Clone the repository to your local machine and open it in your preferred IDE.
    - Recommended IDE: **IntelliJ IDEA** or **Eclipse**

3. If you encounter missing libraries or build issues, try:
    - `gradlew --refresh-dependencies` → Refresh dependencies
    - `gradlew clean` → Clean the project (does not affect your code)

---

## Mapping Names

By default, this MDK uses the official Mojang mapping names for Minecraft methods and fields.  
These mappings are protected by a license that all developers should be aware of.

For the latest license text, refer to the mapping file itself or see the reference copy here:  
🔗 [Mojang Mapping License](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md)

---

## Additional Resources

- 📖 [NeoForged Documentation](https://docs.neoforged.net/)
- 💬 [NeoForged Discord](https://discord.neoforged.net/)

---

## 使用說明（中文）

1. 選一個翻譯 Endpoint：**Google AI Studio**、**Mistral AI**，或本機跑的 **Ollama**（不需要 API Key，但要自己先把模型載好並啟動 `http://127.0.0.1:11434`）。
2. 用 Google AI Studio 或 Mistral 的話，先去申請 API 金鑰。
3. 進入 Minecraft 的模組設定檔資料夾：
   ```
   <遊戲資料夾>/config/microdaerystranslator-client.toml
   ```
4. 用文字編輯器打開設定檔，依需求調整：
   - `endpoint`：`GOOGLE_AI_STUDIO` / `OLLAMA` / `MISTRAL`
   - `api_key`：Google AI Studio 或 Mistral 的 API 金鑰（Ollama 不需要）
   - `model_name`：要用的模型名稱（例如 Mistral 的 `mistral-small-latest`）
   - `follow_game_language`：翻譯目標語言是否跟隨遊戲本身選擇的語言（預設開）
   - `target_language`：`follow_game_language` 關閉時，手動指定目標語言代碼（`zh_tw`/`zh_cn`/`ja_jp`/`en_us`，跟遊戲語言選項同一套代碼）
   - 其餘功能開關（Tooltip / Jade / 成就 / FTB Quests / 螢幕截圖翻譯）都可以個別關閉
5. 儲存後重新進入遊戲，即可開始使用翻譯功能！

### 資料流向與隱私

- 直連通訊：翻譯內容只會直接送到你選擇的 Endpoint（Google AI Studio / Mistral / 你自己的 Ollama），不經過任何轉接伺服器。
- 本模組不會蒐集、紀錄或轉傳你的 API Key 及輸入內容到開發者或其他第三方伺服器。
- API Key 僅儲存在本機的設定檔內，請妥善保管。

### 常用按鍵

- 滑鼠指向物品會自動翻譯 tooltip；已翻譯過的文字有本地快取（含落地磁碟，重開遊戲不用重打 API）。
- 按鍵可在遊戲的按鍵設定裡自訂：清除翻譯快取、重新翻譯目前顯示的翻譯、顯示/隱藏螢幕翻譯結果。

---

Enjoy your Minecraft translation experience! 🚀
