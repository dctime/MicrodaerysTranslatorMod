package net.github.dctime.libs;

import java.util.Map;
import java.util.Set;

/**
 * Built-in default translation prompts, one per supported target language, each written
 * NATIVELY in that language (not a single template with the language name substituted in --
 * the wording of the rules themselves is in the target language). No Minecraft/NeoForge
 * dependency on purpose (see TargetLanguage/JsonUtil/RetryPolicy).
 *
 * Config.PROMPT/PROMPT_SCREENSHOT stay as free-text overrides: when the player leaves them
 * blank (the default), Translator.resolvePrompt() picks the template here for the resolved
 * target language; a non-blank config value overrides this for every language instead.
 *
 * Deliberately a closed, curated set (matches TargetLanguage's own philosophy) -- not a
 * pluggable i18n framework. An unlisted language code falls back to a generic English-authored
 * template (English being the safest common ground for instructing an LLM), parameterized with
 * %s for the language name, same substitution style the old single-template design used.
 */
public class PromptTemplates {

    private record Templates(String prompt, String promptScreenshot) {}

    private static final Map<String, Templates> KNOWN = Map.of(
            "zh_tw", new Templates(
                    """
                    只回繁體中文的翻譯，不要多字、不要解釋。
                    遵守：
                    不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)
                    名詞使用繁體中文 Minecraft 社群慣用譯名；有官方繁體中文翻譯的詞優先採用官方翻譯。
                    字面直譯、保持簡潔；不要加背景、不要腦補。
                    標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。
                    待翻譯：
                    """,
                    """
                    請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成繁體中文

                    翻譯的格式為
                    畫面簡介:xxx\\n
                    xxx/xxx\\n(原文英文1/繁體中文譯文1)(括號裡不需要顯示)
                    xxx/xxx\\n(原文英文2/繁體中文譯文2)(括號裡不需要顯示)
                    """
            ),
            "zh_cn", new Templates(
                    """
                    只回简体中文的翻译，不要多字、不要解释。
                    遵守：
                    不翻译：模组/方块/物品 ID、路径、Key、Tag、文件名、指令(/give 等)、进度代码、颜色/格式码(§ 或 &)
                    名词使用简体中文 Minecraft 社区惯用译名；有官方简体中文翻译的词优先采用官方翻译。
                    字面直译、保持简洁；不要加背景、不要脑补。
                    标点与大小写尽量贴近原风格(专有名词维持大小写) 不要加句号。
                    待翻译：
                    """,
                    """
                    请在图片上找到所有的英文(不包含没有英文的数字)并且翻译成简体中文

                    翻译的格式为
                    画面简介:xxx\\n
                    xxx/xxx\\n(原文英文1/简体中文译文1)(括号里不需要显示)
                    xxx/xxx\\n(原文英文2/简体中文译文2)(括号里不需要显示)
                    """
            ),
            "ja_jp", new Templates(
                    """
                    日本語の翻訳のみを返してください。余計な言葉や説明は不要です。
                    ルール：
                    翻訳しない対象：MOD/ブロック/アイテムのID、パス、キー、タグ、ファイル名、コマンド(/give など)、実績コード、色/書式コード(§ または &)
                    用語は日本語版Minecraftコミュニティで一般的な訳語を使用し、公式の日本語訳がある場合はそれを優先してください。
                    直訳で簡潔に。背景説明や創作は追加しないでください。
                    句読点や大文字小文字は原文のスタイルに近づけ(固有名詞の大文字小文字は維持)、文末に句点を付けないでください。
                    翻訳対象：
                    """,
                    """
                    画像内のすべての英語(英語を含まない数字は除く)を見つけて日本語に翻訳してください

                    翻訳のフォーマット:
                    画面の概要:xxx\\n
                    xxx/xxx\\n(原文の英語1/日本語訳1)(括弧内は表示不要)
                    xxx/xxx\\n(原文の英語2/日本語訳2)(括弧内は表示不要)
                    """
            ),
            "en_us", new Templates(
                    """
                    Reply with ONLY the English translation. No extra words, no explanations.
                    Rules:
                    Do not translate: mod/block/item IDs, paths, keys, tags, filenames, commands (e.g. /give), advancement codes, color/formatting codes (§ or &).
                    Use terminology standard in the English Minecraft community; prefer the official English translation when one exists.
                    Translate literally and concisely; do not add background information or invented content.
                    Keep punctuation/casing close to the original style (keep proper nouns' casing); do not add a trailing period.
                    Text to translate:
                    """,
                    """
                    Find all text in the image that is not already in English (excluding numbers with no letters) and translate it to English

                    Format:
                    Scene summary:xxx\\n
                    xxx/xxx\\n(original text 1/English translation 1)(parentheses are not shown)
                    xxx/xxx\\n(original text 2/English translation 2)(parentheses are not shown)
                    """
            ),
            "es_es", new Templates(
                    """
                    Responde ÚNICAMENTE con la traducción al español. Sin palabras adicionales ni explicaciones.
                    Reglas:
                    No traduzcas: IDs de mods/bloques/objetos, rutas, claves, etiquetas, nombres de archivo, comandos (como /give), códigos de logros, códigos de color/formato (§ o &).
                    Usa la terminología habitual de la comunidad de Minecraft en español; prioriza la traducción oficial en español cuando exista.
                    Traduce de forma literal y concisa; no añadas contexto ni contenido inventado.
                    Mantén la puntuación y mayúsculas/minúsculas cercanas al estilo original (conserva las mayúsculas de los nombres propios); no añadas un punto final.
                    Texto a traducir:
                    """,
                    """
                    Encuentra todo el texto en inglés de la imagen (sin incluir números sin letras) y tradúcelo al español

                    Formato:
                    Resumen de la escena:xxx\\n
                    xxx/xxx\\n(texto original 1/traducción al español 1)(los paréntesis no se muestran)
                    xxx/xxx\\n(texto original 2/traducción al español 2)(los paréntesis no se muestran)
                    """
            ),
            "fr_fr", new Templates(
                    """
                    Réponds UNIQUEMENT avec la traduction en français. Sans mots supplémentaires ni explications.
                    Règles :
                    Ne traduis pas : les identifiants de mods/blocs/objets, les chemins, les clés, les tags, les noms de fichiers, les commandes (ex. /give), les codes de progression, les codes de couleur/format (§ ou &).
                    Utilise la terminologie habituelle de la communauté Minecraft francophone ; privilégie la traduction officielle en français lorsqu'elle existe.
                    Traduis littéralement et de façon concise ; n'ajoute ni contexte ni contenu inventé.
                    Garde la ponctuation et la casse proches du style d'origine (conserve la casse des noms propres) ; n'ajoute pas de point final.
                    Texte à traduire :
                    """,
                    """
                    Trouve tout le texte en anglais dans l'image (sans les nombres sans lettres) et traduis-le en français

                    Format :
                    Résumé de la scène :xxx\\n
                    xxx/xxx\\n(texte original 1/traduction française 1)(les parenthèses ne sont pas affichées)
                    xxx/xxx\\n(texte original 2/traduction française 2)(les parenthèses ne sont pas affichées)
                    """
            )
    );

