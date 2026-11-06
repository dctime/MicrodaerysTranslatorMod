package net.github.dctime.libs.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Case-insensitive, read-only header view -- the {@code firstValue(name)} subset of
 * {@code java.net.http.HttpHeaders} that the router (Retry-After) and the verify tools use.
 */
public final class HttpHeaders {

    private final Map<String, List<String>> map;

    private HttpHeaders(Map<String, List<String>> caseInsensitiveMap) {
        this.map = Collections.unmodifiableMap(caseInsensitiveMap);
    }

    static HttpHeaders ofSingle(Map<String, String> single) {
        Map<String, List<String>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> e : single.entrySet()) {
            m.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
        }
        return new HttpHeaders(m);
    }

    static HttpHeaders ofMulti(Map<String, List<String>> multi) {
        Map<String, List<String>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> e : multi.entrySet()) {
            // HttpURLConnection reports the status line under a null key -- not a header.
            if (e.getKey() == null || e.getValue() == null) continue;
            m.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
        }
        return new HttpHeaders(m);
    }

    public Optional<String> firstValue(String name) {
        List<String> values = map.get(name);
        if (values == null || values.isEmpty()) return Optional.empty();
        return Optional.ofNullable(values.get(0));
    }

    public List<String> allValues(String name) {
        List<String> values = map.get(name);
        return values == null ? Collections.<String>emptyList() : Collections.unmodifiableList(values);
    }

    public Map<String, List<String>> map() {
        return map;
    }
}
