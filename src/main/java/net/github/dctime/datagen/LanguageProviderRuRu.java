package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** Russian (ru_ru) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderRuRu extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderRuRu(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "ru_ru");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "Очистить кэш перевода");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "Показать перевод скриншота в GUI (удерживать)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "Перевести отображаемый текст заново (удерживать)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "API-ключ");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "Название модели");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "Промпт");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "Промпт для перевода скриншотов");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Включить интеграцию с Jade");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "Включить интеграцию с FTB Quests");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "Включить перевод подсказок предметов");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "Включить перевод достижений");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "Включить перевод скриншотов");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Отправлять значок предмета вместе с первой строкой Jade и подсказок");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "Включить анимацию во время перевода");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "Время ожидания");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "Функции");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "Провайдер");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");

        add(p + "section.service", "Служба перевода");
        add(p + "service", "Служба");
        add(p + "model", "Модель");
        add(p + "model.custom", "Пользовательская...");
        add(p + "model.custom_id", "ID пользовательской модели");
        add(p + "api_key", "API-ключ");
        add(p + "api_key.show", "Показать");
        add(p + "api_key.hide", "Скрыть");
        add(p + "api_key.paste", "Вставить");
        add(p + "api_key.ollama_note", "Локальная служба - API-ключ не требуется");
        add(p + "test_connection", "Проверить соединение");
        add(p + "test_connection.testing", "Проверка...");
        add(p + "test_connection.connected", "Подключено");
        add(p + "test_connection.invalid_key", "Недействительный API-ключ");
        add(p + "test_connection.rate_limited", "Превышен лимит запросов");
        add(p + "test_connection.cannot_connect", "Не удаётся подключиться");
        add(p + "test_connection.http_error", "Ошибка HTTP %s");
        add(p + "test_connection.model_not_found", "Модель «%s» не найдена в списке (список может быть неполным)");
        add(p + "test_connection.note", "Подтверждает только соединение и авторизацию -- не гарантирует, что запросы на перевод будут успешными");

        add(p + "section.language", "Язык");
        add(p + "follow_game_language", "Следовать языку Minecraft");
        add(p + "follow_game_language.tooltip", "Использует язык, выбранный сейчас в настройках Minecraft.");
        add(p + "target_language", "Целевой язык");
        add(p + "target_language.custom", "Пользовательский/неизвестный: %s");

        add(p + "section.features", "Функции перевода");
        add(p + "feature.tooltip", "Подсказка предмета");
        add(p + "feature.jade", "Подсказка Jade");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "Достижения");
        add(p + "feature.screenshot", "Перевод скриншотов");

        add(p + "advanced_settings", "Дополнительные настройки");
        add(p + "model_cache_note", "Смена провайдера или модели не отменяет и не очищает автоматически старые переводы в кэше.");
        add(p + "clear_cache_confirm.title", "Очистить кэш перевода?");
        add(p + "clear_cache_confirm.message", "Настройки, связанные с переводом, были изменены. Очистить существующие переводы в кэше (%s шт.)?");

        add(p + "section.translation", "Перевод");
        add(p + "include_icon", "Включать значок предмета в перевод");
        add(p + "include_icon.tooltip", "Отправляет значок предмета в поддерживаемые модели с распознаванием изображений для лучшего контекста перевода.");
        add(p + "pretranslate_containers", "Предварительно переводить предметы в контейнерах");
        add(p + "pretranslate_containers.tooltip", "Автоматически переводит видимые предметы в контейнере ещё до наведения курсора. Может увеличить количество запросов к API.");
        add(p + "custom_prompt", "Пользовательский промпт перевода");
        add(p + "custom_prompt.builtin", "Использовать встроенный промпт");
        add(p + "custom_prompt.custom", "Пользовательский промпт");
        add(p + "edit_custom_prompt", "Изменить пользовательский промпт");
        add(p + "edit_screenshot_prompt", "Изменить промпт для скриншотов");
        add(p + "reset_to_builtin", "Сбросить на встроенный");

        add(p + "section.interface", "Интерфейс");
        add(p + "translating_animation", "Показывать анимацию перевода");
        add(p + "translating_animation.tooltip", "Показывает небольшую анимацию на экране во время ожидания результата перевода.");

        add(p + "section.network", "Сеть");
        add(p + "timeout", "Тайм-аут запроса");
        add(p + "timeout.seconds", "%s с");
        add(p + "timeout.custom", "Пользовательский: %s с");
        add(p + "rpm", "Запросов в минуту");
        add(p + "rpm.tooltip", "Ограничивает количество AI-запросов на перевод за 60 секунд. Уменьшите это значение, если провайдер возвращает HTTP 429.");
        add(p + "rpm.custom", "Пользовательский: %s");

        add(p + "section.cache", "Кэш");
        add(p + "clear_cache", "Очистить кэш перевода (%s шт.)");

        add(p + "prompt_edit.title", "Изменить пользовательский промпт");
        add(p + "prompt_edit.screenshot_title", "Изменить промпт для скриншотов");
        add(p + "prompt_edit.hint", "Оставьте пустым, чтобы использовать встроенный промпт");
    }
}
