package net.github.dctime.libs.routing;

/**
 * How much a {@link TranslationJob} actually needs image support from whichever provider serves
 * it. Distinguishing REQUIRED from OPTIONAL is what lets a text-only provider still serve a
 * tooltip-icon translation (just without the image) while never being offered a screenshot job it
 * can only fail.
 */
public enum VisionRequirement {
    /** No image attached regardless of provider capability (most translations). */
    NONE,
    /** An image is attached when the CANDIDATE provider supports vision; a text-only candidate
     *  remains eligible and simply gets the image omitted for that attempt (e.g. tooltip item
     *  icon -- acceptance test 10, 12). */
    OPTIONAL,
    /** The image IS the payload -- a text-only candidate is excluded entirely by the router's hard
     *  filter, never even attempted (e.g. Screenshot Translation -- acceptance test 11). */
    REQUIRED
}
