package com.bryce.taipeisignalhud;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class CarHudState {
    private static final String PREFS = "car_hud_state";
    private static final String KEY_STATUS = "status";
    private static final String KEY_UPDATED_AT = "updated_at_ms";
    private static final List<WeakReference<Listener>> listeners = new ArrayList<>();

    public interface Listener {
        void onCarHudStateChanged();
    }

    public static final class Row {
        public final String name;
        public final TrafficLightView.State state;
        public final String seconds;

        Row(String name, TrafficLightView.State state, String seconds) {
            this.name = name;
            this.state = state;
            this.seconds = seconds;
        }
    }

    public static final class Snapshot {
        public final String status;
        public final long updatedAtMs;
        public final Row[] rows;

        Snapshot(String status, long updatedAtMs, Row[] rows) {
            this.status = status;
            this.updatedAtMs = updatedAtMs;
            this.rows = rows;
        }

        public boolean isFresh() {
            return updatedAtMs > 0L && System.currentTimeMillis() - updatedAtMs <= 5000L;
        }
    }

    private CarHudState() {}

    public static void publish(Context context, String status,
                               String[] names,
                               TrafficLightView.State[] states,
                               String[] seconds) {
        if (context == null) return;
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATUS, status == null ? "" : status)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis());

        for (int i = 0; i < 3; i++) {
            String name = names != null && i < names.length && names[i] != null
                    ? names[i] : "—";
            TrafficLightView.State state = states != null && i < states.length && states[i] != null
                    ? states[i] : TrafficLightView.State.UNKNOWN;
            String sec = seconds != null && i < seconds.length && seconds[i] != null
                    ? seconds[i] : "--";
            e.putString("row_" + i + "_name", name);
            e.putString("row_" + i + "_state", state.name());
            e.putString("row_" + i + "_seconds", sec);
        }
        e.apply();
        notifyListeners();
    }

    public static Snapshot read(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Row[] rows = new Row[3];
        for (int i = 0; i < 3; i++) {
            String name = p.getString("row_" + i + "_name", i == 0 ? "請先在手機啟動道路測試" : "—");
            String sec = p.getString("row_" + i + "_seconds", "--");
            TrafficLightView.State state = TrafficLightView.State.UNKNOWN;
            try {
                state = TrafficLightView.State.valueOf(
                        p.getString("row_" + i + "_state", TrafficLightView.State.UNKNOWN.name()));
            } catch (Exception ignored) {}
            rows[i] = new Row(name, state, sec);
        }
        return new Snapshot(
                p.getString(KEY_STATUS, "等待 HUD"),
                p.getLong(KEY_UPDATED_AT, 0L),
                rows);
    }

    public static synchronized void subscribe(Listener listener) {
        if (listener == null) return;
        for (Iterator<WeakReference<Listener>> it = listeners.iterator(); it.hasNext();) {
            Listener current = it.next().get();
            if (current == null) {
                it.remove();
            } else if (current == listener) {
                return;
            }
        }
        listeners.add(new WeakReference<>(listener));
    }

    private static synchronized void notifyListeners() {
        for (Iterator<WeakReference<Listener>> it = listeners.iterator(); it.hasNext();) {
            Listener listener = it.next().get();
            if (listener == null) {
                it.remove();
            } else {
                listener.onCarHudStateChanged();
            }
        }
    }
}
