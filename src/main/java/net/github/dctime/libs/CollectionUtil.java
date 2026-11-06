package net.github.dctime.libs;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Java 8 equivalents of the Java 9/10 immutable collection factories ({@code List.of},
 * {@code Set.of}, {@code Map.of}/{@code ofEntries}/{@code entry}, {@code copyOf}) the 2.0.0 code
 * base uses. Same contract where it matters: the results are unmodifiable and reject null
 * elements/keys/values, and {@code setOf}/map factories reject duplicates with
 * {@link IllegalArgumentException} -- so a typo'd duplicate key in a curated table still fails
 * loudly at class-load time rather than silently overwriting. Iteration order is insertion order
 * (the JDK leaves it unspecified, so this is a strict refinement, never a behavior change anyone
 * could have depended on). No Minecraft/Forge import -- usable by the headless tools/verify-*.
 */
public final class CollectionUtil {

    private CollectionUtil() {
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        for (T e : elements) Objects.requireNonNull(e);
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(elements)));
    }

    public static <T> List<T> listCopyOf(Collection<? extends T> c) {
        for (T e : c) Objects.requireNonNull(e);
        return Collections.unmodifiableList(new ArrayList<>(c));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        Set<T> set = new LinkedHashSet<>();
        for (T e : elements) {
            if (!set.add(Objects.requireNonNull(e))) throw new IllegalArgumentException("duplicate element: " + e);
        }
        return Collections.unmodifiableSet(set);
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(Objects.requireNonNull(key), Objects.requireNonNull(value));
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (Map.Entry<? extends K, ? extends V> e : entries) {
            K k = Objects.requireNonNull(e.getKey());
            if (map.put(k, Objects.requireNonNull(e.getValue())) != null) {
                throw new IllegalArgumentException("duplicate key: " + k);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static <K, V> Map<K, V> mapOf() {
        return Collections.emptyMap();
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        return mapOfEntries(entry(k1, v1));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4), entry(k5, v5));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4), entry(k5, v5),
                entry(k6, v6));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4), entry(k5, v5),
                entry(k6, v6), entry(k7, v7));
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                         K k6, V v6, K k7, V v7, K k8, V v8) {
        return mapOfEntries(entry(k1, v1), entry(k2, v2), entry(k3, v3), entry(k4, v4), entry(k5, v5),
                entry(k6, v6), entry(k7, v7), entry(k8, v8));
    }

    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> map) {
        Map<K, V> copy = new LinkedHashMap<>();
        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            copy.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
