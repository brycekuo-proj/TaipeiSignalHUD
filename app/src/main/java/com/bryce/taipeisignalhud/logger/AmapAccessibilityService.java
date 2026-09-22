package com.bryce.taipeisignalhud.logger;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

public final class AmapAccessibilityService extends AccessibilityService {
    private static final String AMAP_PACKAGE = "com.autonavi.minimap";
    private static final long MIN_CAPTURE_INTERVAL_MS = 450L;
    private static final long DUPLICATE_WINDOW_MS = 1800L;
    private static final int MAX_NODES = 320;
    private static final int MAX_TEXT_CHARS = 12000;

    private SignalDatabase db;
    private String activeSessionId = "";
    private long amapSeq = 0L;
    private long lastCaptureElapsedMs = 0L;
    private long lastHashElapsedMs = 0L;
    private String lastHash = "";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        db = new SignalDatabase(this);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.packageNames = new String[]{AMAP_PACKAGE};
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!AMAP_PACKAGE.contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(LoggerService.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(LoggerService.PREF_BACKGROUND_ACTIVE, false)) return;

        String sessionId = prefs.getString(LoggerService.PREF_ACTIVE_SESSION, "");
        if (sessionId == null || sessionId.isEmpty()) return;

        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastCaptureElapsedMs < MIN_CAPTURE_INTERVAL_MS) return;
        lastCaptureElapsedMs = nowElapsed;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) root = event.getSource();
        if (root == null) return;

        try {
            JSONObject capture = captureTree(root);
            String textBlob = capture.toString();
            String hash = Integer.toHexString(textBlob.hashCode());

            if (hash.equals(lastHash) && nowElapsed - lastHashElapsedMs < DUPLICATE_WINDOW_MS) {
                return;
            }
            lastHash = hash;
            lastHashElapsedMs = nowElapsed;

            if (!sessionId.equals(activeSessionId)) {
                activeSessionId = sessionId;
                amapSeq = db.maxSeq("amap_ui_sample", sessionId);
            }
            amapSeq++;

            long nowUtc = System.currentTimeMillis();
            long gpsUtc = prefs.getLong(LoggerService.PREF_LATEST_GPS_UTC_MS, 0L);
            long locationAgeMs = gpsUtc <= 0L ? -1L : Math.max(0L, nowUtc - gpsUtc);

            double lat = Double.longBitsToDouble(
                    prefs.getLong(LoggerService.PREF_LATEST_LAT_BITS,
                            Double.doubleToRawLongBits(Double.NaN)));
            double lon = Double.longBitsToDouble(
                    prefs.getLong(LoggerService.PREF_LATEST_LON_BITS,
                            Double.doubleToRawLongBits(Double.NaN)));
            float accuracy = prefs.getFloat(LoggerService.PREF_LATEST_ACCURACY_M, Float.NaN);
            float speed = prefs.getFloat(LoggerService.PREF_LATEST_SPEED_MPS, Float.NaN);
            float bearing = prefs.getFloat(LoggerService.PREF_LATEST_BEARING_DEG, Float.NaN);

            String windowClass = event.getClassName() == null ? "" : event.getClassName().toString();
            db.insertAmapUi(
                    sessionId,
                    amapSeq,
                    nowUtc,
                    SystemClock.elapsedRealtimeNanos(),
                    AccessibilityEvent.eventTypeToString(event.getEventType()),
                    AMAP_PACKAGE,
                    windowClass,
                    textBlob,
                    hash,
                    lat,
                    lon,
                    accuracy,
                    speed,
                    bearing,
                    locationAgeMs);
        } catch (Exception ignored) {
        }
    }

    private JSONObject captureTree(AccessibilityNodeInfo root) throws Exception {
        JSONArray nodes = new JSONArray();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        int chars = 0;

        while (!queue.isEmpty() && count < MAX_NODES && chars < MAX_TEXT_CHARS) {
            AccessibilityNodeInfo node = queue.removeFirst();
            JSONObject item = new JSONObject();

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String viewId = node.getViewIdResourceName();

            if (text != null && text.length() > 0) {
                String value = text.toString();
                chars += value.length();
                item.put("text", value);
            }
            if (desc != null && desc.length() > 0) {
                String value = desc.toString();
                chars += value.length();
                item.put("content_description", value);
            }
            if (viewId != null && !viewId.isEmpty()) item.put("view_id", viewId);
            if (node.getClassName() != null) item.put("class", node.getClassName().toString());

            if (item.length() > 1
                    || item.has("text")
                    || item.has("content_description")
                    || item.has("view_id")) {
                nodes.put(item);
            }

            int childCount = node.getChildCount();
            for (int i = 0; i < childCount && queue.size() + count < MAX_NODES; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
            count++;
        }

        JSONObject out = new JSONObject();
        out.put("source", "AMAP_ACCESSIBILITY");
        out.put("package", AMAP_PACKAGE);
        out.put("node_count_scanned", count);
        out.put("nodes", nodes);
        return out;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (db != null) db.close();
        db = null;
        super.onDestroy();
    }
}
