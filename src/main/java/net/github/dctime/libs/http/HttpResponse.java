package net.github.dctime.libs.http;

/** Status code + headers + the whole body decoded as text (UTF-8 unless the server says otherwise). */
public final class HttpResponse {

    private final int statusCode;
    private final HttpHeaders headers;
    private final String body;

    HttpResponse(int statusCode, HttpHeaders headers, String body) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public String body() {
        return body;
    }
}
