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

    private static final class Templates {
        private final String prompt;
        private final String promptScreenshot;

        private Templates(String prompt, String promptScreenshot) {
            this.prompt = prompt;
            this.promptScreenshot = promptScreenshot;
        }

        public String prompt() {
            return prompt;
        }

        public String promptScreenshot() {
            return promptScreenshot;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Templates other = (Templates) o;
            return java.util.Objects.equals(prompt, other.prompt)
                    && java.util.Objects.equals(promptScreenshot, other.promptScreenshot);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(prompt, promptScreenshot);
        }

        @Override
        public String toString() {
            return "Templates[prompt=" + prompt + ", promptScreenshot=" + promptScreenshot + "]";
        }
    }

    // Map.ofEntries(), not Map.of(): Map.of() has no varargs overload past 10 key-value pairs
    // (it tops out at Map.of(k1,v1, ..., k10,v10)) -- this map was AT that limit the moment the
    // 10th language (pt_br) was added, so the 11th would have failed to compile with a confusing
    // "no suitable method found for of(...)" error that gives no hint the real issue is a hidden
    // arity cap. Map.ofEntries() takes a varargs array of Map.entry(...) instead, so there's no
    // count ceiling to run into again.
    private static final Map<String, Templates> KNOWN = CollectionUtil.mapOfEntries(
            CollectionUtil.entry("zh_tw", new Templates(
                    ("只回繁體中文的翻譯，不要多字、不要解釋。\n" +
                            "遵守：\n" +
                            "不翻譯：模組/方塊/物品 ID、路徑、Key、Tag、檔名、指令(/give 等)、進度代碼、顏色/格式碼(§ 或 &)\n" +
                            "名詞使用繁體中文 Minecraft 社群慣用譯名；有官方繁體中文翻譯的詞優先採用官方翻譯。\n" +
                            "字面直譯、保持簡潔；不要加背景、不要腦補。\n" +
                            "標點與大小寫盡量貼近原風格(專有名詞維持大小寫) 不要加句號。\n" +
                            "待翻譯：\n"),
                    ("請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成繁體中文\n" +
                            "\n" +
                            "翻譯的格式為\n" +
                            "畫面簡介:xxx\\n\n" +
                            "xxx/xxx\\n(原文英文1/繁體中文譯文1)(括號裡不需要顯示)\n" +
                            "xxx/xxx\\n(原文英文2/繁體中文譯文2)(括號裡不需要顯示)\n")
            )),
            CollectionUtil.entry("zh_cn", new Templates(
                    ("只回简体中文的翻译，不要多字、不要解释。\n" +
                            "遵守：\n" +
                            "不翻译：模组/方块/物品 ID、路径、Key、Tag、文件名、指令(/give 等)、进度代码、颜色/格式码(§ 或 &)\n" +
                            "名词使用简体中文 Minecraft 社区惯用译名；有官方简体中文翻译的词优先采用官方翻译。\n" +
                            "字面直译、保持简洁；不要加背景、不要脑补。\n" +
                            "标点与大小写尽量贴近原风格(专有名词维持大小写) 不要加句号。\n" +
                            "待翻译：\n"),
                    ("请在图片上找到所有的英文(不包含没有英文的数字)并且翻译成简体中文\n" +
                            "\n" +
                            "翻译的格式为\n" +
                            "画面简介:xxx\\n\n" +
                            "xxx/xxx\\n(原文英文1/简体中文译文1)(括号里不需要显示)\n" +
                            "xxx/xxx\\n(原文英文2/简体中文译文2)(括号里不需要显示)\n")
            )),
            CollectionUtil.entry("ja_jp", new Templates(
                    ("日本語の翻訳のみを返してください。余計な言葉や説明は不要です。\n" +
                            "ルール：\n" +
                            "翻訳しない対象：MOD/ブロック/アイテムのID、パス、キー、タグ、ファイル名、コマンド(/give など)、実績コード、色/書式コード(§ または &)\n" +
                            "用語は日本語版Minecraftコミュニティで一般的な訳語を使用し、公式の日本語訳がある場合はそれを優先してください。\n" +
                            "直訳で簡潔に。背景説明や創作は追加しないでください。\n" +
                            "句読点や大文字小文字は原文のスタイルに近づけ(固有名詞の大文字小文字は維持)、文末に句点を付けないでください。\n" +
                            "翻訳対象：\n"),
                    ("画像内のすべての英語(英語を含まない数字は除く)を見つけて日本語に翻訳してください\n" +
                            "\n" +
                            "翻訳のフォーマット:\n" +
                            "画面の概要:xxx\\n\n" +
                            "xxx/xxx\\n(原文の英語1/日本語訳1)(括弧内は表示不要)\n" +
                            "xxx/xxx\\n(原文の英語2/日本語訳2)(括弧内は表示不要)\n")
            )),
            CollectionUtil.entry("en_us", new Templates(
                    ("Reply with ONLY the English translation. No extra words, no explanations.\n" +
                            "Rules:\n" +
                            "Do not translate: mod/block/item IDs, paths, keys, tags, filenames, commands (e.g. /give), advancement codes, color/formatting codes (§ or &).\n" +
                            "Use terminology standard in the English Minecraft community; prefer the official English translation when one exists.\n" +
                            "Translate literally and concisely; do not add background information or invented content.\n" +
                            "Keep punctuation/casing close to the original style (keep proper nouns' casing); do not add a trailing period.\n" +
                            "Text to translate:\n"),
                    ("Find all text in the image that is not already in English (excluding numbers with no letters) and translate it to English\n" +
                            "\n" +
                            "Format:\n" +
                            "Scene summary:xxx\\n\n" +
                            "xxx/xxx\\n(original text 1/English translation 1)(parentheses are not shown)\n" +
                            "xxx/xxx\\n(original text 2/English translation 2)(parentheses are not shown)\n")
            )),
            CollectionUtil.entry("es_es", new Templates(
                    ("Responde ÚNICAMENTE con la traducción al español. Sin palabras adicionales ni explicaciones.\n" +
                            "Reglas:\n" +
                            "No traduzcas: IDs de mods/bloques/objetos, rutas, claves, etiquetas, nombres de archivo, comandos (como /give), códigos de logros, códigos de color/formato (§ o &).\n" +
                            "Usa la terminología habitual de la comunidad de Minecraft en español; prioriza la traducción oficial en español cuando exista.\n" +
                            "Traduce de forma literal y concisa; no añadas contexto ni contenido inventado.\n" +
                            "Mantén la puntuación y mayúsculas/minúsculas cercanas al estilo original (conserva las mayúsculas de los nombres propios); no añadas un punto final.\n" +
                            "Texto a traducir:\n"),
                    ("Encuentra todo el texto en inglés de la imagen (sin incluir números sin letras) y tradúcelo al español\n" +
                            "\n" +
                            "Formato:\n" +
                            "Resumen de la escena:xxx\\n\n" +
                            "xxx/xxx\\n(texto original 1/traducción al español 1)(los paréntesis no se muestran)\n" +
                            "xxx/xxx\\n(texto original 2/traducción al español 2)(los paréntesis no se muestran)\n")
            )),
            CollectionUtil.entry("fr_fr", new Templates(
                    ("Réponds UNIQUEMENT avec la traduction en français. Sans mots supplémentaires ni explications.\n" +
                            "Règles :\n" +
                            "Ne traduis pas : les identifiants de mods/blocs/objets, les chemins, les clés, les tags, les noms de fichiers, les commandes (ex. /give), les codes de progression, les codes de couleur/format (§ ou &).\n" +
                            "Utilise la terminologie habituelle de la communauté Minecraft francophone ; privilégie la traduction officielle en français lorsqu'elle existe.\n" +
                            "Traduis littéralement et de façon concise ; n'ajoute ni contexte ni contenu inventé.\n" +
                            "Garde la ponctuation et la casse proches du style d'origine (conserve la casse des noms propres) ; n'ajoute pas de point final.\n" +
                            "Texte à traduire :\n"),
                    ("Trouve tout le texte en anglais dans l'image (sans les nombres sans lettres) et traduis-le en français\n" +
                            "\n" +
                            "Format :\n" +
                            "Résumé de la scène :xxx\\n\n" +
                            "xxx/xxx\\n(texte original 1/traduction française 1)(les parenthèses ne sont pas affichées)\n" +
                            "xxx/xxx\\n(texte original 2/traduction française 2)(les parenthèses ne sont pas affichées)\n")
            )),
            CollectionUtil.entry("ko_kr", new Templates(
                    ("한국어 번역만 답하세요. 추가 설명 없이.\n" +
                            "규칙:\n" +
                            "번역하지 않음: 모드/블록/아이템 ID, 경로, 키, 태그, 파일명, 명령어(/give 등), 진행 상황 코드, 색상/서식 코드(§ 또는 &)\n" +
                            "한국 마인크래프트 커뮤니티에서 일반적으로 사용하는 용어를 사용하고, 공식 한국어 번역이 있으면 우선 사용하세요.\n" +
                            "직역하고 간결하게; 배경 설명이나 창작 내용을 추가하지 마세요.\n" +
                            "문장 부호와 대소문자는 원문 스타일에 최대한 가깝게(고유명사의 대소문자는 유지) 마침표를 붙이지 마세요.\n" +
                            "번역할 텍스트:\n"),
                    ("이미지에서 영어가 아닌 모든 텍스트(문자가 없는 숫자는 제외)를 찾아 한국어로 번역하세요\n" +
                            "\n" +
                            "형식:\n" +
                            "화면 요약:xxx\\n\n" +
                            "xxx/xxx\\n(원문 1/한국어 번역 1)(괄호는 표시하지 않음)\n" +
                            "xxx/xxx\\n(원문 2/한국어 번역 2)(괄호는 표시하지 않음)\n")
            )),
            CollectionUtil.entry("ru_ru", new Templates(
                    ("Отвечай ТОЛЬКО переводом на русский язык. Без лишних слов и объяснений.\n" +
                            "Правила:\n" +
                            "Не переводить: ID модов/блоков/предметов, пути, ключи, теги, имена файлов, команды (например /give), коды достижений, коды цвета/формата (§ или &).\n" +
                            "Используй терминологию, принятую в русскоязычном сообществе Minecraft; отдавай предпочтение официальному русскому переводу, если он есть.\n" +
                            "Переводи дословно и кратко; не добавляй фоновую информацию или выдуманное содержание.\n" +
                            "Сохраняй пунктуацию/регистр близкими к оригинальному стилю (сохраняй регистр имён собственных); не добавляй точку в конце.\n" +
                            "Текст для перевода:\n"),
                    ("Найди весь текст на изображении, который ещё не на русском (кроме чисел без букв), и переведи его на русский\n" +
                            "\n" +
                            "Формат:\n" +
                            "Краткое описание сцены:xxx\\n\n" +
                            "xxx/xxx\\n(оригинальный текст 1/перевод на русский 1)(скобки не показывать)\n" +
                            "xxx/xxx\\n(оригинальный текст 2/перевод на русский 2)(скобки не показывать)\n")
            )),
            CollectionUtil.entry("de_de", new Templates(
                    ("Antworte NUR mit der deutschen Übersetzung. Keine zusätzlichen Worte, keine Erklärungen.\n" +
                            "Regeln:\n" +
                            "Nicht übersetzen: Mod-/Block-/Item-IDs, Pfade, Schlüssel, Tags, Dateinamen, Befehle (z. B. /give), Fortschrittscodes, Farb-/Formatierungscodes (§ oder &).\n" +
                            "Verwende die in der deutschen Minecraft-Community übliche Terminologie; bevorzuge die offizielle deutsche Übersetzung, falls vorhanden.\n" +
                            "Übersetze wörtlich und knapp; füge keine Hintergrundinformationen oder erfundenen Inhalt hinzu.\n" +
                            "Halte Zeichensetzung/Groß- und Kleinschreibung nah am Originalstil (Groß-/Kleinschreibung von Eigennamen beibehalten); keinen Schlusspunkt hinzufügen.\n" +
                            "Zu übersetzender Text:\n"),
                    ("Finde allen Text im Bild, der noch nicht auf Deutsch ist (ausgenommen Zahlen ohne Buchstaben), und übersetze ihn ins Deutsche\n" +
                            "\n" +
                            "Format:\n" +
                            "Szenenübersicht:xxx\\n\n" +
                            "xxx/xxx\\n(Originaltext 1/deutsche Übersetzung 1)(Klammern nicht anzeigen)\n" +
                            "xxx/xxx\\n(Originaltext 2/deutsche Übersetzung 2)(Klammern nicht anzeigen)\n")
            )),
            CollectionUtil.entry("pt_br", new Templates(
                    ("Responda APENAS com a tradução em português. Sem palavras extras, sem explicações.\n" +
                            "Regras:\n" +
                            "Não traduzir: IDs de mods/blocos/itens, caminhos, chaves, tags, nomes de arquivo, comandos (como /give), códigos de conquistas, códigos de cor/formatação (§ ou &).\n" +
                            "Use a terminologia padrão da comunidade brasileira de Minecraft; prefira a tradução oficial em português quando existir.\n" +
                            "Traduza de forma literal e concisa; não adicione contexto nem conteúdo inventado.\n" +
                            "Mantenha pontuação/maiúsculas e minúsculas próximas do estilo original (mantenha a grafia de nomes próprios); não adicione ponto final.\n" +
                            "Texto a traduzir:\n"),
                    ("Encontre todo o texto na imagem que ainda não está em português (exceto números sem letras) e traduza para o português\n" +
                            "\n" +
                            "Formato:\n" +
                            "Resumo da cena:xxx\\n\n" +
                            "xxx/xxx\\n(texto original 1/tradução em português 1)(parênteses não devem ser exibidos)\n" +
                            "xxx/xxx\\n(texto original 2/tradução em português 2)(parênteses não devem ser exibidos)\n")
            ))
    );

    private static final String GENERIC_PROMPT = ("Reply with ONLY the %s translation. No extra words, no explanations.\n" +
            "Rules:\n" +
            "Do not translate: mod/block/item IDs, paths, keys, tags, filenames, commands (e.g. /give), advancement codes, color/formatting codes (§ or &).\n" +
            "Use terminology standard for %s in the Minecraft community; prefer the official %s translation when one exists.\n" +
            "Translate literally and concisely; do not add background information or invented content.\n" +
            "Keep punctuation/casing close to the original style (keep proper nouns' casing); do not add a trailing period.\n" +
            "Text to translate:\n");

    private static final String GENERIC_PROMPT_SCREENSHOT = ("Find all English text in the image (excluding numbers with no letters) and translate it to %s\n" +
            "\n" +
            "Format:\n" +
            "Scene summary:xxx\\n\n" +
            "xxx/xxx\\n(original English 1/%s translation 1)(parentheses not shown)\n" +
            "xxx/xxx\\n(original English 2/%s translation 2)(parentheses not shown)\n");

    // Every default value Config.PROMPT has ever shipped with, before this mod had per-language
    // templates at all. NeoForge never overwrites an existing config key's saved value when the
    // code's default changes, so a config.toml generated under any of these older versions is
    // permanently stuck on one of these exact strings -- confirmed against a real player's config
    // in the wild, not hypothetical. Recognizing them here means that player doesn't have to find
    // and manually clear the field themselves for a config file they may not even feel safe
    // hand-editing. This is a narrow, exact-match allowlist of specific past defaults, not a
    // general version-migration system.
    private static final Set<String> LEGACY_PROMPT_DEFAULTS = CollectionUtil.setOf(
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

    private static final Set<String> LEGACY_PROMPT_SCREENSHOT_DEFAULTS = CollectionUtil.setOf(
            // shipped before target_language existed at all (no %s anywhere)
            ("請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成繁體中文\n" +
                    "\n" +
                    "翻譯的格式為\n" +
                    "畫面簡介:xxx\\n\n" +
                    "xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)\n" +
                    "xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)\n"),
            // shipped while target_language existed but the example format was still hardcoded
            // Traditional Chinese ("中文1"/"中文2")
            ("請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成%s\n" +
                    "\n" +
                    "翻譯的格式為\n" +
                    "畫面簡介:xxx\\n\n" +
                    "xxx/xxx\\n(原文英文1/中文1)(括號裡不需要顯示)\n" +
                    "xxx/xxx\\n(原文英文2/中文2)(括號裡不需要顯示)\n"),
            // the short-lived "language-agnostic single template" version
            ("請在圖片上找到所有的英文(不包含沒有英文的數字)並且翻譯成%s\n" +
                    "\n" +
                    "翻譯的格式為\n" +
                    "畫面簡介:xxx\\n\n" +
                    "xxx/xxx\\n(原文英文1/%s譯文1)(括號裡不需要顯示)\n" +
                    "xxx/xxx\\n(原文英文2/%s譯文2)(括號裡不需要顯示)\n")
    );

    /** True if this config value is blank OR byte-identical to a default this mod shipped in the past. */
    public static boolean isBlankOrLegacyDefault(String prompt) {
        return StringUtil.isBlank(prompt) || LEGACY_PROMPT_DEFAULTS.contains(prompt);
    }

    /** Same as {@link #isBlankOrLegacyDefault} but for the screenshot prompt's own separate history. */
    public static boolean isBlankOrLegacyScreenshotDefault(String prompt) {
        return StringUtil.isBlank(prompt) || LEGACY_PROMPT_SCREENSHOT_DEFAULTS.contains(prompt);
    }

    /**
     * True if this language has a native (not generic-English-fallback) template. Exists so
     * callers that need to verify "does this language actually have real coverage" (see
     * tools/verify-prompt-templates) can ask directly instead of comparing promptFor()'s output
     * against a second, independently-typed copy of what the generic fallback would produce --
     * that comparison is itself a drift risk (a copy that silently goes stale if GENERIC_PROMPT's
     * wording ever changes would compare unequal forever and never catch a real regression again).
     */
    public static boolean hasNativeTemplate(String languageCode) {
        return KNOWN.containsKey(normalize(languageCode));
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
