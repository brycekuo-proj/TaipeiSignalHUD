package com.bryce.taipeisignalhud;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HudService extends Service implements LocationListener {
    public static final String ACTION_START = "com.bryce.taipeisignalhud.START";
    public static final String ACTION_STOP = "com.bryce.taipeisignalhud.STOP";
    public static final String ACTION_DEMO = "com.bryce.taipeisignalhud.DEMO";

    private static final String CHANNEL_ID = "road_test";
    private static final int NOTIFICATION_ID = 1401;

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private LinearLayout overlayRoot;
    private TextView enforcementBanner;
    private final TrafficLightView[] lights = new TrafficLightView[3];
    private final TextView[] names = new TextView[3];

    private LocationManager locationManager;
    private IntersectionStore intersectionStore;
    private SignalPlanStore signalPlanStore;
    private EnforcementStore enforcementStore;
    private SpecialRoadStore specialRoadStore;
    private SpecialRoadStore.Match specialRoadMatch;
    private HudMcpClient mcpClient;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String activeEnforcementId = null;
    private int activeEnforcementVoiceStage = 0;
    private String lastSpokenEnforcementId = null;
    private long lastSpokenEnforcementElapsedMs = 0L;
    private final RoadLocationFilter locationFilter = new RoadLocationFilter();
    private final ExecutorService mcpExecutor = Executors.newSingleThreadExecutor();
    private Location latestLocation;
    private List<IntersectionStore.Candidate> latestCandidates = Collections.emptyList();
    private Float stableTravelBearingDeg = null;
    private HudMcpClient.Snapshot latestMcpSnapshot;
    private long latestMcpReceivedElapsedMs = 0L;
    private long lastMcpRequestElapsedMs = 0L;
    private volatile boolean mcpRequestInFlight = false;
    private boolean demoMode = false;
    private boolean overlayDocked = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int[] demoSeconds = new int[]{22, 41, 3};
    private final TrafficLightView.State[] demoStates = new TrafficLightView.State[]{
            TrafficLightView.State.GREEN,
            TrafficLightView.State.RED,
            TrafficLightView.State.YELLOW
    };

    private final Runnable liveTicker = new Runnable() {
        @Override public void run() {
            if (!demoMode) {
                requestMcpSnapshotIfNeeded();
                renderLiveRows();
                handler.postDelayed(this, 250L);
            }
        }
    };

    private final Runnable demoTicker = new Runnable() {
        @Override public void run() {
            if (!demoMode) return;
            for (int i = 0; i < demoSeconds.length; i++) {
                demoSeconds[i]--;
                if (demoSeconds[i] <= 0) {
                    if (demoStates[i] == TrafficLightView.State.GREEN) {
                        demoStates[i] = TrafficLightView.State.YELLOW;
                        demoSeconds[i] = 3;
                    } else if (demoStates[i] == TrafficLightView.State.YELLOW) {
                        demoStates[i] = TrafficLightView.State.RED;
                        demoSeconds[i] = 24;
                    } else {
                        demoStates[i] = TrafficLightView.State.GREEN;
                        demoSeconds[i] = 18;
                    }
                }
            }
            renderDemoRows();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        intersectionStore = new IntersectionStore(this);
        signalPlanStore = new SignalPlanStore(this);
        enforcementStore = new EnforcementStore(this);
        specialRoadStore = new SpecialRoadStore(this);
        mcpClient = new HudMcpClient(this);
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            int languageResult = tts.setLanguage(Locale.TAIWAN);
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA
                    && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
        });
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!hasLocationPermission()
                || (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this))) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        showOverlayIfNeeded();

        demoMode = ACTION_DEMO.equals(action);
        handler.removeCallbacks(demoTicker);
        handler.removeCallbacks(liveTicker);

        if (demoMode) {
            hideEnforcementBanner();
            resetDemo();
            renderDemoRows();
            handler.postDelayed(demoTicker, 1000L);
        } else {
            renderWaitingRows();
            startLocationUpdates();
            handler.post(liveTicker);
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("TaipeiSignalHUD 道路測試")
                .setContentText("GPS、道路層級、號誌倒數與執法提示正在運作")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel c = new NotificationChannel(
                CHANNEL_ID, "Road test", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("TaipeiSignalHUD road-test foreground location service");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(c);
    }

    private void showOverlayIfNeeded() {
        if (overlayView != null) return;

        LinearLayout root = new LinearLayout(this);
        overlayRoot = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(12), dp(8));
        root.setBackground(buildOverlayBackground());

        TextView banner = new TextView(this);
        enforcementBanner = banner;
        banner.setTextColor(Color.WHITE);
        banner.setTextSize(14);
        banner.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        banner.setGravity(Gravity.CENTER);
        banner.setSingleLine(true);
        banner.setPadding(dp(8), dp(5), dp(8), dp(5));
        banner.setBackground(buildEnforcementBackground());
        banner.setVisibility(View.GONE);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.setMargins(0, 0, 0, dp(5));
        root.addView(banner, bannerParams);

        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TrafficLightView light = new TrafficLightView(this);
            lights[i] = light;
            LinearLayout.LayoutParams lpLight = new LinearLayout.LayoutParams(dp(48), dp(48));
            lpLight.setMargins(0, i == 0 ? 0 : dp(4), dp(9), 0);
            row.addView(light, lpLight);

            TextView name = new TextView(this);
            names[i] = name;
            name.setTextColor(Color.WHITE);
            name.setTextSize(16);
            name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setLineSpacing(0f, 0.96f);
            name.setMaxWidth(dp(230));
            name.setMinWidth(dp(120));
            row.addView(name, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            root.addView(row);
        }

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        // Use START gravity so x is an absolute left-edge coordinate. This makes
        // partial off-screen docking reliable across Android vendors.
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = dp(10);
        overlayParams.y = dp(110);

        attachDrag(root);
        windowManager.addView(root, overlayParams);
        overlayView = root;

        root.post(() -> {
            if (overlayParams == null || overlayView == null) return;
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int normalX = Math.max(0, screenWidth - root.getWidth() - dp(10));
            overlayParams.x = normalX;
            try {
                windowManager.updateViewLayout(overlayView, overlayParams);
            } catch (Exception ignored) {
            }
        });
    }

    private void attachDrag(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;

            @Override public boolean onTouch(View v, MotionEvent event) {
                if (overlayParams == null) return false;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = overlayParams.x;
                        startY = overlayParams.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int requestedX = startX
                                + Math.round(event.getRawX() - downX);
                        overlayParams.x = Math.max(
                                0, Math.min(screenWidth - dp(24), requestedX));
                        overlayParams.y = Math.max(
                                0, startY + Math.round(event.getRawY() - downY));
                        try {
                            windowManager.updateViewLayout(overlayView, overlayParams);
                        } catch (Exception ignored) {
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float dx = event.getRawX() - downX;
                        if (dx >= dp(24)) {
                            setOverlayCompact(true);
                        } else if (dx <= -dp(24)) {
                            setOverlayCompact(false);
                        } else {
                            snapOverlayToRightEdge();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void setOverlayCompact(boolean compact) {
        overlayDocked = compact;
        if (overlayRoot == null) return;

        for (TextView name : names) {
            if (name != null) {
                name.setVisibility(compact ? View.GONE : View.VISIBLE);
            }
        }

        if (compact) {
            overlayRoot.setPadding(0, 0, 0, 0);
            overlayRoot.setBackground(null);
        } else {
            overlayRoot.setPadding(dp(10), dp(8), dp(12), dp(8));
            overlayRoot.setBackground(buildOverlayBackground());
        }

        overlayRoot.requestLayout();
        overlayRoot.post(this::snapOverlayToRightEdge);
    }

    private void snapOverlayToRightEdge() {
        if (overlayParams == null || overlayView == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int margin = overlayDocked ? dp(2) : dp(10);
        overlayParams.x = Math.max(0, screenWidth - overlayView.getWidth() - margin);
        try {
            windowManager.updateViewLayout(overlayView, overlayParams);
        } catch (Exception ignored) {
        }
    }

    private GradientDrawable buildOverlayBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(205, 14, 17, 22));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return bg;
    }

    private GradientDrawable buildEnforcementBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 210, 92, 24));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.argb(110, 255, 255, 255));
        return bg;
    }

    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;
        try {
            // Road-test v0.0.2: GPS/GNSS only. NETWORK_PROVIDER caused large jumps
            // and parallel-road errors during driving.
            locationManager.removeUpdates(this);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L,
                    0f,
                    this);

            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) {
                Location filtered = locationFilter.add(last);
                if (filtered != null) updateMatchedLocation(filtered);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void updateMatchedLocation(Location location) {
        latestLocation = location;

        float speed = location.hasSpeed() ? location.getSpeed() : 0f;
        boolean movingWithReliableBearing = location.hasBearing() && speed >= 2.0f;
        Float previousStableBearingDeg = stableTravelBearingDeg;
        if (movingWithReliableBearing) {
            stableTravelBearingDeg = location.getBearing();

            // A real turn means the old MCP snapshot may describe the road we just left.
            // Drop it immediately instead of showing stale forward-road/signal rows for
            // another 1–3 seconds while the next network response is in flight.
            if (previousStableBearingDeg != null
                    && angleDeltaAbs(previousStableBearingDeg, stableTravelBearingDeg) >= 30f) {
                latestMcpSnapshot = null;
                latestMcpReceivedElapsedMs = 0L;
                lastMcpRequestElapsedMs = 0L;
            }
        }

        specialRoadMatch = specialRoadStore == null
                ? null : specialRoadStore.update(location, stableTravelBearingDeg);
        boolean suppressSurfaceSignals = shouldSuppressSurfaceSignals();
        if (suppressSurfaceSignals) {
            latestCandidates = Collections.emptyList();
            latestMcpSnapshot = null;
            latestMcpReceivedElapsedMs = 0L;
        } else if (movingWithReliableBearing) {
            // A recognized ramp is the handoff point from grade-separated mainline
            // to the first reachable surface signal. Limit it to the nearest signal.
            int candidateLimit = specialRoadStore != null && specialRoadStore.isOnRamp()
                    ? 1 : 3;
            latestCandidates = intersectionStore.findAhead(location, candidateLimit);
        } else if (stableTravelBearingDeg != null && latestCandidates.isEmpty()) {
            // If the service starts just as the vehicle is slowing/stopped, reuse the
            // last reliable travel direction to acquire candidates once.
            Location matchingLocation = new Location(location);
            matchingLocation.setBearing(stableTravelBearingDeg);
            matchingLocation.setSpeed(2.1f);
            int candidateLimit = specialRoadStore != null && specialRoadStore.isOnRamp()
                    ? 1 : 3;
            latestCandidates = intersectionStore.findAhead(matchingLocation, candidateLimit);
        }
        // When stopped with existing candidates, deliberately keep them frozen.
        // GPS course becomes noisy near 0 km/h, but the signal phase must keep counting.
        updateEnforcementWarning();
        renderLiveRows();
    }

    private void updateEnforcementWarning() {
        if (enforcementStore == null
                || enforcementBanner == null
                || latestLocation == null
                || stableTravelBearingDeg == null) {
            hideEnforcementBanner();
            return;
        }

        EnforcementStore.Match match =
                enforcementStore.findApproaching(latestLocation, stableTravelBearingDeg);

        // Suppress intersection-style enforcement that belongs to the surface
        // while we are locked to an elevated/expressway/highway/tunnel mainline.
        // Fixed and section-speed enforcement remain valid on those facilities.
        if (match != null
                && shouldSuppressSurfaceSignals()
                && match.point.type != EnforcementStore.Type.SPEED
                && match.point.type != EnforcementStore.Type.SECTION) {
            match = null;
        }

        if (match == null) {
            hideEnforcementBanner();
            activeEnforcementId = null;
            activeEnforcementVoiceStage = 0;
            return;
        }

        boolean isNewPoint = !match.point.id.equals(activeEnforcementId);
        if (isNewPoint) {
            activeEnforcementId = match.point.id;
            activeEnforcementVoiceStage = 0;
        }

        boolean wasHidden = enforcementBanner.getVisibility() != View.VISIBLE;
        enforcementBanner.setText(formatEnforcementBanner(match));
        enforcementBanner.setVisibility(View.VISIBLE);
        if (wasHidden && overlayRoot != null) {
            overlayRoot.requestLayout();
            overlayRoot.post(this::snapOverlayToRightEdge);
        }

        int voiceStage = match.distanceMeters <= 180f ? 2 : 1;
        if (voiceStage > activeEnforcementVoiceStage) {
            long now = SystemClock.elapsedRealtime();
            boolean recentSamePoint = match.point.id.equals(lastSpokenEnforcementId)
                    && now - lastSpokenEnforcementElapsedMs < 120_000L;
            if (voiceStage == 2 || !recentSamePoint) {
                speakEnforcement(match, voiceStage);
                lastSpokenEnforcementId = match.point.id;
                lastSpokenEnforcementElapsedMs = now;
            }
            activeEnforcementVoiceStage = voiceStage;
        }
    }

    private void hideEnforcementBanner() {
        if (enforcementBanner == null
                || enforcementBanner.getVisibility() == View.GONE) {
            return;
        }
        enforcementBanner.setVisibility(View.GONE);
        if (overlayRoot != null) {
            overlayRoot.requestLayout();
            overlayRoot.post(this::snapOverlayToRightEdge);
        }
    }

    private String formatEnforcementBanner(EnforcementStore.Match match) {
        int distance = Math.max(20, Math.round(match.distanceMeters / 10f) * 10);
        StringBuilder text = new StringBuilder();
        if (match.point.type == EnforcementStore.Type.SPEED) {
            text.append("測速 ");
        } else if (match.point.type == EnforcementStore.Type.SECTION) {
            text.append("區間測速 ");
        } else if (match.point.type == EnforcementStore.Type.RED_LIGHT) {
            text.append("闖紅燈 ");
        } else {
            text.append("科技執法 ");
        }
        text.append(distance).append("m");

        String speed = compactSpeedLimit(match.point.speedLimit);
        if (!speed.isEmpty()
                && (match.point.type == EnforcementStore.Type.SPEED
                || match.point.type == EnforcementStore.Type.SECTION)) {
            text.append("｜速限").append(speed);
        }
        return text.toString();
    }

    private void speakEnforcement(EnforcementStore.Match match, int stage) {
        if (!ttsReady || tts == null) return;

        String prefix;
        if (stage >= 2) {
            prefix = "即將通過";
        } else {
            int distance = Math.max(50, Math.round(match.distanceMeters / 50f) * 50);
            prefix = "前方" + distance + "公尺";
        }

        String type;
        if (match.point.type == EnforcementStore.Type.SPEED) {
            type = "測速照相";
        } else if (match.point.type == EnforcementStore.Type.SECTION) {
            type = "區間測速";
        } else if (match.point.type == EnforcementStore.Type.RED_LIGHT) {
            type = "闖紅燈照相";
        } else {
            type = "科技執法";
        }

        StringBuilder spoken = new StringBuilder(prefix).append(type);
        String speed = compactSpeedLimit(match.point.speedLimit);
        if (!speed.isEmpty()
                && (match.point.type == EnforcementStore.Type.SPEED
                || match.point.type == EnforcementStore.Type.SECTION)) {
            spoken.append("，速限").append(speed.replace("/", "或"));
        }
        tts.speak(
                spoken.toString(),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "enforcement-" + match.point.id + "-" + stage);
    }

    private String compactSpeedLimit(String raw) {
        if (raw == null || raw.trim().isEmpty() || "\\".equals(raw.trim())) return "";
        String normalized = raw.replaceAll("[0-9]+\\s*噸", "");
        String digits = normalized.replaceAll("[^0-9]+", " ").trim();
        if (digits.isEmpty()) return "";
        String[] pieces = digits.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) continue;
            boolean duplicate = false;
            String[] existing = out.toString().split("/");
            for (String value : existing) {
                if (piece.equals(value)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            if (out.length() > 0) out.append('/');
            out.append(piece);
            if (out.indexOf("/") >= 0) break;
        }
        return out.toString();
    }

    private void renderWaitingRows() {
        for (int i = 0; i < 3; i++) {
            lights[i].setSignal(TrafficLightView.State.UNKNOWN, "--");
            names[i].setText(i == 0 ? "等待 GPS / 行進方向" : "—");
        }
    }

    private void requestMcpSnapshotIfNeeded() {
        if (demoMode || mcpClient == null || !mcpClient.isConfigured()) return;
        if (latestLocation == null || stableTravelBearingDeg == null) return;
        if (shouldSuppressSurfaceSignals()) return;
        long nowElapsed = SystemClock.elapsedRealtime();
        if (mcpRequestInFlight || nowElapsed - lastMcpRequestElapsedMs < 450L) return;

        final Location requestLocation = new Location(latestLocation);
        final float requestBearing = stableTravelBearingDeg;
        final List<IntersectionStore.Candidate> requestCandidates =
                latestCandidates == null ? Collections.emptyList()
                        : new java.util.ArrayList<>(latestCandidates);

        lastMcpRequestElapsedMs = nowElapsed;
        mcpRequestInFlight = true;
        mcpExecutor.execute(() -> {
            HudMcpClient.Snapshot snapshot =
                    mcpClient.fetch(requestLocation, requestBearing, requestCandidates);
            handler.post(() -> {
                mcpRequestInFlight = false;
                if (snapshot != null && snapshot.ok) {
                    latestMcpSnapshot = snapshot;
                    latestMcpReceivedElapsedMs = SystemClock.elapsedRealtime();
                    renderLiveRows();
                }
            });
        });
    }

    private boolean hasFreshMcpSnapshot() {
        return latestMcpSnapshot != null
                && latestMcpSnapshot.ok
                && SystemClock.elapsedRealtime() - latestMcpReceivedElapsedMs <= 3000L;
    }

    private void renderMcpRows() {
        List<HudMcpClient.Row> rows = latestMcpSnapshot.rows;
        for (int i = 0; i < 3; i++) {
            if (rows == null || i >= rows.size()) {
                lights[i].setSignal(TrafficLightView.State.UNKNOWN, "--");
                names[i].setText(i == 0 ? "MCP 無前方號誌" : "—");
                continue;
            }
            HudMcpClient.Row row = rows.get(i);
            names[i].setText(formatIntersectionName(row.name));
            if (row.isCoverageOnly()
                    || row.state == TrafficLightView.State.UNKNOWN
                    || row.remainingSeconds < 0) {
                // Grade C is presence/geometry only. It must never emit a color
                // or countdown until explicitly promoted to a timed grade.
                lights[i].setSignal(TrafficLightView.State.UNKNOWN, "--");
            } else {
                // The server's remaining_s is valid at snapshot generation time.
                // Compensate for half the measured network round-trip plus time spent
                // locally since receipt, so the displayed countdown does not sit 1–2 s behind.
                long localAgeMs = Math.max(
                        0L, SystemClock.elapsedRealtime() - latestMcpReceivedElapsedMs);
                long estimatedOneWayMs = Math.min(
                        1500L, Math.max(0L, latestMcpSnapshot.roundTripMs / 2L));
                int elapsedSeconds = (int) ((localAgeMs + estimatedOneWayMs) / 1000L);
                int adjustedRemaining = Math.max(
                        1, row.remainingSeconds - elapsedSeconds);
                lights[i].setSignal(
                        row.state, Integer.toString(adjustedRemaining));
            }
        }
    }

    private void renderLiveRows() {
        if (demoMode) return;

        if (latestLocation == null) {
            renderWaitingRows();
            return;
        }

        if (shouldSuppressSurfaceSignals()) {
            renderSpecialRoadRows();
            return;
        }

        if (hasFreshMcpSnapshot()) {
            renderMcpRows();
            return;
        }

        boolean bearingReady = stableTravelBearingDeg != null;

        for (int i = 0; i < 3; i++) {
            if (i >= latestCandidates.size()) {
                lights[i].setSignal(TrafficLightView.State.UNKNOWN, "--");
                names[i].setText(i == 0 ? "未找到前方號誌" : "—");
                continue;
            }

            IntersectionStore.Candidate c = latestCandidates.get(i);
            names[i].setText(formatIntersectionName(c.intersection.name));

            if (c.intersection.isCoverageOnly() || !bearingReady) {
                // C-grade New Taipei / Keelung anchors intentionally stop here:
                // locate the next signal, but do not infer state or seconds.
                lights[i].setSignal(TrafficLightView.State.UNKNOWN, "--");
                continue;
            }

            SignalPlanStore.Estimate estimate = signalPlanStore.estimate(
                    c.intersection.id,
                    stableTravelBearingDeg,
                    System.currentTimeMillis());

            if (estimate.supported) {
                lights[i].setSignal(
                        estimate.state,
                        Integer.toString(estimate.remainingSeconds));
            } else {
                lights[i].setSignal(TrafficLightView.State.UNKNOWN, "--");
            }
        }
    }

    private boolean shouldSuppressSurfaceSignals() {
        boolean geometryLock = specialRoadStore != null
                && specialRoadStore.shouldSuppressSurfaceSignals();
        // Preserve the old speed heuristic only when the OSM special-road matcher
        // has no active road context at all.
        boolean fallback = specialRoadMatch == null
                && locationFilter.isHighSpeedRoadMode();
        return geometryLock || fallback;
    }

    private void renderSpecialRoadRows() {
        String road = "高速／快速道路";
        String structure = "特殊道路主線";
        if (specialRoadMatch != null) {
            road = specialRoadMatch.displayName();
            switch (specialRoadMatch.segment.structure) {
                case ELEVATED:
                    structure = "高架道路";
                    break;
                case TUNNEL:
                    structure = "隧道／地下道";
                    break;
                case MAINLINE:
                    structure = specialRoadMatch.segment.roadClass == SpecialRoadStore.RoadClass.HIGHWAY
                            ? "高速公路主線" : "快速道路主線";
                    break;
                default:
                    structure = "特殊道路主線";
                    break;
            }
        }

        lights[0].setSignal(TrafficLightView.State.UNKNOWN, "--");
        names[0].setText(formatIntersectionName(road));
        lights[1].setSignal(TrafficLightView.State.UNKNOWN, "--");
        names[1].setText(structure + "\n已抑制平面號誌");
        lights[2].setSignal(TrafficLightView.State.UNKNOWN, "--");
        names[2].setText("出口匝道後\n重新搜尋號誌");
    }

    private void resetDemo() {
        demoSeconds[0] = 22;
        demoSeconds[1] = 41;
        demoSeconds[2] = 3;
        demoStates[0] = TrafficLightView.State.GREEN;
        demoStates[1] = TrafficLightView.State.RED;
        demoStates[2] = TrafficLightView.State.YELLOW;
    }

    private void renderDemoRows() {
        String[] demoNames = new String[]{"仁愛路口", "信義路口", "和平東路口"};
        for (int i = 0; i < 3; i++) {
            lights[i].setSignal(demoStates[i], Integer.toString(demoSeconds[i]));
            names[i].setText(demoNames[i]);
        }
    }

    private String formatIntersectionName(String raw) {
        if (raw == null) return "—";
        String text = raw.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return "—";

        int elevatedSeparator = text.indexOf('・');
        if (elevatedSeparator > 0 && elevatedSeparator < text.length() - 1) {
            return text.substring(0, elevatedSeparator).trim()
                    + "\n"
                    + text.substring(elevatedSeparator + 1).trim();
        }

        int firstSpace = text.indexOf(' ');
        if (firstSpace > 0 && firstSpace < text.length() - 1) {
            return text.substring(0, firstSpace).trim()
                    + "\n"
                    + text.substring(firstSpace + 1).trim();
        }
        return text;
    }

    private static float angleDeltaAbs(float a, float b) {
        float delta = (b - a + 540f) % 360f - 180f;
        return Math.abs(delta);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (demoMode || location == null) return;
        if (!LocationManager.GPS_PROVIDER.equals(location.getProvider())) return;
        Location filtered = locationFilter.add(location);
        if (filtered != null) updateMatchedLocation(filtered);
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onDestroy() {
        demoMode = false;
        handler.removeCallbacks(demoTicker);
        handler.removeCallbacks(liveTicker);
        mcpExecutor.shutdownNow();
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
            ttsReady = false;
        }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
            overlayView = null;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
