package com.bryce.taipeisignalhud;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

public final class SignalCarScreen extends Screen implements CarHudState.Listener {
    SignalCarScreen(CarContext carContext) {
        super(carContext);
        CarHudState.subscribe(this);
    }

    @Override
    public void onCarHudStateChanged() {
        getCarContext().getMainExecutor().execute(this::invalidate);
    }

    @Override
    public Template onGetTemplate() {
        CarHudState.Snapshot snapshot = CarHudState.read(getCarContext());
        String status = snapshot.isFresh()
                ? compact(snapshot.status)
                : "資料逾時 · 請啟動手機 HUD";

        Pane.Builder pane = new Pane.Builder();
        for (int i = 0; i < snapshot.rows.length; i++) {
            CarHudState.Row signal = snapshot.rows[i];
            pane.addRow(new Row.Builder()
                    .setTitle((i + 1) + " · " + signalText(signal))
                    .addText(compact(signal.name))
                    .build());
        }

        Header header = new Header.Builder()
                .setTitle("Signal HUD · " + status)
                .setStartHeaderAction(Action.APP_ICON)
                .build();

        return new PaneTemplate.Builder(pane.build())
                .setHeader(header)
                .build();
    }

    private String signalText(CarHudState.Row row) {
        String prefix;
        switch (row.state) {
            case GREEN:
                prefix = "綠燈";
                break;
            case YELLOW:
                prefix = "黃燈";
                break;
            case RED:
                prefix = "紅燈";
                break;
            default:
                prefix = "燈號未知";
                break;
        }
        if (row.state == TrafficLightView.State.UNKNOWN
                || row.seconds == null
                || row.seconds.isEmpty()
                || "--".equals(row.seconds)) {
            return prefix;
        }
        return prefix + " " + row.seconds + " 秒";
    }

    private String compact(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        return raw.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
