package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** Spanish (es_es) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderEsEs extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderEsEs(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "es_es");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "Borrar caché de traducción");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "Mostrar traducción de captura en pantalla (mantener)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "Volver a traducir el contenido mostrado (mantener)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "Clave de API");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "Nombre del modelo");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "Instrucción (prompt)");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "Instrucción para traducir capturas de pantalla");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Activar integración con Jade");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "Activar integración con FTB Quests");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "Activar traducción de descripciones emergentes");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "Activar traducción de logros");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "Activar traducción de capturas de pantalla");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Activar el envío del icono del objeto en la primera línea de Jade y las descripciones");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "Activar animación de traducción");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "Tiempo de espera");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "Funciones");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "Proveedor");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");

        add(p + "section.service", "Servicio de traducción");
        add(p + "service", "Servicio");
        add(p + "model", "Modelo");
        add(p + "model.custom", "Personalizado...");
        add(p + "model.custom_id", "ID de modelo personalizado");
        add(p + "api_key", "Clave de API");
        add(p + "api_key.show", "Mostrar");
        add(p + "api_key.hide", "Ocultar");
        add(p + "api_key.paste", "Pegar");
        add(p + "api_key.ollama_note", "Servicio local - no se necesita clave de API");
        add(p + "test_connection", "Probar conexión");
        add(p + "test_connection.testing", "Probando...");
        add(p + "test_connection.connected", "Conectado");
        add(p + "test_connection.invalid_key", "Clave de API no válida");
        add(p + "test_connection.rate_limited", "Límite de solicitudes excedido");
        add(p + "test_connection.cannot_connect", "No se puede conectar");
        add(p + "test_connection.http_error", "Error HTTP %s");
        add(p + "test_connection.model_not_found", "No se encontró el modelo '%s' en la lista (la lista puede estar incompleta)");
        add(p + "test_connection.note", "Solo confirma la conexión y autorización -- no garantiza que las solicitudes de traducción vayan a funcionar");

        add(p + "section.language", "Idioma");
        add(p + "follow_game_language", "Seguir el idioma de Minecraft");
        add(p + "follow_game_language.tooltip", "Usa el idioma que tienes seleccionado actualmente en las opciones de Minecraft.");
        add(p + "target_language", "Idioma de destino");
        add(p + "target_language.custom", "Personalizado/Desconocido: %s");

        add(p + "section.features", "Funciones de traducción");
        add(p + "feature.tooltip", "Descripción del objeto");
        add(p + "feature.jade", "Descripción de Jade");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "Logros");
        add(p + "feature.screenshot", "Traducción de capturas de pantalla");

        add(p + "advanced_settings", "Configuración avanzada");
        add(p + "model_cache_note", "Cambiar de proveedor o modelo no invalida ni borra automáticamente las traducciones ya guardadas en caché.");
        add(p + "clear_cache_confirm.title", "¿Borrar la caché de traducción?");
        add(p + "clear_cache_confirm.message", "Se han cambiado ajustes relacionados con la traducción. ¿Quieres borrar las %s traducciones guardadas en caché?");

        add(p + "section.translation", "Traducción");
        add(p + "include_icon", "Incluir el icono del objeto en la traducción");
        add(p + "include_icon.tooltip", "Envía el icono del objeto a los modelos con visión compatibles para dar mejor contexto a la traducción.");
        add(p + "pretranslate_containers", "Pretraducir objetos en contenedores");
        add(p + "pretranslate_containers.tooltip", "Traduce automáticamente los objetos visibles dentro de un contenedor antes de pasar el cursor sobre ellos. Puede aumentar el número de solicitudes a la API.");
        add(p + "custom_prompt", "Instrucción de traducción personalizada");
        add(p + "custom_prompt.builtin", "Usar instrucción incorporada");
        add(p + "custom_prompt.custom", "Instrucción personalizada");
        add(p + "edit_custom_prompt", "Editar instrucción personalizada");
        add(p + "edit_screenshot_prompt", "Editar instrucción para capturas de pantalla");
        add(p + "reset_to_builtin", "Restablecer a la incorporada");

        add(p + "section.interface", "Interfaz");
        add(p + "translating_animation", "Mostrar animación de traducción");
        add(p + "translating_animation.tooltip", "Muestra una pequeña animación en pantalla mientras se espera el resultado de la traducción.");

        add(p + "section.network", "Red");
        add(p + "timeout", "Tiempo de espera de la solicitud");
        add(p + "timeout.seconds", "%s s");
        add(p + "timeout.custom", "Personalizado: %s s");
        add(p + "rpm", "Solicitudes por minuto");
        add(p + "rpm.tooltip", "Limita cuántas solicitudes de traducción con IA se pueden enviar en 60 segundos. Reduce este valor si tu proveedor responde con HTTP 429.");
        add(p + "rpm.custom", "Personalizado: %s");

        add(p + "section.cache", "Caché");
        add(p + "clear_cache", "Borrar caché de traducción (%s entradas)");

        add(p + "prompt_edit.title", "Editar instrucción personalizada");
        add(p + "prompt_edit.screenshot_title", "Editar instrucción para capturas de pantalla");
        add(p + "prompt_edit.hint", "Déjalo en blanco para usar la instrucción incorporada");
    }
}
