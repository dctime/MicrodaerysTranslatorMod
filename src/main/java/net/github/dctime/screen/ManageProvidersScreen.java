package net.github.dctime.screen;

import net.minecraft.util.text.TranslationTextComponent;
import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.provider.ProviderInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/**
 * Lists every provider, one row each, with an ON/OFF toggle and a Configure button into {@link
 * ProviderDetailScreen} -- reached from {@code TranslatorConfigScreen}'s "Manage Providers" button.
 * Deliberately NOT drag-and-drop reorderable (spec's own explicit "如果實作 drag & drop 太複雜：不要
 * 做"): ordering is instead a Priority VALUE edited per-provider in {@link ProviderDetailScreen},
 * which needs no custom input handling at all, just another CycleButton.
 * <p>
 * Edits {@link PendingTranslatorConfig} directly (the ON/OFF toggle here, and everything {@link
 * ProviderDetailScreen} edits) -- same pending-state discipline as every other screen in this
 * package: nothing here ever calls {@code Config.set(...)} or {@code Config.save()} itself. Only
 * {@code TranslatorConfigScreen}'s own Done button persists; this screen's own footer ("Done", the
 * default {@code OptionsSubScreen} one) is really just "back", exactly like {@link
 * TranslatorAdvancedConfigScreen}'s own footer.
 * <p>
 * NOT covered by any headless test -- see {@link TranslatorConfigScreen}'s javadoc for why.
 */
public class ManageProvidersScreen extends CompatOptionsSubScreen {

    private static final String P = MicrodaerysTranslatorClient.MODID + ".config.";
    private static final int SECTION_WIDTH = 310;

    private final PendingTranslatorConfig pending;

    public ManageProvidersScreen(Screen parent, PendingTranslatorConfig pending) {
        super(parent, Minecraft.getInstance().options, new TranslationTextComponent(P + "manage_providers"));
        this.pending = pending;
    }

    @Override
    protected void addOptions() {
        for (ProviderInfo info : ProviderInfo.ALL) {
            Config.EndPoint endpoint = info.endpoint();

            CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(pending.isProviderEnabled(endpoint))
                    .displayOnlyValue()
                    .create(0, 0, 150, 20, new TranslationTextComponent(info.displayNameKey()), (btn, val) -> pending.setProviderEnabled(endpoint, val));

            Button configureButton = CompatButton.builder(new TranslationTextComponent(P + "provider.configure"),
                    b -> minecraft.setScreen(new ProviderDetailScreen(this, pending, endpoint))).build();

            list.addSmall(new StringWidget(new TranslationTextComponent(info.displayNameKey()), font).alignLeft(), enabledButton);
            list.addSmall(configureButton, null);
        }

        list.addSmall(new StringWidget(SECTION_WIDTH, CompatButton.DEFAULT_HEIGHT,
                new TranslationTextComponent(P + "manage_providers.note"), font).alignLeft(), null);
    }
}
