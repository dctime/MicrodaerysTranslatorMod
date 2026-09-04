package net.github.dctime.datagen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.minecraft.data.PackOutput;

/** French (fr_fr) strings -- see {@link LanguageProvider}'s javadoc for the per-locale-file rationale. */
public class LanguageProviderFrFr extends net.neoforged.neoforge.common.data.LanguageProvider {

    public LanguageProviderFrFr(PackOutput output) {
        super(output, MicrodaerysTranslatorClient.MODID, "fr_fr");
    }

    @Override
    protected void addTranslations() {
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_translation_cache", "Effacer le cache de traduction");
        add("key." + MicrodaerysTranslatorClient.MODID + ".show_translation_in_gui", "Afficher la traduction de la capture d'écran (maintenir)");
        add("key." + MicrodaerysTranslatorClient.MODID + ".delete_showing_translation", "Retraduire le contenu affiché (maintenir)");
        add("key.categories." + MicrodaerysTranslatorClient.MODID + ".key_mapping_category", "Google AI Studio Translator");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.API_KEY_PATH, "Clé API");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.MODEL_NAME_PATH, "Nom du modèle");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_PATH, "Instruction (prompt)");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.PROMPT_SCREENSHOT_PATH, "Instruction pour la traduction de captures d'écran");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_JADE_CONFIG_PATH, "Activer l'intégration Jade");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_FTB_QUEST_TRANSLATION_PATH, "Activer l'intégration FTB Quests");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TOOLTIP_TRANSLATION_PATH, "Activer la traduction des infobulles d'objets");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ADVANCEMENTS_CONFIG_PATH, "Activer la traduction des progrès");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_SCREENSHOT_CONFIG_PATH, "Activer la traduction des captures d'écran");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_ICON_CONFIG_PATH, "Activer l'envoi de l'icône de l'objet sur la première ligne pour Jade et les infobulles");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENABLE_TRANSLATING_ANIMATION_CONFIG_PATH, "Activer l'animation de traduction");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.TIMEOUT_DURATION_CONFIG_PATH, "Délai d'expiration");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.FEATURE_TOGGLE_PATH, "Fonctionnalités");
        add(MicrodaerysTranslatorClient.MODID + ".configuration." + Config.ENDPOINT_CONFIG_PATH, "Fournisseur");

        add(MicrodaerysTranslatorClient.MODID + ".translator.vision_unsupported",
                "Échec de la traduction ! Le modèle sélectionné ne prend pas en charge les images -- essayez un autre modèle dans l'écran de configuration.");

        String p = MicrodaerysTranslatorClient.MODID + ".config.";
        add(p + "title", "Microdaery's Translator");
        add(p + "options_button", "Microdaery's Translator...");

        add(p + "section.service", "Service de traduction");
        add(p + "service", "Service");
        add(p + "provider.google", "Google AI Studio");
        add(p + "provider.nvidia", "NVIDIA NIM");
        add(p + "provider.groq", "Groq");
        add(p + "provider.openrouter", "OpenRouter");
        add(p + "provider.mistral", "Mistral AI");
        add(p + "provider.deepseek", "DeepSeek");
        add(p + "provider.cerebras", "Cerebras");
        add(p + "provider.anthropic", "Anthropic Claude");
        add(p + "provider.openai", "OpenAI");
        add(p + "provider.ollama", "Ollama (Local)");
        add(p + "provider.custom", "Fournisseur personnalisé");

        add(p + "provider_mode", "Mode de fournisseur");
        add(p + "provider_mode.single", "Unique");
        add(p + "provider_mode.single.tooltip", "Utilise exactement un fournisseur. Aucun basculement automatique en cas d'échec -- identique au comportement d'origine du mod.");
        add(p + "provider_mode.priority", "Priorité");
        add(p + "provider_mode.priority.tooltip", "Essaie les fournisseurs activés dans l'ordre de priorité, en basculant vers le suivant en cas d'échec temporaire (limite de débit, délai dépassé, erreur de connexion, erreur serveur).");
        add(p + "provider_mode.round_robin", "Tourniquet");
        add(p + "provider_mode.round_robin.tooltip", "Alterne équitablement entre les fournisseurs activés et disponibles.");
        add(p + "provider_mode.automatic", "Automatique");
        add(p + "provider_mode.automatic.tooltip", "Choisit automatiquement le meilleur fournisseur disponible selon la charge actuelle, les échecs récents et la latence, la priorité servant de critère secondaire. Continue de basculer en cas d'échec temporaire. Recommandé.");
        add(p + "manage_providers", "Gérer les fournisseurs");
        add(p + "manage_providers.note", "Activez ou désactivez chaque fournisseur ici. Utilisez le bouton Configure pour régler la clé API, le modèle, la priorité et les requêtes par minute.");
        add(p + "provider.configure", "Configurer");
        add(p + "provider.enabled", "Activé");
        add(p + "provider.priority", "Priorité");
        add(p + "provider.priority.tooltip", "Un nombre plus petit est essayé en premier en mode Priority, et sert de critère secondaire en mode Automatic. 1 est la priorité la plus haute ; la plupart des joueurs n'ont pas besoin de la modifier.");
        add(p + "provider.rpm", "Requêtes par minute (ce fournisseur)");
        add(p + "provider.rpm.tooltip", "Ceci est un point de départ suggéré par le mod, pas un quota officiel garanti -- ajustez-le selon la limite réelle de votre propre compte.");
        add(p + "provider.status", "État");
        add(p + "provider.status.ready", "Prêt");
        add(p + "provider.status.rate_limited", "Limite de débit atteinte (%ss)");
        add(p + "provider.status.cooldown", "En attente (%ss)");
        add(p + "provider.status.invalid_key", "Clé API invalide");
        add(p + "provider.status.cannot_connect", "Connexion impossible");
        add(p + "provider.status.disabled", "Désactivé");
        add(p + "provider.status.detail.untried", "(jamais essayé)");
        add(p + "provider.status.detail", "(moy. %ss, essayé il y a %ss)");

        add(p + "model", "Modèle");
        add(p + "model.custom", "Personnalisé...");
        add(p + "model.custom_id", "ID de modèle personnalisé");
        add(p + "api_key", "Clé API");
        add(p + "api_key.show", "Afficher");
        add(p + "api_key.hide", "Masquer");
        add(p + "api_key.paste", "Coller");
        add(p + "api_key.ollama_note", "Service local - clé API non requise");
        add(p + "test_connection", "Tester la connexion");
        add(p + "test_connection.testing", "Test en cours...");
        add(p + "test_connection.connected", "Connecté");
        add(p + "test_connection.invalid_key", "Clé API invalide");
        add(p + "test_connection.rate_limited", "Limite de débit dépassée");
        add(p + "test_connection.cannot_connect", "Connexion impossible");
        add(p + "test_connection.http_error", "Erreur HTTP %s");
        add(p + "test_connection.model_not_found", "Modèle « %s » introuvable dans la liste (la liste est peut-être incomplète)");
        add(p + "test_connection.invalid_base_url", "URL de base invalide");
        add(p + "test_connection.note", "Confirme uniquement la connexion et l'autorisation -- ne garantit pas que les requêtes de traduction réussiront");

        add(p + "custom_provider.name", "Nom du fournisseur");
        add(p + "custom_provider.base_url", "URL de base");
        add(p + "custom_provider.authentication", "Authentification");
        add(p + "custom_provider.authentication.bearer", "Jeton Bearer");
        add(p + "custom_provider.authentication.none", "Aucune");
        add(p + "custom_provider.supports_images", "Prend en charge les images");
        add(p + "custom_provider.privacy_note", "Les requêtes et le contenu de traduction sont envoyés directement au serveur configuré ci-dessus.");

        add(p + "section.language", "Langue");
        add(p + "follow_game_language", "Suivre la langue de Minecraft");
        add(p + "follow_game_language.tooltip", "Utilise la langue actuellement sélectionnée dans les options de Minecraft.");
        add(p + "target_language", "Langue cible");
        add(p + "target_language.custom", "Personnalisé/Inconnu : %s");

        add(p + "section.features", "Fonctions de traduction");
        add(p + "feature.tooltip", "Infobulle d'objet");
        add(p + "feature.jade", "Infobulle Jade");
        add(p + "feature.ftbquests", "FTB Quests");
        add(p + "feature.advancements", "Progrès");
        add(p + "feature.screenshot", "Traduction de capture d'écran");

        add(p + "advanced_settings", "Paramètres avancés");
        add(p + "model_cache_note", "Changer de fournisseur ou de modèle n'invalide ni n'efface automatiquement les anciennes traductions mises en cache.");
        add(p + "clear_cache_confirm.title", "Effacer le cache de traduction ?");
        add(p + "clear_cache_confirm.message", "Des paramètres liés à la traduction ont été modifiés. Effacer les %s traductions actuellement en cache ?");

        add(p + "section.translation", "Traduction");
        add(p + "include_icon", "Inclure l'icône de l'objet dans la traduction");
        add(p + "include_icon.tooltip", "Envoie l'icône de l'objet aux modèles compatibles avec la vision pour un meilleur contexte de traduction.");
        add(p + "pretranslate_containers", "Pré-traduire les objets des conteneurs");
        add(p + "pretranslate_containers.tooltip", "Traduit automatiquement les objets visibles d'un conteneur avant même que vous les survoliez. Peut consommer plus de requêtes API.");
        add(p + "custom_prompt", "Instruction de traduction personnalisée");
        add(p + "custom_prompt.builtin", "Utiliser l'instruction intégrée");
        add(p + "custom_prompt.custom", "Instruction personnalisée");
        add(p + "edit_custom_prompt", "Modifier l'instruction personnalisée");
        add(p + "edit_screenshot_prompt", "Modifier l'instruction pour les captures d'écran");
        add(p + "reset_to_builtin", "Réinitialiser à l'instruction intégrée");

        add(p + "section.interface", "Interface");
        add(p + "translating_animation", "Afficher l'animation de traduction");
        add(p + "translating_animation.tooltip", "Affiche une petite animation à l'écran en attendant le résultat de la traduction.");

        add(p + "section.network", "Réseau");
        add(p + "timeout", "Délai d'expiration des requêtes");
        add(p + "timeout.seconds", "%s s");
        add(p + "timeout.custom", "Personnalisé : %s s");
        add(p + "rpm", "Requêtes par minute");
        add(p + "rpm.tooltip", "Limite le nombre de requêtes de traduction IA envoyées en 60 secondes. Réduisez cette valeur si votre fournisseur renvoie une erreur HTTP 429.");
        add(p + "rpm.custom", "Personnalisé : %s");

        add(p + "section.cache", "Cache");
        add(p + "clear_cache", "Effacer le cache de traduction (%s entrées)");

        add(p + "prompt_edit.title", "Modifier l'instruction personnalisée");
        add(p + "prompt_edit.screenshot_title", "Modifier l'instruction pour les captures d'écran");
        add(p + "prompt_edit.hint", "Laisser vide pour utiliser l'instruction intégrée");
    }
}