    private static final String GENERIC_PROMPT = """
            Reply with ONLY the %s translation. No extra words, no explanations.
            Rules:
            Do not translate: mod/block/item IDs, paths, keys, tags, filenames, commands (e.g. /give), advancement codes, color/formatting codes (§ or &).
            Use terminology standard for %s in the Minecraft community; prefer the official %s translation when one exists.
            Translate literally and concisely; do not add background information or invented content.
            Keep punctuation/casing close to the original style (keep proper nouns' casing); do not add a trailing period.
            Text to translate:
            """;

    private static final String GENERIC_PROMPT_SCREENSHOT = """
            Find all English text in the image (excluding numbers with no letters) and translate it to %s

            Format:
            Scene summary:xxx\\n
            xxx/xxx\\n(original English 1/%s translation 1)(parentheses not shown)
            xxx/xxx\\n(original English 2/%s translation 2)(parentheses not shown)
            """;

    // Every default value Config.PROMPT has ever shipped with, before this mod had per-language
    // templates at all. NeoForge never overwrites an existing config key's saved value when the
    // code's default changes, so a config.toml generated under any of these older versions is
    // permanently stuck on one of these exact strings -- confirmed against a real player's config
    // in the wild, not hypothetical. Recognizing them here means that player doesn't have to find
    // and manually clear the field themselves for a config file they may not even feel safe
    // hand-editing. This is a narrow, exact-match allowlist of specific past defaults, not a
    // general version-migration system.
    private static final Set<String> LEGACY_PROMPT_DEFAULTS = Set.of(
            // shipped before target_language existed at all (no %s anywhere)
            "只回繁體中文的翻譯，不要多字、不要解釋。\n" +
            "遵守：\n" +
            "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
            "名詞遵循遊戲慣用：block=方塊、slab=半磚、stairs=樓梯、planks=木材、log=原木、ore=礦石、ingot=錠、nugget=金粒、dye=染料、bucket=桶、stack=堆疊、craft=合成、smelt=熔煉、furnace=熔爐、blast furnace=高爐、smoker=煙燻爐、enchant=附魔、anvil=鐵砧、loot=戰利品、biome=生態域\n" +
            "優先使用《Minecraft》繁中(zh_tw)官方譯名；無官方譯名則用台灣社群慣用語。\n" +
            "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
            "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
            "待翻譯：\n",
            // shipped while target_language existed but the rules themselves were still hardcoded
            // Traditional Chinese, only the first line's language name was parameterized
            "只回%s的翻譯，不要多字、不要解釋。\n" +
            "遵守：\n" +
            "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
            "名詞遵循遊戲慣用：block=方塊、slab=半磚、stairs=樓梯、planks=木材、log=原木、ore=礦石、ingot=錠、nugget=金粒、dye=染料、bucket=桶、stack=堆疊、craft=合成、smelt=熔煉、furnace=熔爐、blast furnace=高爐、smoker=煙燻爐、enchant=附魔、anvil=鐵砧、loot=戰利品、biome=生態域\n" +
            "優先使用《Minecraft》繁中(zh_tw)官方譯名；無官方譯名則用台灣社群慣用語。\n" +
            "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
            "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
            "待翻譯：\n",
            // the short-lived "language-agnostic single template" version (superseded the same
            // round it shipped, once per-language native templates replaced it)
            "只回%s的翻譯，不要多字、不要解釋。\n" +
            "遵守：\n" +
            "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
            "名詞使用%s Minecraft 社群慣用譯名；有官方%s翻譯的詞優先採用官方翻譯。\n" +
            "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
            "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
            "待翻譯：\n"
    );

