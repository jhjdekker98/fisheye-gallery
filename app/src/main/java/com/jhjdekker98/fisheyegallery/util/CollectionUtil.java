package com.jhjdekker98.fisheyegallery.util;

import java.util.ArrayList;
import java.util.Arrays;

public class CollectionUtil {
    @SafeVarargs
    public static <T> ArrayList<T> listOf(T... elements) {
        return new ArrayList<>(Arrays.asList(elements));
    }
}
