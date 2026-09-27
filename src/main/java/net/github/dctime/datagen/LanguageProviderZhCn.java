package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** Simplified Chinese (zh_cn) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderZhCn extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderZhCn(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "删除快取");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "在GUI显示截图翻译内容 (按住)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "重新翻译内容 (按住)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "API 密钥");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "模型名称");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "提示词");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "翻译截图的提示词");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "启用 Jade 整合");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "启用 FTB Quests 整合");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "启用物品提示框翻译");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "启用成就翻译");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "启用截图翻译");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Jade和提示框翻译第一行附带物品图案");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "翻译中是否在游戏画面显示动画");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "超时时间(秒)");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "功能");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "提供者");

        add(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported",
                "翻译失败！目前选择的模型不支持图片输入 -- 请到设置画面换一个模型试试。");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "翻译服务");
        add(p + "service", "服务");
        add(p + "provider.google", "Google AI Studio");
        add(p + "provider.nvidia", "NVIDIA NIM");
        add(p + "provider.groq", "Groq");
        add(p + "provider.openrouter", "OpenRouter");
        add(p + "provider.mistral", "Mistral AI");
        add(p + "provider.deepseek", "DeepSeek");
        add(p + "provider.cerebras", "Cerebras");
        add(p + "provider.anthropic", "Anthropic Claude");
        add(p + "provider.openai", "OpenAI");
        add(p + "provider.ollama", "Ollama（本地）");
        add(p + "provider.custom", "自定义提供商");

        add(p + "provider_mode", "Provider 模式");
        add(p + "provider_mode.single", "单一");
        add(p + "provider_mode.single.tooltip", "只使用一个 provider，失败时不会自动切换 -- 与本模组原本的行为相同。");
        add(p + "provider_mode.priority", "优先级");
        add(p + "provider_mode.priority.tooltip", "按照优先级顺序尝试已启用的 provider，遇到临时性失败（RPM 超限、超时、连接错误、服务器错误）时自动切换到下一个。");
        add(p + "provider_mode.round_robin", "轮询");
        add(p + "provider_mode.round_robin.tooltip", "在已启用且可用的 provider 之间平均轮流使用。");
        add(p + "provider_mode.automatic", "自动");
        add(p + "provider_mode.automatic.tooltip", "根据当前负载、近期失败记录与延迟自动选择最合适的 provider，优先级只作为次要参考。遇到临时性失败同样会自动切换。推荐使用。");
        add(p + "manage_providers", "管理 Provider");
        add(p + "manage_providers.note", "在这里开关每个 provider。点击 Configure 可设置 API 密钥、模型、优先级与每分钟请求数。");
        add(p + "provider.configure", "设置");
        add(p + "provider.enabled", "启用");
        add(p + "provider.priority", "优先级");
        add(p + "provider.priority.tooltip", "数字越小越优先：Priority 模式会优先尝试它，Automatic 模式会将其作为次要参考。1 为最高优先级，大多数玩家不需要修改此值。");
        add(p + "provider.rpm", "每分钟请求数（此 provider）");
        add(p + "provider.rpm.tooltip", "这是本模组建议的起始值，并非官方保证的额度 -- 请根据你自己账号实际的速率限制调整。");
        add(p + "provider.status", "状态");
        add(p + "provider.status.ready", "就绪");
        add(p + "provider.status.rate_limited", "已达速率限制（%s 秒）");
        add(p + "provider.status.cooldown", "冷却中（%s 秒）");
        add(p + "provider.status.invalid_key", "API 密钥无效");
        add(p + "provider.status.cannot_connect", "无法连接");
        add(p + "provider.status.disabled", "已禁用");
        add(p + "provider.status.detail.untried", "（未尝试过）");
        add(p + "provider.status.detail", "（平均 %s 秒，上次尝试于 %s 秒前）");

        add(p + "model", "模型");
        add(p + "model.custom", "自定义...");
        add(p + "model.custom_id", "自定义模型 ID");
        add(p + "api_key", "API 密钥");
        add(p + "api_key.show", "显示");
        add(p + "api_key.hide", "隐藏");
        add(p + "api_key.paste", "粘贴");
        add(p + "api_key.ollama_note", "本地服务，不需要 API 密钥");
        add(p + "test_connection", "测试连接");
        add(p + "test_connection.testing", "测试中...");
        add(p + "test_connection.connected", "已连接");
        add(p + "test_connection.invalid_key", "API 密钥无效");
        add(p + "test_connection.rate_limited", "已达速率限制");
        add(p + "test_connection.cannot_connect", "无法连接");
        add(p + "test_connection.http_error", "HTTP 错误 %s");
        add(p + "test_connection.model_not_found", "列表中找不到模型“%s”，列表可能不完整");
        add(p + "test_connection.invalid_base_url", "Base URL 无效");
        add(p + "test_connection.note", "仅验证连接与授权，不保证一定能成功翻译");

        add(p + "custom_provider.name", "提供商名称");
        add(p + "custom_provider.base_url", "Base URL");
        add(p + "custom_provider.authentication", "验证方式");
        add(p + "custom_provider.authentication.bearer", "Bearer Token");
        add(p + "custom_provider.authentication.none", "无");
        add(p + "custom_provider.supports_images", "支持图片输入");
        add(p + "custom_provider.privacy_note", "请求与翻译内容会直接发送到上面配置的服务器。");

        add(p + "section.language", "语言");
        add(p + "follow_game_language", "跟随 Minecraft 语言");
        add(p + "follow_game_language.tooltip", "使用你目前 Minecraft 选项里的语言。");
        add(p + "target_language", "目标语言");
        add(p + "target_language.custom", "自定义/未知：%s");

        add(p + "section.features", "翻译功能");
        add(p + "feature.tooltip", "物品提示框");
        add(p + "feature.jade", "Jade 信息框");
        add(p + "feature.ftbquests", "FTB 任务");
        add(p + "feature.advancements", "成就");
        add(p + "feature.screenshot", "截图翻译");

        add(p + "advanced_settings", "高级设置");
        add(p + "model_cache_note", "更换提供者或模型后，旧的翻译快取不会自动失效/清除。");
        add(p + "clear_cache_confirm.title", "是否清除翻译快取？");
        add(p + "clear_cache_confirm.message", "翻译相关设置已变更。是否清除现有 %s 条快取翻译？");

        add(p + "section.translation", "翻译");
        add(p + "include_icon", "翻译时包含物品图示");
        add(p + "include_icon.tooltip", "将物品图示传送给支持视觉识别的模型，取得更好的翻译内容。");
        add(p + "pretranslate_containers", "预先翻译容器内物品");
        add(p + "pretranslate_containers.tooltip", "在你把鼠标移到物品上之前，自动翻译画面上可见的容器物品，可能会用掉更多 API 请求。");
        add(p + "custom_prompt", "自定义翻译提示词");
        add(p + "custom_prompt.builtin", "使用内建提示词");
        add(p + "custom_prompt.custom", "使用自定义提示词");
        add(p + "edit_custom_prompt", "编辑自定义提示词");
        add(p + "edit_screenshot_prompt", "编辑截图翻译提示词");
        add(p + "reset_to_builtin", "重设为内建");

        add(p + "section.interface", "界面");
        add(p + "translating_animation", "显示翻译中动画");
        add(p + "translating_animation.tooltip", "在等待翻译结果时，于画面上显示一个小动画。");

        add(p + "section.network", "网络");
        add(p + "timeout", "请求超时");
        add(p + "timeout.seconds", "%s 秒");
        add(p + "timeout.custom", "自定义：%s 秒");
        add(p + "rpm", "每分钟请求数");
        add(p + "rpm.tooltip", "限制 60 秒内最多能送出几次 AI 翻译请求。如果你的供应商回传 HTTP 429，请调低这个数字。");
        add(p + "rpm.custom", "自定义：%s");

        add(p + "section.cache", "快取");
        add(p + "clear_cache", "清除翻译快取（%s 条）");

        add(p + "prompt_edit.title", "编辑自定义提示词");
        add(p + "prompt_edit.screenshot_title", "编辑截图翻译提示词");
        add(p + "prompt_edit.hint", "留空 = 使用内建提示词");
    }
}
