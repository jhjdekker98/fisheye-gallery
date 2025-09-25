package com.jhjdekker98.fisheyegallery.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class CollectionUtil {
    @SafeVarargs
    public static <T> ArrayList<T> listOf(T... elements) {
        return new ArrayList<>(Arrays.asList(elements));
    }

    public static <T> HashSet<T> setOf(T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }
}
