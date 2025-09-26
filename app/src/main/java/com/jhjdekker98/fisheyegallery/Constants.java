package com.jhjdekker98.fisheyegallery;

import androidx.appcompat.app.AppCompatDelegate;
import com.jhjdekker98.fisheyegallery.util.CollectionUtil;
import java.util.Map;

public class Constants {

    // --- Intent Request IDs ---
    public static final int PERMISSION_REQUEST_ID = 1;
    public static final int CONFIG_REQUEST_ID = 2;
    public static final int STORAGE_AREA_REQUEST_ID = 3;

    // --- Shared Preferences ---
    public static final String SHARED_PREFS_NAME = "app_config";
    public static final String SHARED_PREFS_KEY_USE_MEDIASTORE = "use_mediastore";
    public static final String SHARED_PREFS_KEY_SAF_FOLDERS = "saf_folders";
    public static final String SHARED_PREFS_KEY_DEPTH = "max_depth";
    public static final String SHARED_PREFS_KEY_COLUMNS = "columns_per_row";
    public static final String SHARED_PREFS_KEY_THEME = "theme";
    public static final String SAF_SEPARATOR = ";";

    public static final String SECURE_SHARED_PREFS_KEY_SMB_CONNS = "smb_conns";


    public static final Map<Integer, String> THEME_LOOKUP = CollectionUtil.mapOf(
            CollectionUtil.mapEntry(AppCompatDelegate.MODE_NIGHT_NO, "Light"),
            CollectionUtil.mapEntry(AppCompatDelegate.MODE_NIGHT_YES, "Dark"),
            CollectionUtil.mapEntry(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, "Follow system"));
}
