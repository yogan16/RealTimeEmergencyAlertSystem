package com.fyp.alertsystem;

import android.content.Context;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

/** Values and helpers shared across screens. */
public final class AppConfig {
    public static final String PREFS_NAME = "login_prefs";
    public static final String KEY_ROLE   = "role";

    /** FCM topic every student device subscribes to. Must match functions/index.js. */
    public static final String ALERTS_TOPIC = "alerts";

    public static final String ROLE_ADMIN    = "admin";
    public static final String ROLE_LECTURER = "lecturer";
    public static final String ROLE_STUDENT  = "student";

    private AppConfig() {}

    /**
     * Database instance for this build. The URL comes from local.properties
     * (firebase.databaseUrl); when it is not set, the URL in google-services.json is used.
     */
    public static FirebaseDatabase database() {
        String url = BuildConfig.FIREBASE_DATABASE_URL;
        return url.isEmpty() ? FirebaseDatabase.getInstance() : FirebaseDatabase.getInstance(url);
    }

    /** Firebase keys may not contain . # $ [ ] or /, so replace them before using free text as a key. */
    public static String toSafeKey(String s) {
        return s.replaceAll("[.#$\\[\\]/]", "_");
    }

    /** Signs out, stops alert notifications on this device and returns to the login screen. */
    public static void logout(Context context) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(ALERTS_TOPIC);
        FirebaseAuth.getInstance().signOut();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_ROLE).apply();
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);
    }
}
