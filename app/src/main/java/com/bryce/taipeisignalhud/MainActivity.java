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
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 2001;

    private TextView statusView;
    private TextView enforcementValueView;
    private TextView opacityValueView;
    private TextView speedValueView;

    private boolean bindingEnforcementSpinner;
    private boolean bindingSpeedSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshSettingLabels();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(root);

        TextView title = text("TaipeiSignalHUD", 27, true);
        root.addView(title);

        TextView subtitle = text("Road Test · v" + appVersion(), 14, false);
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

        addDivider(root);
        root.addView(sectionTitle("使用者設定"));

        addEnforcementDistanceSettings(root);
        addOverlayOpacitySettings(root);
        addSpeedAlertSettings(root);

        addDivider(root);

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
                "目前可調整：\n" +
                "• 測速／科技執法／區間測速提醒距離：50、100、300、500 公尺或自訂。\n" +
                "• Overlay 透明度：0–100%。0% 完全透明，100% 完全不透明。\n" +
                "• 車速提醒：50、60、70 km/h 或自訂；超過門檻時顯示並語音提示。\n\n" +
                "道路辨識：\n" +
                "• GPS/GNSS 定位＋精度門檻、短期平滑、跳點排除與行進方向過濾。\n" +
                "• 高架／快速道路／高速公路／隧道／地下道使用特殊道路幾何與道路層級判斷。\n" +
                "• 主線抑制平面號誌；進入出口匝道後重新搜尋第一個可達號誌。\n" +
                "• 新北／基隆 C 級號誌只辨識位置，不猜燈色與秒數。\n" +
                "• MCP 逾時會自動退回本機算法。\n\n" +
                "道路測試時以現場交通號誌及速限標誌為準。",
                14, false);
        note.setTextColor(Color.rgb(65, 65, 65));
        note.setPadding(0, dp(22), 0, 0);
        root.addView(note);

        return scroll;
    }

    private void addEnforcementDistanceSettings(LinearLayout root) {
        root.addView(settingLabel("執法提醒距離"));

        enforcementValueView = valueText("");
        root.addView(enforcementValueView);

        String[] labels = {
                "50 公尺",
                "100 公尺",
                "300 公尺",
                "500 公尺",
                "自訂"
        };
        Spinner spinner = spinner(labels);
        int current = UserSettings.enforcementDistanceM(this);
        int selected = enforcementPresetIndex(current);
        bindingEnforcementSpinner = true;
        spinner.setSelection(selected, false);
        bindingEnforcementSpinner = false;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                if (bindingEnforcementSpinner) return;
                int meters = enforcementPresetValue(position);
                if (meters > 0) {
                    UserSettings.setEnforcementDistanceM(MainActivity.this, meters);
                    refreshSettingLabels();
                    toast("執法提醒距離已設為 " + meters + " 公尺");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spinner, fullWidth());

        LinearLayout customRow = horizontalRow();
        EditText custom = numberInput("自訂公尺", Integer.toString(current));
        customRow.addView(custom, weighted());
        Button apply = smallButton("套用自訂");
        apply.setOnClickListener(v -> {
            Integer value = parseInt(custom.getText().toString());
            if (value == null || value < 20 || value > 5000) {
                toast("請輸入 20～5000 公尺。");
                return;
            }
            UserSettings.setEnforcementDistanceM(this, value);
            bindingEnforcementSpinner = true;
            spinner.setSelection(enforcementPresetIndex(value), false);
            bindingEnforcementSpinner = false;
            refreshSettingLabels();
            toast("執法提醒距離已設為 " + value + " 公尺");
        });
        customRow.addView(apply);
        root.addView(customRow, fullWidth());
    }

    private void addOverlayOpacitySettings(LinearLayout root) {
        root.addView(settingLabel("Overlay 透明度"));

        opacityValueView = valueText("");
        root.addView(opacityValueView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(UserSettings.overlayOpacityPercent(this));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                UserSettings.setOverlayOpacityPercent(MainActivity.this, progress);
                refreshSettingLabels();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                toast("Overlay 透明度 " + seekBar.getProgress() + "%");
            }
        });
        root.addView(seekBar, fullWidth());

        TextView hint = text("0% = 完全透明　｜　100% = 完全不透明", 12, false);
        hint.setTextColor(Color.GRAY);
        root.addView(hint);
    }

    private void addSpeedAlertSettings(LinearLayout root) {
        root.addView(settingLabel("車速提示門檻"));

        speedValueView = valueText("");
        root.addView(speedValueView);

        String[] labels = {
                "50 km/h",
                "60 km/h",
                "70 km/h",
                "自訂"
        };
        Spinner spinner = spinner(labels);
        int current = UserSettings.speedAlertKmh(this);
        int selected = speedPresetIndex(current);
        bindingSpeedSpinner = true;
        spinner.setSelection(selected, false);
        bindingSpeedSpinner = false;
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                if (bindingSpeedSpinner) return;
                int kmh = speedPresetValue(position);
                if (kmh > 0) {
                    UserSettings.setSpeedAlertKmh(MainActivity.this, kmh);
                    refreshSettingLabels();
                    toast("車速提醒門檻已設為 " + kmh + " km/h");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(spinner, fullWidth());

        LinearLayout customRow = horizontalRow();
        EditText custom = numberInput("自訂 km/h", Integer.toString(current));
        customRow.addView(custom, weighted());
        Button apply = smallButton("套用自訂");
        apply.setOnClickListener(v -> {
            Integer value = parseInt(custom.getText().toString());
            if (value == null || value < 10 || value > 300) {
                toast("請輸入 10～300 km/h。");
                return;
            }
            UserSettings.setSpeedAlertKmh(this, value);
            bindingSpeedSpinner = true;
            spinner.setSelection(speedPresetIndex(value), false);
            bindingSpeedSpinner = false;
            refreshSettingLabels();
            toast("車速提醒門檻已設為 " + value + " km/h");
        });
        customRow.addView(apply);
        root.addView(customRow, fullWidth());
    }

    private void refreshSettingLabels() {
        if (enforcementValueView != null) {
            enforcementValueView.setText(
                    "目前：" + UserSettings.enforcementDistanceM(this) + " 公尺");
        }
        if (opacityValueView != null) {
            opacityValueView.setText(
                    "目前：" + UserSettings.overlayOpacityPercent(this) + "%");
        }
        if (speedValueView != null) {
            speedValueView.setText(
                    "目前：" + UserSettings.speedAlertKmh(this) + " km/h");
        }
    }

    private int enforcementPresetIndex(int value) {
        if (value == 50) return 0;
        if (value == 100) return 1;
        if (value == 300) return 2;
        if (value == 500) return 3;
        return 4;
    }

    private int enforcementPresetValue(int index) {
        if (index == 0) return 50;
        if (index == 1) return 100;
        if (index == 2) return 300;
        if (index == 3) return 500;
        return -1;
    }

    private int speedPresetIndex(int value) {
        if (value == 50) return 0;
        if (value == 60) return 1;
        if (value == 70) return 2;
        return 3;
    }

    private int speedPresetValue(int index) {
        if (index == 0) return 50;
        if (index == 1) return 60;
        if (index == 2) return 70;
        return -1;
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
        try {
            startService(i);
        } catch (Exception ignored) {
            stopService(new Intent(this, HudService.class));
        }
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

    private TextView sectionTitle(String value) {
        TextView v = text(value, 20, true);
        v.setPadding(0, dp(8), 0, dp(8));
        return v;
    }

    private TextView settingLabel(String value) {
        TextView v = text(value, 16, true);
        v.setPadding(0, dp(12), 0, dp(2));
        return v;
    }

    private TextView valueText(String value) {
        TextView v = text(value, 13, false);
        v.setTextColor(Color.rgb(65, 90, 130));
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        return b;
    }

    private Button smallButton(String label) {
        Button b = button(label);
        b.setMinHeight(dp(46));
        return b;
    }

    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private EditText numberInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setPadding(dp(8), dp(6), dp(8), dp(6));
        return input;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private void addDivider(LinearLayout root) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(225, 225, 225));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        p.setMargins(0, dp(16), 0, dp(12));
        root.addView(divider, p);
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f);
        p.setMargins(0, 0, dp(8), 0);
        return p;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String appVersion() {
        try {
            String version = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            return version == null || version.trim().isEmpty() ? "?" : version;
        } catch (Exception ignored) {
            return "?";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
