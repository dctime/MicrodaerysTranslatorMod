package net.github.dctime;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.github.dctime.libs.OfficialTranslationLookup;
import net.github.dctime.libs.ProviderMigrationMarker;
import net.github.dctime.libs.Translator;
import net.github.dctime.libs.provider.ProviderAdapterRegistry;
import net.github.dctime.libs.provider.ProviderConfigResolver;
import net.github.dctime.libs.provider.ProviderInfo;
import net.github.dctime.libs.routing.ProviderMode;
import net.github.dctime.screen.TranslatorConfigScreen;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MicrodaerysTranslatorClient.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MicrodaerysTranslatorClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrodaerysTranslatorClient.class);
    public static final String MODID = "microdaerystranslator";
    public MicrodaerysTranslatorClient(ModContainer container) {
        // Custom config screen (TranslatorConfigScreen) instead of NeoForge's generic
        // ConfigurationScreen -- see mailbox #002: the auto-generated screen exposes raw config
        // keys and free-text fields (endpoint/model_name/target_language/...) that aren't usable
        // for a player who doesn't know what an API endpoint or a language code is.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new TranslatorConfigScreen(parent));
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) throws IOException, InterruptedException {
        Translator.loadCacheFromDisk();
        checkProviderRegistriesAreComplete();
        migrateProviderPoolIfNeeded();
    }

    private static Path providerMigrationMarkerPath() {
        return FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("provider_pool_migrated.marker");
    }

    // Marker content format -- deliberately trivial (no JSON/parsing library needed for two
    // tokens): "mode=SINGLE;endpoint=GOOGLE_AI_STUDIO" for an existing-player migration, or
    // "mode=<MODE_NAME>" otherwise. See migrateProviderPoolIfNeeded's javadoc.
    private static final String MARKER_MODE_SINGLE_PREFIX = "mode=SINGLE";

    /**
     * Keeps the marker in sync with every DELIBERATE player change, not just what migration itself
     * wrote -- called from {@link PendingTranslatorConfig#saveToConfig()} right after {@code
     * Config.save()} (mailbox review round 031, point Y1: without this, the marker stays frozen at
     * whatever migration originally wrote, e.g. "mode=SINGLE" -- the moment a player switches to
     * AUTOMATIC (the feature's own main mode) and saves, {@link #looksWipedSinceMigration} would
     * misread that deliberate choice as a downgrade-wipe on the NEXT launch, force it back to
     * SINGLE, and the player switching to AUTOMATIC again would just repeat the cycle forever --
     * a real, main-path infinite-revert loop, not a narrow edge case). After this call, marker vs.
     * live-config disagreement can only mean the TOML was reset by something OUTSIDE normal
     * gameplay (a downgrade), since every in-game change updates both together.
     * <p>
     * <b>Known remaining gap, not fixed by this method:</b> only the SINGLE case is fully
     * reconstructible after a wipe -- {@link #migrateProviderPoolIfNeeded} can deterministically
     * re-derive "SINGLE + this one credentialed provider enabled" from {@code
     * Config.ENDPOINT_CONFIG} alone. A player who deliberately chose PRIORITY/ROUND_ROBIN with a
     * specific hand-picked enabled/priority/rpm setup has no equivalent reconstruction path if a
     * downgrade wipes it -- this method only prevents {@link #looksWipedSinceMigration} from
     * mis-firing on their choice, it does not add the ability to restore it. That player would land
     * on the static AUTOMATIC default post-downgrade, same as a fresh install, with no migration
     * re-run to fix it (the marker no longer starts with "mode=SINGLE", so the existing detection
     * simply doesn't apply). Restoring THAT case would require the marker to record the full
     * provider pool, not just the mode -- out of scope for this fix.
     */
    public static void syncProviderMigrationMarker(ProviderMode mode, Config.EndPoint activeEndpoint) {
        String content = mode == ProviderMode.SINGLE
                ? MARKER_MODE_SINGLE_PREFIX + ";endpoint=" + activeEndpoint.name()
                : "mode=" + mode.name();
        try {
            ProviderMigrationMarker.write(providerMigrationMarkerPath(), content);
        } catch (IOException e) {
            LOGGER.warn("Failed to sync provider migration marker after config save: " + e.getMessage());
        }
    }

    /**
     * One-time migration for the Multi-Provider Router refactor (mailbox review rounds 023-028).
     * Gated by a plain FILE marker, not a TOML key -- {@code ModConfigSpec.correct()} unconditionally
     * deletes any key the running code no longer defines, so a TOML-based "already migrated" flag
     * would itself vanish on a downgrade, silently letting this migration re-run (and overwrite
     * whatever the player had since deliberately configured in Manage Providers) on the next
     * re-upgrade -- see {@link ProviderMigrationMarker}'s own javadoc (round 024/027, point S2).
     * <p>
     * Most-backward-compatible strategy, per the spec's own explicit permission to pick this: an
     * existing player (their {@code Config.ENDPOINT_CONFIG} provider already has real credentials,
     * resolved through {@link ProviderConfigResolver} exactly like every pre-Router translation
     * request already trusted) is forced onto {@code ProviderMode.SINGLE} with ONLY that one
     * provider enabled -- byte-for-byte the same behavior as before this refactor, until they
     * explicitly open Manage Providers and opt into something else. A genuinely blank slate (no
     * credentials anywhere) is left at its static defaults (AUTOMATIC, the 4-provider default-
     * enabled pool already defined in {@code Config}'s {@code defineProvider} calls) -- the
     * "new install" path.
     * <p>
     * Re-runs if the marker's content says migration set SINGLE mode but the LIVE config is
     * currently back at the static default (AUTOMATIC) -- see {@link #looksWipedSinceMigration}
     * (mailbox review round 030, point X1: the mirror image of S2. The marker file survives a
     * downgrade, but the TOML data it describes does NOT -- {@code correct()} deletes provider_mode/
     * enabled/priority right alongside everything else it doesn't recognize. A bare yes/no marker
     * would then lie: present, but describing state that's already gone, silently skipping
     * migration on the next re-upgrade and stranding the player on AUTOMATIC instead of their
     * original SINGLE).
     */
    private static void migrateProviderPoolIfNeeded() {
        Path markerPath = providerMigrationMarkerPath();
        String markerContent = ProviderMigrationMarker.read(markerPath);
        if (markerContent != null && !looksWipedSinceMigration(markerContent)) return;

        Config.EndPoint activeEndpoint = Config.ENDPOINT_CONFIG.get();
        boolean isExistingPlayer;
        if (activeEndpoint == Config.EndPoint.OLLAMA) {
            // Ollama needs no API key at all -- ENDPOINT_CONFIG's own default is
            // GOOGLE_AI_STUDIO (not Ollama), so the endpoint being Ollama at all already implies
            // a player deliberately chose it at some point; treat that as "existing player" the
            // same way a real saved API key would be treated for any other provider.
            isExistingPlayer = true;
        } else if (activeEndpoint == Config.EndPoint.CUSTOM) {
            isExistingPlayer = !Config.CUSTOM_PROVIDER_BASE_URL.get().isBlank();
        } else {
            isExistingPlayer = !ProviderConfigResolver.resolve(activeEndpoint).apiKey().isBlank();
        }

        String newMarkerContent;
        if (isExistingPlayer) {
            Config.PROVIDER_MODE.set(ProviderMode.SINGLE);
            for (Config.EndPoint endpoint : Config.EndPoint.values()) {
                boolean enabled = endpoint == activeEndpoint;
                if (endpoint == Config.EndPoint.CUSTOM) {
                    Config.CUSTOM_PROVIDER_ENABLED.set(enabled);
                } else {
                    Config.PROVIDER_KEYS.get(endpoint).enabled().set(enabled);
                }
            }
            newMarkerContent = MARKER_MODE_SINGLE_PREFIX + ";endpoint=" + activeEndpoint.name();
            LOGGER.info("Provider pool migration: existing config detected, migrated to SINGLE mode "
                    + "with active provider " + activeEndpoint + " (matches pre-Router behavior exactly).");
        } else {
            newMarkerContent = "mode=AUTOMATIC";
            LOGGER.info("Provider pool migration: no existing provider credentials found, leaving the "
                    + "provider pool at its fresh-install defaults (AUTOMATIC mode).");
        }

        Config.save();
        try {
            ProviderMigrationMarker.write(markerPath, newMarkerContent);
        } catch (IOException e) {
            // Not fatal, never a crash (this class's own established "log loudly, never crash" rule
            // -- see checkProviderRegistriesAreComplete's javadoc), but not perfectly safe either:
            // this migration will simply re-run on the next launch, same as the marker-missing-after-
            // a-downgrade scenario this whole file marker exists to prevent (round 024/027, point
            // S2) -- if the player changes their provider setup between now and their next launch,
            // that change would get silently overwritten by this migration running again. A disk
            // write failing for a small local config directory is expected to be rare; this is
            // logged loudly specifically so it's diagnosable if it ever does happen.
            LOGGER.warn("Failed to write provider migration marker -- migration will re-run on next launch: "
                    + e.getMessage());
        }
    }

    /** True when the marker claims migration originally forced SINGLE mode, but {@code
     *  Config.PROVIDER_MODE} is currently back at its static default (AUTOMATIC) -- the signature of
     *  a downgrade (which deletes provider_mode/enabled/priority, but not this out-of-TOML marker
     *  file) followed by a re-upgrade. Deliberately does NOT try to distinguish that from a player
     *  who manually switched back to AUTOMATIC themselves after migration -- the two are
     *  indistinguishable from state alone, and re-applying SINGLE in that narrow false-positive case
     *  is still the CONSERVATIVE direction (matches the migration's own stated goal), not a
     *  destructive one (mailbox review round 030, point X1). A marker claiming "mode=AUTOMATIC"
     *  (the fresh-install path) never triggers this -- re-running that branch would just re-derive
     *  the same already-current AUTOMATIC state either way, so there's nothing to detect there. */
    private static boolean looksWipedSinceMigration(String markerContent) {
        return markerContent.startsWith(MARKER_MODE_SINGLE_PREFIX) && Config.PROVIDER_MODE.get() == ProviderMode.AUTOMATIC;
    }

    /**
     * A provider has to appear in FOUR separate places to actually work: {@link Config.EndPoint}
     * itself, {@link Config#PROVIDER_KEYS} (or the separate CUSTOM_PROVIDER_* fields for Custom),
     * {@link ProviderInfo#ALL}, and {@link ProviderAdapterRegistry} (see mailbox review round 016,
     * point M1) -- each with a DIFFERENT failure mode if one is forgotten (a loud exception from
     * the adapter registry or {@code ProviderInfo.of}, a silent NPE from
     * {@code Config.PROVIDER_KEYS.get(...)} on the translation path). Checking once here, at
     * startup, turns "forgot to register a provider somewhere" into one clear log line at mod load
     * instead of a confusing crash the first time a player hovers an item with that provider
     * selected. Deliberately logs and continues rather than throwing: this environment's exact
     * behavior on an exception from an {@code FMLClientSetupEvent} handler (does the whole game
     * refuse to start, or does it degrade to a mod-loading-error screen) wasn't verified, and this
     * mod's own established rule is "worst case is a warning, never a crash" -- better to log
     * loudly and let the specific broken provider fail later than risk the entire game not
     * starting over what is, for every player except this mod's own developer, an impossible state.
     */
    private static void checkProviderRegistriesAreComplete() {
        for (Config.EndPoint endpoint : Config.EndPoint.values()) {
            try {
                ProviderAdapterRegistry.forEndpoint(endpoint);
            } catch (Exception e) {
                LOGGER.error("No TranslationProviderAdapter registered for " + endpoint + " -- this provider cannot translate.");
            }
            try {
                ProviderInfo.of(endpoint);
            } catch (Exception e) {
                LOGGER.error("No ProviderInfo registered for " + endpoint + " -- this provider cannot appear correctly in the config GUI.");
            }
            if (endpoint != Config.EndPoint.CUSTOM && !Config.PROVIDER_KEYS.containsKey(endpoint)) {
                LOGGER.error("No Config.PROVIDER_KEYS entry for " + endpoint + " -- this provider's settings cannot be saved or loaded.");
            }
        }
    }

    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Resource packs (a translation patch pack is a common one) can be added/removed/reordered
        // mid-session; without this, OfficialTranslationLookup would keep answering from whatever
        // snapshot it first loaded, silently ignoring anything the player adds afterward.
        event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        resourceManager -> OfficialTranslationLookup.invalidateCache());
    }


}
