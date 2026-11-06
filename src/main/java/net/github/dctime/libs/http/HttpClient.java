package net.github.dctime.libs.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java 8 replacement for {@code java.net.http.HttpClient#sendAsync}, built on
 * {@link HttpURLConnection} (JDK-only, so it adds nothing to the shipped jar and stays loadable by
 * the headless tools/verify-* programs).
 * <p>
 * Behavior kept from the JDK 11 client the 1.17+ branches use:
 * <ul>
 *   <li>{@link #sendAsync} never blocks the caller -- the blocking I/O runs on this class's own
 *       daemon worker pool and the returned future completes there, so every
 *       {@code whenComplete} callback in {@code TranslationRouter}/{@code
 *       TranslationConnectionTester} still runs OFF the render thread, exactly as before.</li>
 *   <li>Failures complete the future exceptionally with a {@link CompletionException} wrapping
 *       the real cause; a connect/read timeout surfaces as {@link HttpTimeoutException} so
 *       {@code FailureClassifier} still tells TIMEOUT from CONNECTION.</li>
 *   <li>Non-2xx statuses are NOT exceptions -- the response (with the error body and headers such
 *       as {@code Retry-After}) is returned normally, and the caller classifies the status code.</li>
 *   <li>The per-request timeout bounds the whole exchange (a watchdog disconnects a response
 *       that is still trickling in when it expires), and redirects are not followed (the JDK
 *       client's default {@code Redirect.NEVER}).</li>
 * </ul>
 * Worker threads are daemons, so an in-flight request never keeps the game process alive on exit.
 * Actual parallelism is bounded by the callers' own semaphores (router: global 8 + per-provider),
 * not by this pool.
 */
public final class HttpClient {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(daemonFactory("MicrodaerysTranslator-HTTP-"));
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(daemonFactory("MicrodaerysTranslator-HTTP-Watchdog-"));

    private final int connectTimeoutMillis;

    private HttpClient(Duration connectTimeout) {
        this.connectTimeoutMillis = (int) Math.min(Integer.MAX_VALUE, connectTimeout.toMillis());
    }

    public static HttpClient withConnectTimeout(Duration connectTimeout) {
        return new HttpClient(connectTimeout);
    }

    public CompletableFuture<HttpResponse> sendAsync(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(request);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, WORKERS);
    }

    /** Blocking send. Only {@link #sendAsync} calls this; public for completeness. */
    public HttpResponse send(HttpRequest request) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) request.uri().toURL().openConnection();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledFuture<?> watchdog = null;
        try {
            conn.setRequestMethod(request.method());
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            int requestTimeoutMillis = request.timeout()
                    .map(d -> (int) Math.min(Integer.MAX_VALUE, d.toMillis()))
                    .orElse(0);
            conn.setConnectTimeout(requestTimeoutMillis > 0
                    ? Math.min(connectTimeoutMillis, requestTimeoutMillis) : connectTimeoutMillis);
            conn.setReadTimeout(requestTimeoutMillis);
            for (Map.Entry<String, List<String>> header : request.headers().map().entrySet()) {
                for (String value : header.getValue()) {
                    conn.addRequestProperty(header.getKey(), value);
                }
            }

            if (requestTimeoutMillis > 0) {
                final HttpURLConnection c = conn;
                watchdog = WATCHDOG.schedule(() -> {
                    timedOut.set(true);
                    c.disconnect();
                }, requestTimeoutMillis, TimeUnit.MILLISECONDS);
            }

            if (request.bodyPublisher().isPresent()) {
                byte[] bytes = request.bodyPublisher().get().content().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(bytes);
                }
            }

            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readFully(in, charsetOf(conn.getContentType()));
            if (timedOut.get()) throw new HttpTimeoutException("request timed out");
            return new HttpResponse(status, HttpHeaders.ofMulti(conn.getHeaderFields()), body);
        } catch (SocketTimeoutException e) {
            throw new HttpTimeoutException(e.getMessage() == null ? "timed out" : e.getMessage());
        } catch (IOException e) {
            // The watchdog's disconnect() surfaces as an arbitrary IOException ("Socket closed",
            // "stream is closed", ...) -- report it as the timeout it actually was.
            if (timedOut.get()) throw new HttpTimeoutException("request timed out");
            throw e;
        } finally {
            if (watchdog != null) watchdog.cancel(false);
            conn.disconnect();
        }
    }

    private static String readFully(InputStream in, Charset charset) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return new String(buffer.toByteArray(), charset);
        }
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String p = part.trim();
                if (p.regionMatches(true, 0, "charset=", 0, 8)) {
                    try {
                        return Charset.forName(p.substring(8).trim().replace("\"", ""));
                    } catch (Exception ignored) {
                        // fall through to UTF-8
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix + THREAD_COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
