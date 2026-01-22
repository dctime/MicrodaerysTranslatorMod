package net.github.dctime.libs;

import net.github.dctime.libs.jade.JadeTooltipRelated;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringUtils;
import net.minecraft.util.text.*;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static net.github.dctime.libs.Translator.translationCache;

public class TooltipRelated {
    private static final Logger LOGGER = LogManager.getLogger();

    public static String getStringFromComponent(TextComponent component) {
        String resultString = "";
        if (component instanceof StringTextComponent && LoadingModList.get().getModFileById("jade") != null && JadeTooltipRelated.jadeRelatedComponentCheck(component)) {
            StringTextComponent stringTextComponent = (StringTextComponent) component;
            resultString += StringUtils.stripColor(stringTextComponent.getContents());
        } else if (component instanceof TranslationTextComponent) {
            TranslationTextComponent translationTextComponent = (TranslationTextComponent) component;
            List<String> argsText = new LinkedList<>();
            for (int argsIndex = 0; argsIndex < translationTextComponent.getArgs().length; argsIndex++) {
                Object[] argsObject = Arrays.stream(translationTextComponent.getArgs()).toArray();
                if (!(argsObject[argsIndex] instanceof TextComponent)) {
                    argsText.add(argsObject[argsIndex].toString());
                    continue;
                }
                TextComponent argTextComponent = (TextComponent) argsObject[argsIndex];
                argsText.add(getStringFromComponent(argTextComponent));
            }
            resultString += StringUtils.stripColor(I18n.get(translationTextComponent.getKey(), argsText.toArray()));
        }

        List<ITextComponent> sibings = component.getSiblings();
        for (ITextComponent iTextComponent : sibings) {
            if (iTextComponent instanceof TextComponent) {
                TextComponent textComponent = (TextComponent) iTextComponent;
                resultString += getStringFromComponent(textComponent) + "";
            }
        }

        return resultString;
    }

    public static void translateListOfText(List<? extends ITextProperties> elements, ItemStack stack) {
        for (int i = 0; i < elements.size(); i++) {
            ITextProperties e = elements.get(i);
            if (!(elements.get(i) instanceof TextComponent)) {
                return;
            }

            TextComponent component = (TextComponent) elements.get(i);
            String original = TooltipRelated.getStringFromComponent(component);

//            System.out.println(original);

            String translated;
            if (translationCache.containsKey(original))
                translated = translationCache.get(original);
            else {
                try {
                    if (i != 0) {
                        Translator.requestTranslateToTraditionalChinese(original);
                    } else {
                        Translator.requestTranslateItemStackToTraditionalChinese(original, stack);
                    }
                } catch (IOException ex) {
                    LOGGER.warn("IO Exception while translating: " + ex.getMessage());
                } catch (InterruptedException ex) {
                    LOGGER.warn("Interrupted Exception while translating: " + ex.getMessage());
                }
                return;
            }

            if (translated == null) return;
            translationCache.put(original, translated);

            if (!org.apache.commons.lang3.StringUtils.isBlank(translated))
                component.getSiblings().add(new StringTextComponent(" " + translated).setStyle(Translator.translatedStyle));
//            System.out.println(success);
//            if (text instanceof Component textComponent)
//                replaced = textComponent.copy().append(new TextComponent(" " + translated).setStyle(Translator.translatedStyle));
//            else
//            // Example transformation: prepend and uppercase
//                replaced = new TextComponent(text.getString()).append(new TextComponent(" " + translated).setStyle(Translator.translatedStyle));
//            elements.set(finalI, Either.left(replaced));
        }
    }
}
