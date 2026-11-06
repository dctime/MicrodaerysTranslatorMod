package net.github.dctime.libs.http;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Java 8 stand-in for the subset of {@code java.net.http.HttpRequest} the provider adapters use
 * (1.16.5 runs on Java 8, where {@code java.net.http} doesn't exist). The builder API mirrors the
 * JDK 11 one on purpose -- {@code newBuilder().uri(..).timeout(..).header(..).POST(BodyPublishers
 * .ofString(..)).build()} -- so the adapters' request-building code stays line-for-line comparable
 * with the 1.17+ branches and a diff between them shows only the import.
 * <p>
 * Also keeps the JDK's two validation behaviors the adapters/router rely on: {@link Builder#uri}
 * rejects anything but an absolute http/https URI with a host, and {@link Builder#header} rejects
 * CR/LF in a name or value, both with {@link IllegalArgumentException} -- the router's "build the
 * request BEFORE committing a rate-limit slot" ordering (AB1(b)) and the connection tester's
 * INVALID_BASE_URL path both depend on a malformed Custom Provider URL throwing HERE, at build
 * time, rather than later inside the send.
 * <p>
 * No Minecraft/Forge import, so tools/verify-provider-adapters can build and introspect requests
 * headless, exactly as it did with the JDK class.
 */
public final class HttpRequest {

    private final URI uri;
    private final String method;
    private final HttpHeaders headers;
    private final Duration timeout;
    private final BodyPublisher bodyPublisher;

    private HttpRequest(Builder b) {
        this.uri = b.uri;
        this.method = b.method;
        this.headers = HttpHeaders.ofSingle(b.headers);
        this.timeout = b.timeout;
        this.bodyPublisher = b.bodyPublisher;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public URI uri() {
        return uri;
    }

    public String method() {
        return method;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    public Optional<BodyPublisher> bodyPublisher() {
        return Optional.ofNullable(bodyPublisher);
    }

    /** A request body. Only the String form exists -- every provider sends JSON text. */
    public static final class BodyPublisher {
        private final String content;

        private BodyPublisher(String content) {
            this.content = content;
        }

        /** The exact text that will be written (as UTF-8) to the request body. */
        public String content() {
            return content;
        }
    }

    public static final class BodyPublishers {
        private BodyPublishers() {
        }

        public static BodyPublisher ofString(String s) {
            if (s == null) throw new NullPointerException("body");
            return new BodyPublisher(s);
        }
    }

    public static final class Builder {
        private URI uri;
        private String method = "GET";
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Duration timeout;
        private BodyPublisher bodyPublisher;

        private Builder() {
        }

        public Builder uri(URI uri) {
            if (uri == null) throw new NullPointerException("uri");
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("invalid URI scheme " + scheme);
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("unsupported URI " + uri);
            }
            this.uri = uri;
            return this;
        }

        public Builder timeout(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Invalid duration: " + duration);
            }
            this.timeout = duration;
            return this;
        }

        public Builder header(String name, String value) {
            if (name == null || value == null) throw new NullPointerException("header");
            if (name.isEmpty() || containsLineBreak(name) || containsLineBreak(value)) {
                throw new IllegalArgumentException("invalid header " + name);
            }
            headers.put(name, value);
            return this;
        }

        public Builder GET() {
            this.method = "GET";
            this.bodyPublisher = null;
            return this;
        }

        public Builder POST(BodyPublisher body) {
            if (body == null) throw new NullPointerException("body");
            this.method = "POST";
            this.bodyPublisher = body;
            return this;
        }

        public HttpRequest build() {
            if (uri == null) throw new IllegalStateException("uri not set");
            return new HttpRequest(this);
        }

        private static boolean containsLineBreak(String s) {
            return s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
        }
    }
}
