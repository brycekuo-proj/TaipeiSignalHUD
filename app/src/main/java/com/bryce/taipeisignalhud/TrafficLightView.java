package com.bryce.taipeisignalhud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

public final class TrafficLightView extends View {
    public enum State { RED, YELLOW, GREEN, UNKNOWN }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private State state = State.UNKNOWN;
    private String seconds = "--";

    public TrafficLightView(Context context) {
        super(context);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2));
        ring.setColor(Color.argb(185, 255, 255, 255));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setSignal(State state, String seconds) {
        this.state = state == null ? State.UNKNOWN : state;
        this.seconds = seconds == null || seconds.isEmpty() ? "--" : seconds;
        invalidate();
    }

    public State getSignalState() {
        return state;
    }

    public String getSignalSeconds() {
        return seconds;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(dp(48));
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(2);

        switch (state) {
            case RED:
                fill.setColor(Color.rgb(224, 48, 48));
                text.setColor(Color.WHITE);
                break;
            case YELLOW:
                fill.setColor(Color.rgb(255, 205, 38));
                text.setColor(Color.rgb(35, 35, 35));
                break;
            case GREEN:
                fill.setColor(Color.rgb(38, 196, 91));
                text.setColor(Color.WHITE);
                break;
            default:
                fill.setColor(Color.rgb(72, 76, 84));
                text.setColor(Color.WHITE);
                break;
        }

        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius - dp(1), ring);

        float textSize = seconds.length() >= 3 ? sp(14) : sp(18);
        text.setTextSize(textSize);
        Paint.FontMetrics fm = text.getFontMetrics();
        float y = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(seconds, cx, y, text);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
