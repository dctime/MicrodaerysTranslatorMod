package net.github.dctime.screen;

import net.github.dctime.Config;
import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.libs.BaseUrlUtil;
import net.github.dctime.libs.ConnectionTestStatus;
import net.github.dctime.libs.TranslationConnectionTester;
import net.github.dctime.libs.provider.AuthMode;
import net.github.dctime.libs.provider.ProviderInfo;
import net.github.dctime.libs.routing.ProviderRuntimeRegistry;
import net.github.dctime.libs.routing.ProviderRuntimeState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * One provider's full settings -- opened from {@link ManageProvidersScreen}'s per-row Configure
 * button. Enabled/Model/API Key/Priority/RPM, a live Status line, and Test Connection; Custom
 * Provider additionally gets Name/Base URL/Authentication/Supports Images (the same fields that
 * used to live directly on {@code TranslatorConfigScreen} before the Multi-Provider Router
 * refactor moved per-provider editing off the Simple screen entirely).
 * <p>
 * Edits {@link PendingTranslatorConfig} directly, through its explicit-endpoint accessors (see
 * that class's javadoc for why there's no "active provider" live view any more) -- never {@link
 * Config} itself. Only {@code TranslatorConfigScreen}'s own Done button ever calls {@link
 * PendingTranslatorConfig#saveToConfig()}; leaving this screen (Back/ESC) never does, matching
 * every other sub-screen in this package.
 * <p>
 * The blank-Custom-Model / invalid-Custom-Base-URL blocking that used to live in {@code
 * TranslatorConfigScreen.handleDone()} (mailbox review #002 point E2, round 016 point M3) lives
 * HERE now, in {@link #onClose()} -- scoped to {@link Config.EndPoint#CUSTOM} specifically, since
 * those free-typed fields only exist for it. {@code TranslationRouter}'s own per-candidate request-
 * build try/catch is still the actual crash-safety net regardless (classifies a malformed request
 * as BAD_REQUEST, job-scoped only, never a crash) -- this is the first, clearest line of defense,
 * not the only one, same framing the original blocking code already had.
 * <p>
 * NOT covered by any headless test -- see {@link TranslatorConfigScreen}'s javadoc for why.
 */
public class ProviderDetailScreen extends OptionsSubScreen {

    private static final String P = MicrodaerysTranslatorClient.MODID + ".config.";
    private static final int SECTION_WIDTH = 310;
    private static final Integer CUSTOM_INT = Integer.MIN_VALUE;
    private static final List<Integer> RPM_PRESETS = List.of(5, 10, 15, 30, 60, 120);

    private final PendingTranslatorConfig pending;
    private final Config.EndPoint endpoint;
    private final Screen parent;

    private boolean apiKeyRevealed = false;
    private boolean blockedOnBlankCustomModel = false;
    private boolean blockedOnInvalidBaseUrl = false;

    private Button testConnectionButtonRef;
    private StringWidget testStatusWidgetRef;
    private boolean testInFlight = false;

    private StringWidget liveStatusWidgetRef;

    public ProviderDetailScreen(Screen parent, PendingTranslatorConfig pending, Config.EndPoint endpoint) {
        super(parent, Minecraft.getInstance().options, Component.translatable(ProviderInfo.of(endpoint).displayNameKey()));
        this.parent = parent;
        this.pending = pending;
        this.endpoint = endpoint;
    }

    private void refreshOptions() {
        setFocused(null);
        list.children().clear();
        addOptions();
    }

    @Override
    protected void addOptions() {
        ProviderInfo provider = ProviderInfo.of(endpoint);

        CycleButton<Boolean> enabledButton = CycleButton.onOffBuilder(pending.isProviderEnabled(endpoint))
                .displayOnlyValue()
                .create(Component.translatable(P + "provider.enabled"),
                        (btn, val) -> pending.setProviderEnabled(endpoint, val));
        list.addSmall(new StringWidget(Component.translatable(P + "provider.enabled"), font).alignLeft(), enabledButton);

        if (endpoint == Config.EndPoint.CUSTOM) {
            addCustomProviderFields();
        } else {
            List<String> modelChoices = new ArrayList<>();
            for (ProviderInfo.ModelPreset preset : provider.models()) modelChoices.add(preset.modelId());
            modelChoices.add(PendingTranslatorConfig.CUSTOM_MODEL);
            CycleButton<String> modelButton = CycleButton.<String>builder(id -> TranslatorConfigScreen.modelDisplayName(provider, id))
                    .withValues(modelChoices)
                    .withInitialValue(pending.getProviderModelSelection(endpoint))
                    .displayOnlyValue()
                    .create(Component.translatable(P + "model"), (btn, val) -> {
                        pending.setProviderModelSelection(endpoint, val);
                        refreshOptions();
                    });
            list.addSmall(new StringWidget(Component.translatable(P + "model"), font).alignLeft(), modelButton);

            if (pending.getProviderModelSelection(endpoint).equals(PendingTranslatorConfig.CUSTOM_MODEL)) {
                EditBox customModelBox = new EditBox(font, 150, 20, Component.translatable(P + "model.custom_id"));
                customModelBox.setMaxLength(256);
                customModelBox.setValue(pending.getProviderCustomModel(endpoint));
                customModelBox.setResponder(v -> pending.setProviderCustomModel(endpoint, v));
                StringWidget customModelLabel = new StringWidget(Component.translatable(P + "model.custom_id"), font).alignLeft();
                if (blockedOnBlankCustomModel && pending.getProviderCustomModel(endpoint).isBlank()) {
                    customModelLabel.setColor(0xFFFF5555);
                    customModelBox.setTextColor(0xFFFF5555);
                }
                list.addSmall(customModelLabel, customModelBox);
            }

            if (provider.requiresApiKey()) {
                addApiKeyFields();
            } else {
                list.addSmall(new StringWidget(Component.translatable(P + "api_key.ollama_note")
                        .withStyle(ChatFormatting.GRAY), font).alignLeft(), null);
            }
        }

        // Moved here from the old Simple screen (mailbox review round 031's open question, GUI
        // batch): the model selection this note is about now lives on THIS screen, not Simple, so
        // the note follows it here rather than being left behind as an orphaned line with no nearby
        // model control to explain.
        list.addSmall(new StringWidget(Component.translatable(P + "model_cache_note")
                .withStyle(ChatFormatting.GRAY), font).alignLeft(), null);

        addPriorityRow();
        addRpmRow();

        liveStatusWidgetRef = new StringWidget(SECTION_WIDTH, Button.DEFAULT_HEIGHT, computeStatusText(), font).alignLeft();
        list.addSmall(new StringWidget(Component.translatable(P + "provider.status"), font).alignLeft(), null);
        list.addSmall(liveStatusWidgetRef, null);

        testConnectionButtonRef = Button.builder(Component.translatable(P + "test_connection"), b -> onTestConnectionPressed()).build();
        testConnectionButtonRef.active = !testInFlight;
        testStatusWidgetRef = new StringWidget(150, 20, Component.empty(), font);
        list.addSmall(testConnectionButtonRef, testStatusWidgetRef);
        list.addSmall(new StringWidget(Component.translatable(P + "test_connection.note")
                .withStyle(ChatFormatting.DARK_GRAY), font).alignLeft(), null);
    }

    /** Live-updates the status line's cooldown countdown every tick, without needing a custom
     *  widget class -- {@link #liveStatusWidgetRef} is a plain {@code StringWidget}, this just
     *  refreshes its message. Cheap: {@link ProviderRuntimeState#status} is a handful of field
     *  reads, no I/O. */
    @Override
    public void tick() {
        super.tick();
        if (liveStatusWidgetRef != null) liveStatusWidgetRef.setMessage(computeStatusText());
    }

    private Component computeStatusText() {
        if (!pending.isProviderEnabled(endpoint)) {
            return Component.translatable(P + "provider.status.disabled").withStyle(ChatFormatting.GRAY);
        }
        ProviderRuntimeState state = ProviderRuntimeRegistry.forEndpoint(endpoint);
        long now = System.currentTimeMillis();
        long remainingSeconds = (state.cooldownRemainingMillis(now) + 999) / 1000;
        Component headline = switch (state.status(now)) {
            case AVAILABLE -> Component.translatable(P + "provider.status.ready").withStyle(ChatFormatting.GREEN);
            case RATE_LIMITED -> Component.translatable(P + "provider.status.rate_limited", remainingSeconds)
                    .withStyle(ChatFormatting.YELLOW);
            case COOLDOWN -> Component.translatable(P + "provider.status.cooldown", remainingSeconds)
                    .withStyle(ChatFormatting.YELLOW);
            case CONNECTION_ERROR -> Component.translatable(P + "provider.status.cannot_connect").withStyle(ChatFormatting.RED);
            case AUTH_ERROR -> Component.translatable(P + "provider.status.invalid_key").withStyle(ChatFormatting.RED);
        };
        return headline.copy().append(Component.literal(" ")).append(latencyDetailText(state, now));
    }

    /** Makes {@link net.github.dctime.libs.routing.ProviderScorer}'s two AUTOMATIC-mode-only inputs
     *  (whether a real latency sample exists, and how long since this provider was last attempted)
     *  actually OBSERVABLE in the GUI (mailbox review round 037, point Q2): before this, "why did
     *  AUTOMATIC just pick NVIDIA" depended on two variables nobody could see anywhere, so answering
     *  that question meant reading source code, not looking at the screen -- exactly what a
     *  debuggable-formula promise is supposed to make unnecessary. Deliberately appended to the
     *  SAME status line (not a separate row) -- this is diagnostic detail, secondary to the
     *  headline status. */
    private Component latencyDetailText(ProviderRuntimeState state, long nowMillis) {
        if (!state.hasLatencySample()) {
            return Component.translatable(P + "provider.status.detail.untried").withStyle(ChatFormatting.DARK_GRAY);
        }
        // Locale.ROOT, not the default locale: String.format's "%.1f" uses a comma for the decimal
        // separator under some JVM default locales (e.g. German), which would silently corrupt a
        // number embedded in a translated sentence -- this mod already sanitizes numbers/URLs
        // against exactly this class of locale-dependent surprise elsewhere (see BaseUrlUtil/
        // ApiKeyUtil), so the same care applies here.
        String avgSeconds = String.format(java.util.Locale.ROOT, "%.1f", state.averageLatencyMs() / 1000.0);
        long sinceAttemptSeconds = Math.max(0L, (nowMillis - state.lastAttemptMillis()) / 1000L);
        return Component.translatable(P + "provider.status.detail", avgSeconds, sinceAttemptSeconds)
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Same masked/paste/reveal API-key block {@code TranslatorConfigScreen} used to have, bound to
     *  THIS screen's specific {@link #endpoint} via {@link PendingTranslatorConfig}'s explicit-
     *  endpoint accessors instead of the old "active provider" live view. */
    private void addApiKeyFields() {
        EditBox apiKeyBox = new EditBox(font, 150, 20, Component.translatable(P + "api_key"));
        apiKeyBox.setMaxLength(512);
        apiKeyBox.setValue(pending.getProviderApiKey(endpoint));
        // Sanitize on EVERY change -- see PendingTranslatorConfig.sanitizeApiKey()'s javadoc for
        // why (a trailing newline crashes Test Connection's click handler synchronously).
        apiKeyBox.setResponder(v -> pending.setProviderApiKey(endpoint, PendingTranslatorConfig.sanitizeApiKey(v)));
        if (!apiKeyRevealed) {
            apiKeyBox.setFormatter((text, index) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        }
        list.addSmall(new StringWidget(Component.translatable(P + "api_key"), font).alignLeft(), apiKeyBox);

        Button showHideButton = Button.builder(
                Component.translatable(apiKeyRevealed ? P + "api_key.hide" : P + "api_key.show"),
                b -> {
                    apiKeyRevealed = !apiKeyRevealed;
                    refreshOptions();
                }).build();
        Button pasteButton = Button.builder(Component.translatable(P + "api_key.paste"), b -> {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null) pending.setProviderApiKey(endpoint, PendingTranslatorConfig.sanitizeApiKey(clipboard));
            refreshOptions();
        }).build();
        list.addSmall(showHideButton, pasteButton);
    }

    /** Same fields {@code TranslatorConfigScreen} used to show inline for Custom Provider -- see
     *  that screen's former {@code addCustomProviderFields()} (removed there, moved here). */
    private void addCustomProviderFields() {
        EditBox nameBox = new EditBox(font, 150, 20, Component.translatable(P + "custom_provider.name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(pending.getCustomProviderName(endpoint));
        nameBox.setResponder(v -> pending.setCustomProviderName(endpoint, v));
        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.name"), font).alignLeft(), nameBox);

        EditBox baseUrlBox = new EditBox(font, 150, 20, Component.translatable(P + "custom_provider.base_url"));
        baseUrlBox.setMaxLength(512);
        baseUrlBox.setValue(pending.getCustomProviderBaseUrl(endpoint));
        baseUrlBox.setResponder(v -> pending.setCustomProviderBaseUrl(endpoint, v));
        StringWidget baseUrlLabel = new StringWidget(Component.translatable(P + "custom_provider.base_url"), font).alignLeft();
        if (blockedOnInvalidBaseUrl && !BaseUrlUtil.isValid(pending.getCustomProviderBaseUrl(endpoint))) {
            baseUrlLabel.setColor(0xFFFF5555);
            baseUrlBox.setTextColor(0xFFFF5555);
        }
        list.addSmall(baseUrlLabel, baseUrlBox);

        CycleButton<AuthMode> authButton = CycleButton.<AuthMode>builder(mode -> Component.translatable(
                        mode == AuthMode.NONE ? P + "custom_provider.authentication.none" : P + "custom_provider.authentication.bearer"))
                .withValues(AuthMode.BEARER, AuthMode.NONE)
                .withInitialValue(pending.getCustomProviderAuthMode(endpoint))
                .displayOnlyValue()
                .create(Component.translatable(P + "custom_provider.authentication"),
                        (btn, val) -> pending.setCustomProviderAuthMode(endpoint, val));
        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.authentication"), font).alignLeft(), authButton);

        addApiKeyFields();

        EditBox modelBox = new EditBox(font, 150, 20, Component.translatable(P + "model.custom_id"));
        modelBox.setMaxLength(256);
        modelBox.setValue(pending.getProviderCustomModel(endpoint));
        modelBox.setResponder(v -> pending.setProviderCustomModel(endpoint, v));
        StringWidget modelLabel = new StringWidget(Component.translatable(P + "model.custom_id"), font).alignLeft();
        if (blockedOnBlankCustomModel && pending.getProviderCustomModel(endpoint).isBlank()) {
            modelLabel.setColor(0xFFFF5555);
            modelBox.setTextColor(0xFFFF5555);
        }
        list.addSmall(modelLabel, modelBox);

        CycleButton<Boolean> visionButton = CycleButton.onOffBuilder(pending.getCustomProviderSupportsVision(endpoint))
                .displayOnlyValue()
                .create(Component.translatable(P + "custom_provider.supports_images"),
                        (btn, val) -> pending.setCustomProviderSupportsVision(endpoint, val));
        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.supports_images"), font).alignLeft(), visionButton);

        list.addSmall(new StringWidget(Component.translatable(P + "custom_provider.privacy_note")
                .withStyle(ChatFormatting.GRAY), font).alignLeft(), null);
    }

    private void addPriorityRow() {
        List<Integer> choices = new ArrayList<>();
        for (int i = 1; i <= 11; i++) choices.add(i);
        CycleButton<Integer> priorityButton = CycleButton.<Integer>builder(v -> Component.literal(String.valueOf(v)))
                .withValues(choices)
                .withInitialValue(pending.getProviderPriority(endpoint))
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(P + "provider.priority.tooltip")))
                .create(Component.translatable(P + "provider.priority"),
                        (btn, val) -> pending.setProviderPriority(endpoint, val));
        list.addSmall(new StringWidget(Component.translatable(P + "provider.priority"), font).alignLeft(), priorityButton);
    }

    /** Same preset-CycleButton + Custom EditBox pattern as {@code
     *  TranslatorAdvancedConfigScreen#addRpmRow()} (that one edits the GLOBAL safety cap; this one
     *  edits THIS provider's own budget) -- deliberately not extracted into a shared helper this
     *  round (two call sites, different backing fields; not worth a shared abstraction yet). */
    private void addRpmRow() {
        List<Integer> choices = new ArrayList<>(RPM_PRESETS);
        choices.add(CUSTOM_INT);
        int current = pending.getProviderMaxRequestsPerMinute(endpoint);
        int selection = RPM_PRESETS.contains(current) ? current : CUSTOM_INT;

        CycleButton<Integer> rpmButton = CycleButton.<Integer>builder(v -> v.equals(CUSTOM_INT)
                        ? Component.translatable(P + "rpm.custom", pending.getProviderMaxRequestsPerMinute(endpoint))
                        : Component.literal(String.valueOf(v)))
                .withValues(choices)
                .withInitialValue(selection)
                .displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.translatable(P + "provider.rpm.tooltip")))
                .create(Component.translatable(P + "provider.rpm"), (btn, val) -> {
                    if (!val.equals(CUSTOM_INT)) pending.setProviderMaxRequestsPerMinute(endpoint, val);
                    refreshOptions();
                });
        list.addSmall(new StringWidget(Component.translatable(P + "provider.rpm"), font).alignLeft(), rpmButton);

        if (selection == CUSTOM_INT) {
            EditBox rpmBox = new EditBox(font, 150, 20, Component.translatable(P + "provider.rpm"));
            rpmBox.setMaxLength(9);
            rpmBox.setValue(String.valueOf(current));
            rpmBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,9}"));
            rpmBox.setResponder(s -> {
                try {
                    int v = Integer.parseInt(s);
                    if (v >= 1) pending.setProviderMaxRequestsPerMinute(endpoint, v);
                } catch (NumberFormatException ignored) {
                    // partial/empty input while typing
                }
            });
            list.addSmall(new StringWidget(Component.empty(), font), rpmBox);
        }
    }

    private void onTestConnectionPressed() {
        testInFlight = true;
        testConnectionButtonRef.active = false;
        testStatusWidgetRef.setMessage(Component.translatable(P + "test_connection.testing"));
        TranslationConnectionTester.test(endpoint, pending.getProviderApiKey(endpoint), pending.resolvedModelFor(endpoint),
                pending.getCustomProviderBaseUrl(endpoint), pending.getCustomProviderAuthMode(endpoint), result ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().screen != this) return;
                    testInFlight = false;
                    testConnectionButtonRef.active = true;
                    testStatusWidgetRef.setMessage(describeResult(result));
                    // A successful test immediately restores this provider to the routing pool --
                    // "Runtime Health Recovery", spec's own explicit requirement -- without needing
                    // Done or a restart. Test Connection itself never touches ProviderRuntimeState's
                    // rate/concurrency tracking (it goes through TranslationConnectionTester, a
                    // completely separate HTTP client -- see that class's own javadoc), so this is
                    // the ONLY effect a test has on runtime state.
                    //
                    // Deliberate exception to this class's own "Cancel discards everything" contract
                    // (mailbox review round 032, point Z1): this uses the PENDING (not-yet-saved) API
                    // key -- if the player types a corrected key, gets OK here, then presses Cancel,
                    // the clear is NOT rolled back even though Config still has the old, wrong key.
                    // The router would then briefly treat this provider as healthy, get a real 401,
                    // and re-mark AUTH_ERROR on its own -- self-correcting at the cost of one wasted
                    // request (and, in AUTOMATIC, a short window where a known-broken provider ranks
                    // ahead of healthy ones) -- so this is accepted, not a bug to fix; recorded here
                    // so it reads as a documented trade-off, not a surprise for whoever finds it next.
                    if (result.status() == ConnectionTestStatus.Status.OK) {
                        ProviderRuntimeRegistry.forEndpoint(endpoint).clearAuthAndCooldown();
                    }
                }));
    }

    private Component describeResult(TranslationConnectionTester.Result result) {
        return switch (result.status()) {
            case OK -> {
                MutableComponent connected = Component.translatable(P + "test_connection.connected").withStyle(ChatFormatting.GREEN);
                if (result.modelFound()) yield connected;
                MutableComponent hint = Component.translatable(P + "test_connection.model_not_found", pending.resolvedModelFor(endpoint))
                        .withStyle(ChatFormatting.YELLOW);
                yield connected.append(Component.literal(" - ")).append(hint);
            }
            case INVALID_KEY -> Component.translatable(P + "test_connection.invalid_key").withStyle(ChatFormatting.RED);
            case RATE_LIMITED -> Component.translatable(P + "test_connection.rate_limited").withStyle(ChatFormatting.YELLOW);
            case CANNOT_CONNECT -> Component.translatable(P + "test_connection.cannot_connect").withStyle(ChatFormatting.RED);
            case HTTP_ERROR -> Component.translatable(P + "test_connection.http_error", result.httpStatusCode()).withStyle(ChatFormatting.RED);
            case INVALID_BASE_URL -> Component.translatable(P + "test_connection.invalid_base_url").withStyle(ChatFormatting.RED);
        };
    }

    /** Back/ESC: never touches {@link Config} (see class javadoc) -- but DOES block leaving with an
     *  obviously-broken enabled Custom Provider, same UX this validation always had before it moved
     *  here from {@code TranslatorConfigScreen}. */
    @Override
    public void onClose() {
        if (endpoint == Config.EndPoint.CUSTOM) {
            if (pending.resolvedModelFor(endpoint).isBlank()) {
                blockedOnBlankCustomModel = true;
                refreshOptions();
                return;
            }
            if (!BaseUrlUtil.isValid(pending.getCustomProviderBaseUrl(endpoint))) {
                blockedOnInvalidBaseUrl = true;
                refreshOptions();
                return;
            }
        }
        minecraft.setScreen(parent);
    }
}
