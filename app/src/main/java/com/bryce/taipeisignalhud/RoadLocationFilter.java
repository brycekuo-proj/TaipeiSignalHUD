package com.bryce.taipeisignalhud;

import android.location.Location;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class RoadLocationFilter {
    private final ArrayDeque<Location> fixes = new ArrayDeque<>();
    private int fastFixes = 0;
    private int slowFixes = 0;
    private boolean highSpeedRoadMode = false;

    public Location add(Location raw) {
        if (raw == null) return null;
        if (raw.hasAccuracy() && raw.getAccuracy() > 50f) return null;

        long ageMs;
        if (raw.getElapsedRealtimeNanos() > 0L) {
            ageMs = Math.max(0L,
                    (SystemClock.elapsedRealtimeNanos() - raw.getElapsedRealtimeNanos()) / 1_000_000L);
        } else {
            ageMs = Math.max(0L, System.currentTimeMillis() - raw.getTime());
        }
        if (ageMs > 5000L) return null;

        Location previous = fixes.peekLast();
        if (previous != null) {
            long dtMs = Math.max(1L, raw.getTime() - previous.getTime());
            float distance = previous.distanceTo(raw);
            float impliedMps = distance / (dtMs / 1000f);
            float reported = raw.hasSpeed() ? raw.getSpeed() : 0f;
            float plausible = Math.max(55f, reported + 25f);
            if (impliedMps > plausible && distance > 80f) return null;
        }

        fixes.addLast(new Location(raw));
        long cutoff = raw.getTime() - 4500L;
        while (fixes.size() > 6 || (!fixes.isEmpty() && fixes.peekFirst().getTime() < cutoff)) {
            fixes.removeFirst();
        }

        updateRoadMode(raw);
        return smoothed(raw);
    }

    public boolean isHighSpeedRoadMode() {
        return highSpeedRoadMode;
    }

    private void updateRoadMode(Location raw) {
        float speed = raw.hasSpeed() ? raw.getSpeed() : 0f;
        if (speed >= 18.5f) {
            fastFixes++;
            slowFixes = 0;
        } else if (speed <= 12.0f) {
            slowFixes++;
            fastFixes = 0;
        } else {
            fastFixes = Math.max(0, fastFixes - 1);
            slowFixes = Math.max(0, slowFixes - 1);
        }

        if (!highSpeedRoadMode && fastFixes >= 3) {
            highSpeedRoadMode = true;
            slowFixes = 0;
        } else if (highSpeedRoadMode && slowFixes >= 5) {
            highSpeedRoadMode = false;
            fastFixes = 0;
        }
    }

    private Location smoothed(Location newest) {
        double sumW = 0.0;
        double lat = 0.0;
        double lon = 0.0;

        for (Location f : fixes) {
            float acc = f.hasAccuracy() ? Math.max(4f, f.getAccuracy()) : 20f;
            double ageSec = Math.max(0.0, (newest.getTime() - f.getTime()) / 1000.0);
            double ageWeight = Math.max(0.20, 1.0 - ageSec / 6.0);
            double w = ageWeight / (acc * acc);
            lat += f.getLatitude() * w;
            lon += f.getLongitude() * w;
            sumW += w;
        }

        Location out = new Location(newest);
        if (sumW > 0.0) {
            out.setLatitude(lat / sumW);
            out.setLongitude(lon / sumW);
        }

        if ((!newest.hasBearing() || newest.getSpeed() < 2.0f) && fixes.size() >= 2) {
            Location first = fixes.peekFirst();
            Location last = fixes.peekLast();
            if (first != null && last != null && first.distanceTo(last) >= 8f) {
                out.setBearing(first.bearingTo(last));
            }
        }

        float bestAccuracy = newest.hasAccuracy() ? newest.getAccuracy() : 50f;
        Iterator<Location> it = fixes.iterator();
        while (it.hasNext()) {
            Location f = it.next();
            if (f.hasAccuracy()) bestAccuracy = Math.min(bestAccuracy, f.getAccuracy());
        }
        out.setAccuracy(Math.max(4f, bestAccuracy));
        return out;
    }
}
