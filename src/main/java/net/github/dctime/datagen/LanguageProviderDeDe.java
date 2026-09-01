package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** German (de_de) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderDeDe extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderDeDe(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "de_de");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "Übersetzungscache löschen");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "Screenshot-Übersetzung im GUI anzeigen (halten)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "Angezeigte Übersetzung neu übersetzen (halten)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "API-Schlüssel");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "Modellname");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "Prompt");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "Prompt für Screenshot-Übersetzung");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Jade-Integration aktivieren");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "FTB-Quests-Integration aktivieren");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "Übersetzung von Item-Tooltips aktivieren");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "Übersetzung von Fortschritten aktivieren");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "Screenshot-Übersetzung aktivieren");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Item-Symbol in der ersten Zeile bei Jade und Tooltip mitsenden");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "Übersetzungsanimation aktivieren");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "Zeitüberschreitung");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "Funktionen");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "Anbieter");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");

        add(p + "section.service", "Übersetzungsdienst");
        add(p + "service", "Dienst");
        add(p + "model", "Modell");
        add(p + "model.custom", "Benutzerdefiniert...");
        add(p + "model.custom_id", "Benutzerdefinierte Modell-ID");
        add(p + "api_key", "API-Schlüssel");
        add(p + "api_key.show", "Anzeigen");
        add(p + "api_key.hide", "Verbergen");
        add(p + "api_key.paste", "Einfügen");
        add(p + "api_key.ollama_note", "Lokaler Dienst - kein API-Schlüssel erforderlich");
        add(p + "test_connection", "Verbindung testen");
        add(p + "test_connection.testing", "Wird getestet...");
        add(p + "test_connection.connected", "Verbunden");
        add(p + "test_connection.invalid_key", "Ungültiger API-Schlüssel");
        add(p + "test_connection.rate_limited", "Ratenlimit überschritten");
        add(p + "test_connection.cannot_connect", "Verbindung nicht möglich");
        add(p + "test_connection.http_error", "HTTP-Fehler %s");
        add(p + "test_connection.model_not_found", "Modell „%s“ nicht in der Liste gefunden (Liste ist möglicherweise unvollständig)");
        add(p + "test_connection.note", "Bestätigt nur Verbindung & Autorisierung -- keine Garantie, dass Übersetzungsanfragen erfolgreich sind");

        add(p + "section.language", "Sprache");
        add(p + "follow_game_language", "Minecraft-Sprache übernehmen");
        add(p + "follow_game_language.tooltip", "Verwendet die aktuell in den Minecraft-Optionen ausgewählte Sprache.");
        add(p + "target_language", "Zielsprache");
        add(p + "target_language.custom", "Benutzerdefiniert/Unbekannt: %s");

        add(p + "section.features", "Übersetzungsfunktionen");
        add(p + "feature.tooltip", "Item-Tooltip");
        add(p + "feature.jade", "Jade-Tooltip");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "Fortschritte");
        add(p + "feature.screenshot", "Screenshot-Übersetzung");

        add(p + "advanced_settings", "Erweiterte Einstellungen");
        add(p + "model_cache_note", "Ein Wechsel von Anbieter oder Modell macht vorhandene zwischengespeicherte Übersetzungen nicht automatisch ungültig und löscht sie nicht.");
        add(p + "clear_cache_confirm.title", "Übersetzungscache löschen?");
        add(p + "clear_cache_confirm.message", "Übersetzungsbezogene Einstellungen wurden geändert. Die vorhandenen %s zwischengespeicherten Übersetzungen löschen?");

        add(p + "section.translation", "Übersetzung");
        add(p + "include_icon", "Item-Symbol in Übersetzung einbeziehen");
        add(p + "include_icon.tooltip", "Sendet das Item-Symbol an unterstützte Vision-Modelle für einen besseren Übersetzungskontext.");
        add(p + "pretranslate_containers", "Behälter-Items vorab übersetzen");
        add(p + "pretranslate_containers.tooltip", "Übersetzt sichtbare Items in einem Behälter automatisch, bevor du mit der Maus darüberfährst. Kann mehr API-Anfragen verursachen.");
        add(p + "custom_prompt", "Benutzerdefinierter Übersetzungsprompt");
        add(p + "custom_prompt.builtin", "Integrierten Prompt verwenden");
        add(p + "custom_prompt.custom", "Benutzerdefinierter Prompt");
        add(p + "edit_custom_prompt", "Benutzerdefinierten Prompt bearbeiten");
        add(p + "edit_screenshot_prompt", "Screenshot-Prompt bearbeiten");
        add(p + "reset_to_builtin", "Auf integrierten Prompt zurücksetzen");

        add(p + "section.interface", "Oberfläche");
        add(p + "translating_animation", "Übersetzungsanimation anzeigen");
        add(p + "translating_animation.tooltip", "Zeigt während des Wartens auf ein Übersetzungsergebnis eine kleine Animation auf dem Bildschirm an.");

        add(p + "section.network", "Netzwerk");
        add(p + "timeout", "Anfrage-Zeitüberschreitung");
        add(p + "timeout.seconds", "%s s");
        add(p + "timeout.custom", "Benutzerdefiniert: %s s");
        add(p + "rpm", "Anfragen pro Minute");
        add(p + "rpm.tooltip", "Begrenzt, wie viele KI-Übersetzungsanfragen innerhalb von 60 Sekunden gesendet werden können. Verringere diesen Wert, wenn dein Anbieter HTTP 429 zurückgibt.");
        add(p + "rpm.custom", "Benutzerdefiniert: %s");

        add(p + "section.cache", "Cache");
        add(p + "clear_cache", "Übersetzungscache löschen (%s Einträge)");

        add(p + "prompt_edit.title", "Benutzerdefinierten Prompt bearbeiten");
        add(p + "prompt_edit.screenshot_title", "Screenshot-Prompt bearbeiten");
        add(p + "prompt_edit.hint", "Leer lassen, um den integrierten Prompt zu verwenden");
    }
}
