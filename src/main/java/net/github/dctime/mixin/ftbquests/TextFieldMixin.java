package net.github.dctime.mixin.ftbquests;

import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import net.github.dctime.libs.ftbquests.FormattedTextGetterSetter;
import net.github.dctime.libs.Translator;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(TextField.class)
public abstract class TextFieldMixin extends Widget implements FormattedTextGetterSetter {
    @Shadow(remap = false)
    private ITextProperties[] formattedText;

    private ITextProperties rawTranslatedText;

    @Shadow(remap = false)
    public abstract TextField resize(Theme theme);

    @Shadow(remap = false)
    public int maxWidth;

    public TextFieldMixin(Panel p) {
        super(p);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
//        System.out.println("TextField mixin appled!");
    }

    public ITextProperties[] getFormattedText() {
        return formattedText;
    }

    public void setTranslatedFormattedText(String text) {
        rawTranslatedText = new StringTextComponent(" " + text).setStyle(Translator.translatedStyle);
        // reflow with the translated text
        Theme theme = this.getGui().getTheme();
        List<ITextProperties> fullText = new java.util.ArrayList<>(Arrays.stream(formattedText).collect(Collectors.toList()));
        fullText.add(rawTranslatedText);
        this.formattedText = (ITextProperties[])theme.listFormattedStringToWidth(ITextProperties.composite(fullText), this.maxWidth).toArray(new ITextProperties[0]);
        resize(theme);
    }
}
