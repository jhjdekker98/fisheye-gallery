package com.jhjdekker98.fisheyegallery.util;

public class StringUtil {
    public static String[] splitOnLast(String subject, char character) {
        int i = subject.lastIndexOf(character);
        return new String[]{subject.substring(0, i), subject.substring(i + 1)};
    }
}
