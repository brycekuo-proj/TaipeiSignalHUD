package com.bryce.taipeisignalhud;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 2001;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root);

        TextView title = text("TaipeiSignalHUD", 27, true);
        root.addView(title);

        TextView subtitle = text("A37 Road Test · v0.0.1", 14, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle);

        statusView = text("", 15, false);
        statusView.setPadding(0, dp(20), 0, dp(12));
        root.addView(statusView);

        Button overlayPermission = button("1. 開啟懸浮視窗權限");
        overlayPermission.setOnClickListener(v -> openOverlayPermission());
        root.addView(overlayPermission, fullWidth());

        Button locationPermission = button("2. 開啟 GPS 權限");
        locationPermission.setOnClickListener(v -> requestRuntimePermissions());
        root.addView(locationPermission, fullWidth());

        Button start = button("開始道路測試 Overlay");
        start.setOnClickListener(v -> startHud(false));
        root.addView(start, fullWidth());

        Button demo = button("Overlay 三色倒數預覽（DEMO）");
        demo.setOnClickListener(v -> startHud(true));
        root.addView(demo, fullWidth());

        Button stop = button("停止 Overlay");
        stop.setOnClickListener(v -> stopHud());
        root.addView(stop, fullWidth());

        TextView note = text(
                "道路測試版功能：\n" +
                "• 使用 A37 GPS 與行進方向找出前方 3 個候選號誌。\n" +
                "• 每個路口只顯示 1 顆圓燈；同一顆燈支援紅／黃／綠，秒數在圓心。\n" +
                "• Overlay 可直接拖動位置。\n" +
                "• 目前尚未完成 phaseorder 解碼與現場相位同步，因此正式道路模式的燈號先顯示灰色「--」，不偽造倒數。\n" +
                "• DEMO 僅用來檢查你指定的三色/圓心倒數視覺，不代表真實號誌。\n\n" +
                "道路測試時以現場交通號誌為準。",
                14, false);
        note.setTextColor(Color.rgb(65, 65, 65));
        note.setPadding(0, dp(22), 0, 0);
        root.addView(note);

        return scroll;
    }

    private void refreshStatus() {
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        statusView.setText(
                "Overlay 權限：" + (overlay ? "OK" : "尚未開啟") +
                "\nGPS 權限：" + (location ? "OK" : "尚未開啟"));
        statusView.setTextColor(overlay && location
                ? Color.rgb(25, 130, 70)
                : Color.rgb(170, 65, 35));
    }

    private void openOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        Intent i = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            }, REQ_PERMISSIONS);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_PERMISSIONS);
        }
    }

    private void startHud(boolean demo) {
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!overlay) {
            Toast.makeText(this, "請先開啟懸浮視窗權限。", Toast.LENGTH_LONG).show();
            openOverlayPermission();
            return;
        }
        if (!location) {
            Toast.makeText(this, "請先允許精確位置。", Toast.LENGTH_LONG).show();
            requestRuntimePermissions();
            return;
        }

        Intent i = new Intent(this, HudService.class)
                .setAction(demo ? HudService.ACTION_DEMO : HudService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this,
                demo ? "已啟動 DEMO Overlay。" : "道路測試 Overlay 已啟動。",
                Toast.LENGTH_SHORT).show();
    }

    private void stopHud() {
        Intent i = new Intent(this, HudService.class).setAction(HudService.ACTION_STOP);
        try { startService(i); } catch (Exception ignored) { stopService(new Intent(this, HudService.class)); }
        Toast.makeText(this, "Overlay 已停止。", Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(Color.BLACK);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        return b;
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
