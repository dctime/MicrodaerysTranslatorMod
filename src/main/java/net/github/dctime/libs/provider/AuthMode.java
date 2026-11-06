package net.github.dctime.libs.provider;

/** How a Custom Provider request authenticates -- first-version scope is intentionally narrow. */
public enum AuthMode {
    /** {@code Authorization: Bearer <api_key>} */
    BEARER,
    /** No {@code Authorization} header at all. */
    NONE
}
