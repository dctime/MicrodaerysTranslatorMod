package net.github.dctime.libs;

import java.util.List;
import java.util.Map;

/**
 * Per-language lines for the one-time welcome/help message sent on world join (see
 * PlayerJoinWorldEvent). Same "small closed table, natively authored per language" shape as
 * PromptTemplates -- but keyed off the game's OWN current display language
 * (LanguageManager.getSelected()), not Translator's resolveTargetLanguage(): this message
 * explains how the mod works, so it should match whatever language Minecraft's own UI is
 * already showing the player, regardless of what language the player has configured the AI to
 * translate INTO (those two can differ, e.g. follow_game_language=false). It's also
 * deliberately NOT run through the AI translator itself -- the mod explaining itself shouldn't
 * depend on the very pipeline it's about to describe.
 */
public class WelcomeMessageTemplates {
    private static final Map<String, List<String>> LINES = Map.of(
            "zh_tw", List.of(
                    "感謝使用 Microdaery's Translator! 自動翻譯提示匡, Jade, Advancements 與 FTBQuest 的內容的小工具!",
                    "螢幕截圖會把裡面的文字翻譯成你設定的目標語言顯示在對話框",
                    "使用前請先去修改 config/microdaerystranslator-client.toml",
                    "按F4可以清除翻譯快取 按F6可以重新翻譯正在顯示的翻譯 可去按鍵設定修改",
                    "在GUI裡按Left Alt可以顯示最近翻譯的螢幕截圖 可去按鍵設定修改",
                    "如果找到bug或是想要什麼請"
            ),
            "zh_cn", List.of(
                    "感谢使用 Microdaery's Translator! 自动翻译提示框, Jade, Advancements 与 FTBQuest 内容的小工具!",
                    "截图会把里面的文字翻译成你设置的目标语言显示在对话框",
                    "使用前请先去修改 config/microdaerystranslator-client.toml",
                    "按F4可以清除翻译缓存 按F6可以重新翻译正在显示的翻译 可去按键设置修改",
                    "在GUI里按Left Alt可以显示最近翻译的截图 可去按键设置修改",
                    "如果发现bug或是想要什么功能请"
            ),
            "ja_jp", List.of(
                    "Microdaery's Translator を使ってくれてありがとう! ツールチップ、Jade、進捗、FTBQuest の内容を自動翻訳するツールです!",
                    "スクリーンショットを撮ると中の文字を設定した翻訳先の言語に翻訳してチャットに表示します",
                    "使用前に config/microdaerystranslator-client.toml を編集してください",
                    "F4で翻訳キャッシュを削除、F6で表示中の翻訳をやり直せます（キー設定変更可）",
                    "GUI画面でLeft Altを押すと直近のスクリーンショット翻訳を表示します（キー設定変更可）",
                    "バグを見つけたり要望があれば"
            ),
            "en_us", List.of(
                    "Thanks for using Microdaery's Translator! A tool that auto-translates tooltips, Jade, Advancements, and FTBQuest content!",
                    "Taking a screenshot translates the text inside it into your configured target language and shows it in chat",
                    "Before using, please edit config/microdaerystranslator-client.toml",
                    "Press F4 to clear the translation cache, F6 to re-translate what's currently shown (rebindable)",
                    "In a GUI, press Left Alt to show the most recent screenshot translation (rebindable)",
                    "If you find a bug or want a feature, please"
            ),
            "es_es", List.of(
                    "¡Gracias por usar Microdaery's Translator! Una herramienta que traduce automáticamente los tooltips, Jade, los logros y el contenido de FTBQuest!",
                    "Al hacer una captura de pantalla, el texto que contiene se traduce al idioma de destino configurado y se muestra en el chat",
                    "Antes de usarlo, edita config/microdaerystranslator-client.toml",
                    "Pulsa F4 para borrar la caché de traducciones, F6 para volver a traducir lo que se muestra (reasignable)",
                    "En una pantalla con GUI, pulsa Left Alt para ver la última captura traducida (reasignable)",
                    "Si encuentras un error o quieres pedir algo, por favor"
            ),
            "fr_fr", List.of(
                    "Merci d'utiliser Microdaery's Translator ! Un outil qui traduit automatiquement les infobulles, Jade, les progrès et le contenu de FTBQuest !",
                    "Une capture d'écran traduit le texte qu'elle contient dans la langue cible configurée et l'affiche dans le chat",
                    "Avant utilisation, modifiez config/microdaerystranslator-client.toml",
                    "Appuyez sur F4 pour vider le cache de traduction, F6 pour retraduire ce qui est affiché (réassignable)",
                    "Dans une interface GUI, appuyez sur Left Alt pour afficher la dernière capture d'écran traduite (réassignable)",
                    "Si vous trouvez un bug ou souhaitez une fonctionnalité, veuillez"
            )
    );

    // the clickable "click here" line is separate from LINES since it needs a ClickEvent
    // attached, not plain text -- see PlayerJoinWorldEvent.
    private static final Map<String, String> CLICK_HERE = Map.of(
            "zh_tw", "點這裡",
            "zh_cn", "点这里",
            "ja_jp", "ここをクリック",
            "en_us", "Click here",
            "es_es", "Haz clic aquí",
            "fr_fr", "Cliquez ici"
    );

    public static List<String> linesFor(String languageCode) {
        // Map.of()'s getOrDefault() throws NPE on a null key (unlike HashMap), so a null code
        // has to be short-circuited before ever reaching the map lookup.
        return languageCode == null ? LINES.get("en_us") : LINES.getOrDefault(languageCode, LINES.get("en_us"));
    }

    public static String clickHereFor(String languageCode) {
        return languageCode == null ? CLICK_HERE.get("en_us") : CLICK_HERE.getOrDefault(languageCode, CLICK_HERE.get("en_us"));
    }
}
