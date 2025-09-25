package com.jhjdekker98.fisheyegallery.config;

import android.content.SharedPreferences;

public interface ISettingsController {
    void init();

    void loadFromPrefs(SharedPreferences prefs);

    void saveToPrefs(SharedPreferences.Editor editor);
}
