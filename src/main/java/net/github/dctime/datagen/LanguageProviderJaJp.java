package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** Japanese (ja_jp) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderJaJp extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderJaJp(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "ja_jp");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "翻訳キャッシュを削除");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "GUIにスクリーンショット翻訳を表示 (長押し)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "表示中の翻訳を再翻訳 (長押し)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "APIキー");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "モデル名");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "プロンプト");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "スクリーンショット翻訳用のプロンプト");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Jade連携を有効化");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "FTB Quests連携を有効化");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "アイテムツールチップ翻訳を有効化");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "進捗翻訳を有効化");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "スクリーンショット翻訳を有効化");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "JadeとTooltipの1行目にアイテム画像を添付");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "翻訳中のアニメーション表示を有効化");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "タイムアウト時間");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "機能");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "エンドポイント");

        add(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported",
                "翻訳に失敗しました！選択中のモデルは画像入力に対応していません -- 設定画面で別のモデルに変更してください。");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "翻訳サービス");
        add(p + "service", "サービス");
        add(p + "provider.google", "Google AI Studio");
        add(p + "provider.nvidia", "NVIDIA NIM");
        add(p + "provider.groq", "Groq");
        add(p + "provider.openrouter", "OpenRouter");
        add(p + "provider.mistral", "Mistral AI");
        add(p + "provider.deepseek", "DeepSeek");
        add(p + "provider.cerebras", "Cerebras");
        add(p + "provider.anthropic", "Anthropic Claude");
        add(p + "provider.openai", "OpenAI");
        add(p + "provider.ollama", "Ollama（ローカル）");
        add(p + "provider.custom", "カスタムプロバイダー");

        add(p + "provider_mode", "プロバイダーモード");
        add(p + "provider_mode.single", "単一");
        add(p + "provider_mode.single.tooltip", "1つのプロバイダーのみを使用します。失敗時の自動フォールバックはありません -- 本モッドの元々の動作と同じです。");
        add(p + "provider_mode.priority", "優先度");
        add(p + "provider_mode.priority.tooltip", "有効なプロバイダーを優先度順に試し、一時的な失敗（レート制限、タイムアウト、接続エラー、サーバーエラー）が起きた場合は次のプロバイダーにフォールバックします。");
        add(p + "provider_mode.round_robin", "ラウンドロビン");
        add(p + "provider_mode.round_robin.tooltip", "有効かつ利用可能なプロバイダーを均等に順番に使用します。");
        add(p + "provider_mode.automatic", "自動");
        add(p + "provider_mode.automatic.tooltip", "現在の負荷、最近の失敗履歴、レイテンシに基づいて最適なプロバイダーを自動的に選択します。優先度は補助的な判断材料として使われます。一時的な失敗時は引き続きフォールバックします。推奨設定です。");
        add(p + "manage_providers", "プロバイダー管理");
        add(p + "manage_providers.note", "ここで各プロバイダーのオン/オフを切り替えられます。Configure ボタンから API キー・モデル・優先度・毎分リクエスト数を設定できます。");
        add(p + "provider.configure", "設定");
        add(p + "provider.enabled", "有効");
        add(p + "provider.priority", "優先度");
        add(p + "provider.priority.tooltip", "数値が小さいほど優先されます：Priority モードでは最初に試され、Automatic モードでは補助的な判断材料になります。1が最高優先度で、通常は変更する必要はありません。");
        add(p + "provider.rpm", "毎分リクエスト数（このプロバイダー）");
        add(p + "provider.rpm.tooltip", "これは本モッドが提案する初期値であり、公式に保証された割り当てではありません -- ご自身のアカウントの実際のレート制限に合わせて調整してください。");
        add(p + "provider.status", "状態");
        add(p + "provider.status.ready", "準備完了");
        add(p + "provider.status.rate_limited", "レート制限中（%s秒）");
        add(p + "provider.status.cooldown", "クールダウン中（%s秒）");
        add(p + "provider.status.invalid_key", "APIキーが無効です");
        add(p + "provider.status.cannot_connect", "接続できません");
        add(p + "provider.status.disabled", "無効");
        add(p + "provider.status.detail.untried", "（未試行）");
        add(p + "provider.status.detail", "（平均 %s 秒、%s 秒前に試行）");

        add(p + "model", "モデル");
        add(p + "model.custom", "カスタム...");
        add(p + "model.custom_id", "カスタムモデルID");
        add(p + "api_key", "APIキー");
        add(p + "api_key.show", "表示");
        add(p + "api_key.hide", "非表示");
        add(p + "api_key.paste", "貼り付け");
        add(p + "api_key.ollama_note", "ローカルサービスのためAPIキーは不要です");
        add(p + "test_connection", "接続テスト");
        add(p + "test_connection.testing", "テスト中...");
        add(p + "test_connection.connected", "接続成功");
        add(p + "test_connection.invalid_key", "APIキーが無効です");
        add(p + "test_connection.rate_limited", "レート制限を超過しました");
        add(p + "test_connection.cannot_connect", "接続できません");
        add(p + "test_connection.http_error", "HTTPエラー %s");
        add(p + "test_connection.model_not_found", "モデル「%s」が一覧に見つかりません（一覧が不完全な可能性があります）");
        add(p + "test_connection.invalid_base_url", "ベース URL が無効です");
        add(p + "test_connection.note", "接続と認証のみを確認します -- 翻訳リクエストが必ず成功するとは限りません");

        add(p + "custom_provider.name", "プロバイダー名");
        add(p + "custom_provider.base_url", "ベース URL");
        add(p + "custom_provider.authentication", "認証方式");
        add(p + "custom_provider.authentication.bearer", "Bearer トークン");
        add(p + "custom_provider.authentication.none", "なし");
        add(p + "custom_provider.supports_images", "画像入力に対応");
        add(p + "custom_provider.privacy_note", "リクエストと翻訳内容は上記で設定したサーバーに直接送信されます。");

        add(p + "section.language", "言語");
        add(p + "follow_game_language", "Minecraftの言語に従う");
        add(p + "follow_game_language.tooltip", "現在のMinecraftのオプションで選択している言語を使用します。");
        add(p + "target_language", "翻訳先の言語");
        add(p + "target_language.custom", "カスタム/不明：%s");

        add(p + "section.features", "翻訳機能");
        add(p + "feature.tooltip", "アイテムツールチップ");
        add(p + "feature.jade", "Jadeツールチップ");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "進捗");
        add(p + "feature.screenshot", "スクリーンショット翻訳");

        add(p + "advanced_settings", "詳細設定");
        add(p + "model_cache_note", "プロバイダーやモデルを変更しても、古い翻訳キャッシュは自動的には無効化・削除されません。");
        add(p + "clear_cache_confirm.title", "翻訳キャッシュを削除しますか？");
        add(p + "clear_cache_confirm.message", "翻訳に関する設定が変更されました。既存の %s 件のキャッシュされた翻訳を削除しますか？");

        add(p + "section.translation", "翻訳");
        add(p + "include_icon", "翻訳にアイテム画像を含める");
        add(p + "include_icon.tooltip", "対応する画像認識モデルにアイテム画像を送信し、より良い翻訳文脈を得ます。");
        add(p + "pretranslate_containers", "コンテナ内アイテムを事前翻訳");
        add(p + "pretranslate_containers.tooltip", "マウスを乗せる前に、画面上に見えているコンテナ内アイテムを自動で翻訳します。APIリクエストが増える場合があります。");
        add(p + "custom_prompt", "カスタム翻訳プロンプト");
        add(p + "custom_prompt.builtin", "内蔵プロンプトを使用");
        add(p + "custom_prompt.custom", "カスタムプロンプト");
        add(p + "edit_custom_prompt", "カスタムプロンプトを編集");
        add(p + "edit_screenshot_prompt", "スクリーンショット用プロンプトを編集");
        add(p + "reset_to_builtin", "内蔵プロンプトに戻す");

        add(p + "section.interface", "インターフェース");
        add(p + "translating_animation", "翻訳中アニメーションを表示");
        add(p + "translating_animation.tooltip", "翻訳結果を待っている間、画面に小さなアニメーションを表示します。");

        add(p + "section.network", "ネットワーク");
        add(p + "timeout", "リクエストタイムアウト");
        add(p + "timeout.seconds", "%s 秒");
        add(p + "timeout.custom", "カスタム：%s 秒");
        add(p + "rpm", "1分あたりのリクエスト数");
        add(p + "rpm.tooltip", "60秒間に送信できるAI翻訳リクエストの最大数を制限します。プロバイダーがHTTP 429を返す場合はこの値を下げてください。");
        add(p + "rpm.custom", "カスタム：%s");

        add(p + "section.cache", "キャッシュ");
        add(p + "clear_cache", "翻訳キャッシュを削除（%s 件）");

        add(p + "prompt_edit.title", "カスタムプロンプトを編集");
        add(p + "prompt_edit.screenshot_title", "スクリーンショット用プロンプトを編集");
        add(p + "prompt_edit.hint", "空欄 = 内蔵プロンプトを使用");
    }
}
