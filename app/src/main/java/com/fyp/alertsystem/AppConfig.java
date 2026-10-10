package com.fyp.alertsystem;

/** Values shared across screens. Change DATABASE_URL when pointing the app at your own Firebase project. */
public final class AppConfig {
    public static final String DATABASE_URL =
            "https://alertsystem-a08e4-default-rtdb.asia-southeast1.firebasedatabase.app";

    public static final String PREFS_NAME    = "login_prefs";
    public static final String KEY_LOGGED_IN = "isLoggedIn";
    public static final String KEY_ROLE      = "role";

    private AppConfig() {}

    /** Firebase keys may not contain . # $ [ ] or /, so replace them before using free text as a key. */
    public static String toSafeKey(String s) {
        return s.replaceAll("[.#$\\[\\]/]", "_");
    }
}
