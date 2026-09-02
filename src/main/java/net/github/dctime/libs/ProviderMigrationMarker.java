package net.github.dctime.libs;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records that the one-time legacy-provider-pool migration has already run, WITHOUT using a TOML
 * key. {@code ModConfigSpec.correct()} unconditionally deletes any key the running code no longer
 * defines (confirmed by reading neoforge-21.1.4-sources.jar) -- a downgrade would delete a TOML-based
 * "already migrated" flag right alongside the provider-pool settings it's supposed to guard, so a
 * later re-upgrade would silently re-run the migration and overwrite whatever the player had
 * deliberately configured in Manage Providers after the first migration (mailbox review round 024,
 * point S2). A plain file sidesteps that mechanism entirely: NeoForge's config system never touches
 * anything outside the TOML it owns.
 * <p>
 * The marker's CONTENT records what migration actually did, not just that it ran (mailbox review
 * round 030, point X1 -- the mirror image of S2): the marker file itself survives a downgrade, but
 * the TOML data it's supposed to guard does NOT (that data is exactly what {@code correct()} deletes
 * on a downgrade). A bare "migrated, yes/no" marker would then lie -- present, but describing a
 * state that no longer exists on disk -- causing a re-upgrade to skip migration entirely and leave
 * the player silently on {@code ProviderMode}'s static default (AUTOMATIC) instead of the SINGLE
 * mode migration originally set. Storing what was actually set lets the caller compare the marker's
 * claim against the CURRENT live config and detect that mismatch (see {@code
 * MicrodaerysTranslatorClient.looksWipedSinceMigration}).
 * <p>
 * No Minecraft/NeoForge dependency on purpose, matching {@link TranslationDiskCache} -- the caller
 * resolves the actual path (see {@code Translator.cacheFilePath()} for the sibling pattern this
 * mirrors: {@code FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("provider_pool_migrated.marker")}).
 */
public final class ProviderMigrationMarker {

    private ProviderMigrationMarker() {
    }

    /** Never throws: an unreadable path just means "not migrated yet". */
    public static boolean exists(Path file) {
        return Files.isRegularFile(file);
    }

    /** Null if the marker doesn't exist or can't be read -- callers should treat that identically
     *  to "not migrated yet", same as a false {@link #exists}. */
    @Nullable
    public static String read(Path file) {
        if (!exists(file)) return null;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            return null;
        }
    }

    public static void write(Path file, String content) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
