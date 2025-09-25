package com.jhjdekker98.fisheyegallery.util;


import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class CollectionUtil {
    @SafeVarargs
    public static <T> ArrayList<T> listOf(T... elements) {
        return new ArrayList<>(Arrays.asList(elements));
    }

    @SafeVarargs
    public static <T> HashSet<T> setOf(T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    @SafeVarargs
    public static <K, V> HashMap<K, V> mapOf(Map.Entry<K, V>... entries) {
        final HashMap<K, V> map = new HashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public static <K, V> Map.Entry<K, V> mapEntry(K key, V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }
}
