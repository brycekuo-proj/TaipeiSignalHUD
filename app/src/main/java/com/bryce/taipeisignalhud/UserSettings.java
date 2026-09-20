package com.bryce.taipeisignalhud;

import android.content.Context;
import android.content.SharedPreferences;

public final class UserSettings {
    public static final String PREFS = "user_settings";

    public static final String KEY_ENFORCEMENT_DISTANCE_M = "enforcement_distance_m";
    public static final String KEY_OVERLAY_OPACITY_PERCENT = "overlay_opacity_percent";
    public static final String KEY_SPEED_ALERT_KMH = "speed_alert_kmh";

    public static final int DEFAULT_ENFORCEMENT_DISTANCE_M = 300;
    public static final int DEFAULT_OVERLAY_OPACITY_PERCENT = 100;
    public static final int DEFAULT_SPEED_ALERT_KMH = 60;

    private UserSettings() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int enforcementDistanceM(Context context) {
        return clamp(
                prefs(context).getInt(
                        KEY_ENFORCEMENT_DISTANCE_M,
                        DEFAULT_ENFORCEMENT_DISTANCE_M),
                20,
                5000);
    }

    public static int overlayOpacityPercent(Context context) {
        return clamp(
                prefs(context).getInt(
                        KEY_OVERLAY_OPACITY_PERCENT,
                        DEFAULT_OVERLAY_OPACITY_PERCENT),
                0,
                100);
    }

    public static int speedAlertKmh(Context context) {
        return clamp(
                prefs(context).getInt(
                        KEY_SPEED_ALERT_KMH,
                        DEFAULT_SPEED_ALERT_KMH),
                10,
                300);
    }

    public static void setEnforcementDistanceM(Context context, int meters) {
        prefs(context).edit()
                .putInt(KEY_ENFORCEMENT_DISTANCE_M, clamp(meters, 20, 5000))
                .apply();
    }

    public static void setOverlayOpacityPercent(Context context, int percent) {
        prefs(context).edit()
                .putInt(KEY_OVERLAY_OPACITY_PERCENT, clamp(percent, 0, 100))
                .apply();
    }

    public static void setSpeedAlertKmh(Context context, int kmh) {
        prefs(context).edit()
                .putInt(KEY_SPEED_ALERT_KMH, clamp(kmh, 10, 300))
                .apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
