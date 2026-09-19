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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
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
    private final TrafficLightView[] lights = new TrafficLightView[3];
    private final TextView[] names = new TextView[3];

    private LocationManager locationManager;
    private IntersectionStore intersectionStore;
    private SignalPlanStore signalPlanStore;
    private HudMcpClient mcpClient;
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
        mcpClient = new HudMcpClient(this);
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
                .setContentText("GPS、前方路口與號誌倒數正在運作")
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

        if (locationFilter.isHighSpeedRoadMode()) {
            latestCandidates = Collections.emptyList();
        } else if (movingWithReliableBearing) {
            // While moving, continuously refresh the forward corridor and remember it.
            latestCandidates = intersectionStore.findAhead(location, 3);
        } else if (stableTravelBearingDeg != null && latestCandidates.isEmpty()) {
            // If the service starts just as the vehicle is slowing/stopped, reuse the
            // last reliable travel direction to acquire candidates once.
            Location matchingLocation = new Location(location);
            matchingLocation.setBearing(stableTravelBearingDeg);
            matchingLocation.setSpeed(2.1f);
            latestCandidates = intersectionStore.findAhead(matchingLocation, 3);
        }
        // When stopped with existing candidates, deliberately keep them frozen.
        // GPS course becomes noisy near 0 km/h, but the signal phase must keep counting.
        renderLiveRows();
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
            if (row.state == TrafficLightView.State.UNKNOWN || row.remainingSeconds < 0) {
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

        if (hasFreshMcpSnapshot()) {
            renderMcpRows();
            return;
        }

        if (locationFilter.isHighSpeedRoadMode()) {
            lights[0].setSignal(TrafficLightView.State.UNKNOWN, "--");
            names[0].setText("高速／快速道路模式");
            lights[1].setSignal(TrafficLightView.State.UNKNOWN, "--");
            names[1].setText("MCP 無匝道資料，已抑制平面號誌");
            lights[2].setSignal(TrafficLightView.State.UNKNOWN, "--");
            names[2].setText("等待匝道／降速匹配");
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

            if (!bearingReady) {
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
