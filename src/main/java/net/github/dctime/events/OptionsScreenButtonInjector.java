package net.github.dctime.events;

import net.github.dctime.MicrodaerysTranslatorClient;
import net.github.dctime.screen.TranslatorConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort shortcut button on the vanilla Options screen ("Microdaery's Translator...") that
 * opens the same {@link TranslatorConfigScreen} as Mods -> Microdaery's Translator -> Config.
 * That Mods-menu path is the one GUARANTEED entry point and is never touched by this class --
 * this is purely an optional convenience layered on top of it.
 * <p>
 * Deliberately event-based (fires on {@link ScreenEvent.Init.Post}), never a mixin into
 * {@code OptionsScreen}: multiple mods commonly inject into this same screen the same way
 * (Embeddium/ModernFix/ImmediatelyFast/FancyMenu-style mods among them), and the order those
 * listeners run in across mods is NOT guaranteed by NeoForge. A mixin redirecting or overwriting
 * OptionsScreen's own layout would risk fighting those mods outright; this only ever ADDS one
 * button, and only when there's demonstrably room for it.
 * <p>
 * "Room for it" is computed from the OTHER widgets' actual positions at the moment this runs (via
 * {@link #computeGap}), not hardcoded coordinates -- so it adapts to window size/GUI scale, and to
 * whatever any other mod already added. This is also what makes cross-mod ordering safe in BOTH
 * directions, not just "doesn't crash": if another mod's widget already occupies part of the gap
 * above Done, that widget's bottom edge is what {@code restBottom} picks up, so the computed gap
 * shrinks and this backs off -- it can never render on top of that widget. If another mod adds a
 * widget BELOW Done, {@code overallBottom} becomes that widget's bottom instead, the gap comes out
 * negative, and this backs off the same way. Neither case depends on which mod's
 * {@code ScreenEvent.Init.Post} listener happens to run first.
 * <p>
 * The gap is recomputed every frame (via {@link #onScreenRenderPre}), not just once at Init time,
 * because {@code Screen.initialized} is set once and never cleared (see {@code Screen.java}), and
 * {@code OptionsScreen} overrides {@code repositionElements()} to just call
 * {@code layout.arrangeElements()} -- so ANY time control returns to an {@code OptionsScreen}
 * instance that was already initialized once, {@code Init.Post} does NOT fire again, only
 * {@code repositionElements()} does. Two different paths lead there: resizing the window while
 * Options is the CURRENT screen, and navigating away (e.g. to this mod's own config screen) and
 * back via Cancel/Done, since {@code Minecraft.setScreen(lastScreen)} passes the very same
 * {@code OptionsScreen} instance back in. Both must keep working, which is why tracking is keyed
 * on the (screen, button) PAIR via {@link #trackedScreen}/{@link #trackedButtonRef}, not on the
 * button alone: {@link #onScreenInitPost} only overwrites that pair when it fires for an
 * {@code OptionsScreen} (a genuinely new instance replacing the old one) and leaves it completely
 * untouched for every other screen's {@code Init.Post} -- including this mod's own config screen,
 * which is exactly the transition that used to null the old single-field tracker out from under a
 * still-valid, still-displayed button. {@link #onScreenRenderPre} then re-arms automatically the
 * instant {@code event.getScreen()} is that same tracked instance again, however the player got
 * back to it. Both references are {@code WeakReference}s so this can never be the reason an
 * {@code OptionsScreen} (and its widgets) outlives its normal lifecycle -- see mailbox review
 * #002 point J2 -- particularly relevant on the one path that fires no {@code Init.Post} at all
 * (Options closed straight back into gameplay via ESC/{@code setScreen(null)}).
 * <p>
 * <b>The invariant this whole class is built around</b> (found the hard way, across three
 * separate bugs -- resizing while Options was open, returning to Options via Cancel/Done, and a
 * button that was never created because there was no room the one time {@code Init.Post} fired
 * for it): once a screen has fired {@code Init.Post}, NOTHING guarantees it will ever fire again
 * for that same instance, no matter what happens to the screen afterward (resized, revisited,
 * left and returned to). Anything that can become stale -- position, visibility, whether there's
 * room at all -- must therefore be decided fresh every frame in {@link #onScreenRenderPre}, never
 * cached from {@code Init.Post} time. {@link #tryAddButton} does NOT decide whether the button is
 * shown; it only ever creates, adds, and starts tracking it, then immediately defers to {@link
 * #reposition} for the real answer -- there is exactly one place in this file that decides
 * visibility, not one decision at Init time plus a second one for every frame after.
 * <p>
 * No exception from this class is allowed to propagate: worst case for any modpack combination or
 * any window size is "the shortcut button doesn't appear (or briefly disappears while resizing)",
 * never a crash, and Mods -> Config keeps working regardless.
 */
@EventBusSubscriber(modid = MicrodaerysTranslatorClient.MODID, value = Dist.CLIENT)
public class OptionsScreenButtonInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptionsScreenButtonInjector.class);
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int MIN_MARGIN = 4;

    // Weak on purpose (see class javadoc, mailbox review #002 point J2): a static field holding a
    // Button strongly would also transitively hold the OptionsScreen its onPress lambda captured,
    // for as long as this class is loaded -- i.e. forever, on whichever path never fires another
    // Init.Post to overwrite these.
    @Nullable
    private static WeakReference<Screen> trackedScreen;
    @Nullable
    private static WeakReference<Button> trackedButtonRef;

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        // Deliberately does NOT touch trackedScreen/trackedButtonRef for any screen other than an
        // OptionsScreen (see class javadoc, point J1) -- this used to unconditionally null the
        // tracker out on every single Init.Post, which broke tracking for a still-live button the
        // moment the player navigated to any other screen (e.g. this mod's own config screen) and
        // back, since the RETURN to the original OptionsScreen instance never re-fires Init.Post.
        if (!(event.getScreen() instanceof OptionsScreen)) return;
        try {
            tryAddButton(event);
        } catch (Exception e) {
            // See class javadoc: this feature must never be able to crash the game. Worst case is
            // simply that the button doesn't appear this time.
            trackedScreen = null;
            trackedButtonRef = null;
            LOGGER.warn("Could not add the Options-screen shortcut button (non-fatal, Mods -> Config still works): " + e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (trackedScreen == null || trackedButtonRef == null) return;
        Screen screen = trackedScreen.get();
        Button button = trackedButtonRef.get();
        if (screen == null || button == null) {
            trackedScreen = null;
            trackedButtonRef = null;
            return;
        }
        if (event.getScreen() != screen) return; // some other screen is showing right now -- nothing to do
        try {
            reposition(screen, button);
        } catch (Exception e) {
            // Stop retrying rather than just logging: this runs every frame, and a persistent
            // (non-transient) failure would otherwise re-throw and re-log on every single frame
            // the player sits on the Options screen -- easily tens of thousands of warn lines in
            // latest.log, which is exactly the file players paste in full when reporting a
            // problem. Since every frame's inputs are the same, a failure here won't un-fail on
            // its own. Hide/disable and drop tracking so this converges to the same worst case as
            // every other failure path: one log line, no button, no crash.
            button.visible = false;
            button.active = false;
            trackedScreen = null;
            trackedButtonRef = null;
            LOGGER.warn("Could not reposition the Options-screen shortcut button (non-fatal, giving up for this screen instance): " + e.getMessage());
        }
    }

    private static void tryAddButton(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();

        // Always create, add, and track the button -- "is there room" is no longer decided here.
        // It used to be: no room at Init time meant this method returned without ever calling
        // addListener, so nothing existed for reposition() to later bring back once the window
        // grew -- reposition() only runs for an ALREADY-tracked button (see onScreenRenderPre),
        // so a screen that opened too small could never regain the button short of the player
        // leaving and re-entering Options for a fresh Init.Post. That's the same class of bug as
        // I1/J1 (see class javadoc's invariant) wearing a third disguise: this method was
        // silently assuming ITS OWN Init.Post-time snapshot of "room or no room" would still be
        // true later, when the whole point of reposition() is that it isn't safe to assume that.
        // Bounds are a placeholder here -- reposition() below (and every frame after, from
        // onScreenRenderPre) is the only thing that ever decides real position/visibility.
        Button button = Button.builder(
                Component.translatable(MicrodaerysTranslatorClient.MODID + ".config.options_button"),
                b -> Minecraft.getInstance().setScreen(new TranslatorConfigScreen(screen))
        ).bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        // Starts hidden/inactive so that ANY failure path between here and the reposition() call
        // below -- including one inside reposition() itself, which the try/catch in
        // onScreenInitPost turns into "clear tracking and give up" -- leaves behind an invisible,
        // inert button rather than a clickable one sitting at (0,0) on top of the screen title.
        // That matches the class's own documented worst case ("the button doesn't appear"); a
        // visible-by-default button briefly would not.
        button.visible = false;
        button.active = false;

        event.addListener(button);
        trackedScreen = new WeakReference<>(screen);
        trackedButtonRef = new WeakReference<>(button);
        // Correct its real position/visibility immediately rather than leaving a placeholder-
        // positioned, wrongly-visible button active for the fraction of a frame before the first
        // onScreenRenderPre call would otherwise fix it.
        reposition(screen, button);
    }

    private static void reposition(Screen screen, Button button) {
        Gap gap = computeGap(screen.children(), button, screen.width);
        if (gap == null) {
            // No room anymore (window shrunk, or another mod's widget moved into the space) --
            // hide AND disable rather than leaving it clickable off in some stale corner.
            button.visible = false;
            button.active = false;
            return;
        }
        button.visible = true;
        button.active = true;
        button.setX(gap.x());
        button.setY(gap.y());
    }

    private record Gap(int x, int y) {}

    /**
     * Where the shortcut button belongs, or null if there isn't demonstrably room for it. See the
     * class javadoc for why excluding this mod's own button (when it's already in {@code
     * listeners}, i.e. every call from {@link #reposition}) and re-deriving the gap from
     * observed widget positions -- not cached state -- is what makes this safe both across mods
     * and across resizes.
     */
    @Nullable
    private static Gap computeGap(List<? extends GuiEventListener> listeners, @Nullable AbstractWidget exclude, int screenWidth) {
        List<int[]> bounds = new ArrayList<>(); // {top, bottom} for every existing AbstractWidget except `exclude`
        for (GuiEventListener listener : listeners) {
            if (listener == exclude) continue;
            if (listener instanceof AbstractWidget widget) {
                bounds.add(new int[]{widget.getY(), widget.getY() + widget.getHeight()});
            }
        }
        if (bounds.isEmpty()) return null;

        // The bottom-most widget on a vanilla OptionsScreen is the Done button, alone in the
        // footer -- everything else (FOV/Online row, the 2-column grid of category buttons) sits
        // above it. Compute the gap between "everything else" and Done from actual widget
        // positions, not assumed layout constants, so this stays correct across GUI scales and
        // whatever other mods already added by the time this runs.
        int overallBottom = bounds.stream().mapToInt(b -> b[1]).max().orElseThrow();
        int footerTop = bounds.stream().filter(b -> b[1] == overallBottom).mapToInt(b -> b[0]).min().orElseThrow();
        int restBottom = bounds.stream().filter(b -> b[1] != overallBottom).mapToInt(b -> b[1]).max().orElse(-1);
        if (restBottom < 0) return null; // only one widget on the whole screen -- not the shape we expect, bail out

        int gapSize = footerTop - restBottom;
        int neededGap = BUTTON_HEIGHT + MIN_MARGIN * 2;
        if (gapSize < neededGap) return null;

        int y = restBottom + (gapSize - BUTTON_HEIGHT) / 2;
        int x = screenWidth / 2 - BUTTON_WIDTH / 2;
        return new Gap(x, y);
    }
}
