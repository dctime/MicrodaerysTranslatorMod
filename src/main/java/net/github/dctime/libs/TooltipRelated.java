package net.github.dctime.libs;

import net.github.dctime.libs.jade.JadeTooltipRelated;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringUtils;
import net.minecraft.util.text.*;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * Reads a Jade 2.x (1.16.5) tooltip line's text. Jade's own RenderableTextComponent (item icons,
 * health bars) and TaggedTextComponent placeholders serialize to opaque strings through
 * getString(), so only plain string/translatable parts (and their siblings) are collected; section
 * sign codes are stripped. Used by {@code TestJadeRenderEvent}; the vanilla inventory tooltip path
 * uses ITextComponent.getString() directly.
 */
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
}
