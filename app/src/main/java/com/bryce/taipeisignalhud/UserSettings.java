package com.bryce.taipeisignalhud;

import android.content.Context;
import android.content.SharedPreferences;

public final class UserSettings {
    public static final String PREFS = "user_settings";

    public static final String KEY_ENFORCEMENT_DISTANCE_M = "enforcement_distance_m";
    public static final String KEY_OVERLAY_OPACITY_PERCENT = "overlay_opacity_percent";
    // Legacy single speed threshold is retained only for one-time migration to the
    // new road-class-aware thresholds.
    public static final String KEY_SPEED_ALERT_KMH = "speed_alert_kmh";
    public static final String KEY_SPEED_ALERT_GENERAL_KMH = "speed_alert_general_kmh";
    public static final String KEY_SPEED_ALERT_EXPRESSWAY_KMH = "speed_alert_expressway_kmh";
    public static final String KEY_SPEED_ALERT_HIGHWAY_KMH = "speed_alert_highway_kmh";

    public static final int DEFAULT_ENFORCEMENT_DISTANCE_M = 300;
    public static final int DEFAULT_OVERLAY_OPACITY_PERCENT = 100;
    public static final int DEFAULT_SPEED_ALERT_GENERAL_KMH = 60;
    public static final int DEFAULT_SPEED_ALERT_EXPRESSWAY_KMH = 80;
    public static final int DEFAULT_SPEED_ALERT_HIGHWAY_KMH = 100;

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

    public static int generalSpeedAlertKmh(Context context) {
        SharedPreferences p = prefs(context);
        int fallback = p.contains(KEY_SPEED_ALERT_KMH)
                ? p.getInt(KEY_SPEED_ALERT_KMH, DEFAULT_SPEED_ALERT_GENERAL_KMH)
                : DEFAULT_SPEED_ALERT_GENERAL_KMH;
        return clamp(p.getInt(KEY_SPEED_ALERT_GENERAL_KMH, fallback), 10, 300);
    }

    public static int expresswaySpeedAlertKmh(Context context) {
        return clamp(
                prefs(context).getInt(
                        KEY_SPEED_ALERT_EXPRESSWAY_KMH,
                        DEFAULT_SPEED_ALERT_EXPRESSWAY_KMH),
                10,
                300);
    }

    public static int highwaySpeedAlertKmh(Context context) {
        return clamp(
                prefs(context).getInt(
                        KEY_SPEED_ALERT_HIGHWAY_KMH,
                        DEFAULT_SPEED_ALERT_HIGHWAY_KMH),
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

    public static void setGeneralSpeedAlertKmh(Context context, int kmh) {
        int value = clamp(kmh, 10, 300);
        prefs(context).edit()
                .putInt(KEY_SPEED_ALERT_GENERAL_KMH, value)
                .putInt(KEY_SPEED_ALERT_KMH, value)
                .apply();
    }

    public static void setExpresswaySpeedAlertKmh(Context context, int kmh) {
        prefs(context).edit()
                .putInt(KEY_SPEED_ALERT_EXPRESSWAY_KMH, clamp(kmh, 10, 300))
                .apply();
    }

    public static void setHighwaySpeedAlertKmh(Context context, int kmh) {
        prefs(context).edit()
                .putInt(KEY_SPEED_ALERT_HIGHWAY_KMH, clamp(kmh, 10, 300))
                .apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
