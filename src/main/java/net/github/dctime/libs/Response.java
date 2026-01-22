package net.github.dctime.libs;

public class Response {
    int thisStatusCode;
    String thisMessage;

    public Response(int statusCode, String message) {
        thisStatusCode = statusCode;
        thisMessage = message;
    }

    public int statusCode() {
        return thisStatusCode;
    }

    public String body() {
        return thisMessage;
    }
}
