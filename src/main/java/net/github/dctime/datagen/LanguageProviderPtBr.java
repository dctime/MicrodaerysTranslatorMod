package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** Portuguese (Brazil, pt_br) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderPtBr extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderPtBr(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "pt_br");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "Limpar cache de tradução");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "Mostrar tradução da captura de tela na GUI (segurar)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "Traduzir novamente o conteúdo exibido (segurar)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "Chave de API");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "Nome do modelo");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "Prompt");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "Prompt para tradução de capturas de tela");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Ativar integração com Jade");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "Ativar integração com FTB Quests");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "Ativar tradução de dicas de itens");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "Ativar tradução de conquistas");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "Ativar tradução de capturas de tela");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Ativar envio do ícone do item na primeira linha do Jade e das dicas");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "Ativar animação de tradução");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "Tempo limite");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "Funções");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "Provedor");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "Serviço de tradução");
        add(p + "service", "Serviço");
        add(p + "model", "Modelo");
        add(p + "model.custom", "Personalizado...");
        add(p + "model.custom_id", "ID de modelo personalizado");
        add(p + "api_key", "Chave de API");
        add(p + "api_key.show", "Mostrar");
        add(p + "api_key.hide", "Ocultar");
        add(p + "api_key.paste", "Colar");
        add(p + "api_key.ollama_note", "Serviço local - chave de API não é necessária");
        add(p + "test_connection", "Testar conexão");
        add(p + "test_connection.testing", "Testando...");
        add(p + "test_connection.connected", "Conectado");
        add(p + "test_connection.invalid_key", "Chave de API inválida");
        add(p + "test_connection.rate_limited", "Limite de requisições excedido");
        add(p + "test_connection.cannot_connect", "Não foi possível conectar");
        add(p + "test_connection.http_error", "Erro HTTP %s");
        add(p + "test_connection.model_not_found", "Modelo '%s' não encontrado na lista (a lista pode estar incompleta)");
        add(p + "test_connection.note", "Confirma apenas a conexão e a autorização -- não garante que as requisições de tradução terão sucesso");

        add(p + "section.language", "Idioma");
        add(p + "follow_game_language", "Seguir idioma do Minecraft");
        add(p + "follow_game_language.tooltip", "Usa o idioma atualmente selecionado nas opções do Minecraft.");
        add(p + "target_language", "Idioma de destino");
        add(p + "target_language.custom", "Personalizado/Desconhecido: %s");

        add(p + "section.features", "Funções de tradução");
        add(p + "feature.tooltip", "Dica de item");
        add(p + "feature.jade", "Dica do Jade");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "Conquistas");
        add(p + "feature.screenshot", "Tradução de captura de tela");

        add(p + "advanced_settings", "Configurações avançadas");
        add(p + "model_cache_note", "Trocar de provedor ou modelo não invalida nem limpa automaticamente as traduções já armazenadas em cache.");
        add(p + "clear_cache_confirm.title", "Limpar cache de tradução?");
        add(p + "clear_cache_confirm.message", "As configurações relacionadas à tradução foram alteradas. Limpar as %s traduções existentes em cache?");

        add(p + "section.translation", "Tradução");
        add(p + "include_icon", "Incluir ícone do item na tradução");
        add(p + "include_icon.tooltip", "Envia o ícone do item para modelos com suporte a visão, para um melhor contexto de tradução.");
        add(p + "pretranslate_containers", "Pré-traduzir itens de contêineres");
        add(p + "pretranslate_containers.tooltip", "Traduz automaticamente os itens visíveis em um contêiner antes de você passar o mouse sobre eles. Pode usar mais requisições de API.");
        add(p + "custom_prompt", "Prompt de tradução personalizado");
        add(p + "custom_prompt.builtin", "Usar prompt integrado");
        add(p + "custom_prompt.custom", "Prompt personalizado");
        add(p + "edit_custom_prompt", "Editar prompt personalizado");
        add(p + "edit_screenshot_prompt", "Editar prompt de captura de tela");
        add(p + "reset_to_builtin", "Restaurar para o integrado");

        add(p + "section.interface", "Interface");
        add(p + "translating_animation", "Mostrar animação de tradução");
        add(p + "translating_animation.tooltip", "Mostra uma pequena animação na tela enquanto aguarda o resultado da tradução.");

        add(p + "section.network", "Rede");
        add(p + "timeout", "Tempo limite da requisição");
        add(p + "timeout.seconds", "%s s");
        add(p + "timeout.custom", "Personalizado: %s s");
        add(p + "rpm", "Requisições por minuto");
        add(p + "rpm.tooltip", "Limita quantas requisições de tradução por IA podem ser enviadas em 60 segundos. Diminua esse valor se seu provedor retornar HTTP 429.");
        add(p + "rpm.custom", "Personalizado: %s");

        add(p + "section.cache", "Cache");
        add(p + "clear_cache", "Limpar cache de tradução (%s itens)");

        add(p + "prompt_edit.title", "Editar prompt personalizado");
        add(p + "prompt_edit.screenshot_title", "Editar prompt de captura de tela");
        add(p + "prompt_edit.hint", "Deixe em branco para usar o prompt integrado");
    }
}