    private static final Set<String> LEGACY_PROMPT_SCREENSHOT_DEFAULTS = Set.of(
            // shipped before target_language existed at all (no %s anywhere)
            """
            請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成繁體中文

            翻譯的格式為
            畫面簡介:xxx\\n
            xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)
            xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)
            """,
            // shipped while target_language existed but the example format was still hardcoded
            // Traditional Chinese ("中文1"/"中文2")
            """
            請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成%s

            翻譯的格式為
            畫面簡介:xxx\\n
            xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)
            xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)
            """,
            // the short-lived "language-agnostic single template" version
            """
            請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成%s

            翻譯的格式為
            畫面簡介:xxx\\n
            xxx/xxx\\n(原文英文1/%s譯文1)(括號裡不需要顯示)
            xxx/xxx\\n(原文英文2/%s譯文2)(括號裡不需要顯示)
            """
    );

    /** True if this config value is blank OR byte-identical to a default this mod shipped in the past. */
    public static boolean isBlankOrLegacyDefault(String prompt) {
        return prompt.isBlank() || LEGACY_PROMPT_DEFAULTS.contains(prompt);
    }

    /** Same as {@link #isBlankOrLegacyDefault} but for the screenshot prompt's own separate history. */
    public static boolean isBlankOrLegacyScreenshotDefault(String prompt) {
        return prompt.isBlank() || LEGACY_PROMPT_SCREENSHOT_DEFAULTS.contains(prompt);
    }

    public static String promptFor(String languageCode) {
        Templates t = KNOWN.get(normalize(languageCode));
        return t != null ? t.prompt() : GENERIC_PROMPT.replace("%s", TargetLanguage.displayName(languageCode));
    }

    public static String screenshotPromptFor(String languageCode) {
        Templates t = KNOWN.get(normalize(languageCode));
        return t != null ? t.promptScreenshot() : GENERIC_PROMPT_SCREENSHOT.replace("%s", TargetLanguage.displayName(languageCode));
    }

    private static String normalize(String languageCode) {
        return languageCode == null ? "" : languageCode.trim().toLowerCase();
    }
}
