package com.bryce.taipeisignalhud;

import android.content.Context;
import android.location.Location;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EnforcementStore {
    public enum Type { SPEED, RED_LIGHT, TECH, SECTION }

    public static final class Point {
        public final String id;
        public final Type type;
        public final double latitude;
        public final double longitude;
        public final String title;
        public final String detail;
        public final String direction;
        public final String speedLimit;

        Point(
                String id,
                Type type,
                double latitude,
                double longitude,
                String title,
                String detail,
                String direction,
                String speedLimit) {
            this.id = id;
            this.type = type;
            this.latitude = latitude;
            this.longitude = longitude;
            this.title = title;
            this.detail = detail;
            this.direction = direction;
            this.speedLimit = speedLimit;
        }
    }

    public static final class Match {
        public final Point point;
        public final float distanceMeters;
        public final float warningDistanceMeters;

        Match(Point point, float distanceMeters, float warningDistanceMeters) {
            this.point = point;
            this.distanceMeters = distanceMeters;
            this.warningDistanceMeters = warningDistanceMeters;
        }
    }

    private final List<Point> points;

    public EnforcementStore(Context context) {
        points = Collections.unmodifiableList(load(context));
    }

    public int size() {
        return points.size();
    }

    public Match findApproaching(Location location, Float travelBearingDeg) {
        if (location == null || travelBearingDeg == null) return null;

        float speedMps = location.hasSpeed() ? Math.max(0f, location.getSpeed()) : 0f;
        float warningDistance = clamp(420f + speedMps * 11f, 420f, 900f);
        Point best = null;
        float bestDistance = Float.MAX_VALUE;
        float[] result = new float[3];

        for (Point p : points) {
            Location.distanceBetween(
                    location.getLatitude(),
                    location.getLongitude(),
                    p.latitude,
                    p.longitude,
                    result);
            float distance = result[0];
            if (distance > warningDistance || distance < 12f) continue;

            float bearingToPoint = normalizeBearing(result[1]);
            float aheadDelta = angleDeltaAbs(travelBearingDeg, bearingToPoint);
            float maxAheadDelta = p.type == Type.TECH ? 42f : 55f;
            if (aheadDelta > maxAheadDelta) continue;

            if (!directionMatches(p.direction, travelBearingDeg)) continue;

            if (distance < bestDistance) {
                best = p;
                bestDistance = distance;
            }
        }

        return best == null ? null : new Match(best, bestDistance, warningDistance);
    }

    private List<Point> load(Context context) {
        ArrayList<Point> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("enforcement_points.psv"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 8) continue;
                try {
                    Type type = Type.valueOf(parts[1]);
                    double lat = Double.parseDouble(parts[2]);
                    double lon = Double.parseDouble(parts[3]);
                    out.add(new Point(
                            parts[0],
                            type,
                            lat,
                            lon,
                            parts[4],
                            parts[5],
                            parts[6],
                            parts[7]));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static boolean directionMatches(String direction, float travelBearing) {
        if (direction == null) return true;
        String d = direction.trim();
        if (d.isEmpty()) return true;

        if (d.startsWith("BEARING:")) {
            try {
                float expectedBearing = Float.parseFloat(d.substring("BEARING:".length()));
                return angleDeltaAbs(travelBearing, expectedBearing) <= 58f;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }

        if (containsAny(d, "西南向東北", "西南向东北", "西南往東北", "西南往东北")) {
            return angleDeltaAbs(travelBearing, 45f) <= 58f;
        }
        if (containsAny(d, "西北向東南", "西北向东南", "西北往東南", "西北往东南")) {
            return angleDeltaAbs(travelBearing, 135f) <= 58f;
        }
        if (containsAny(d, "東北向西南", "东北向西南", "東北往西南", "东北往西南")) {
            return angleDeltaAbs(travelBearing, 225f) <= 58f;
        }
        if (containsAny(d, "東南向西北", "东南向西北", "東南往西北", "东南往西北")) {
            return angleDeltaAbs(travelBearing, 315f) <= 58f;
        }

        ArrayList<Float> expected = new ArrayList<>(2);
        if (containsAny(d, "南北雙向", "南北双向")) {
            expected.add(0f);
            expected.add(180f);
        } else if (containsAny(d, "東西雙向", "东西双向")) {
            expected.add(90f);
            expected.add(270f);
        } else {
            if (containsAny(d, "南向北", "南往北", "往北")) expected.add(0f);
            if (containsAny(d, "西向東", "西向东", "西往東", "西往东", "往東", "往东")) expected.add(90f);
            if (containsAny(d, "北向南", "北往南", "往南")) expected.add(180f);
            if (containsAny(d, "東向西", "东向西", "東往西", "东往西", "往西")) expected.add(270f);

        }

        // Unknown wording is deliberately permissive; an unrecognized source string
        // should not silently suppress a real enforcement warning.
        if (expected.isEmpty()) return true;

        for (float bearing : expected) {
            if (angleDeltaAbs(travelBearing, bearing) <= 58f) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static float normalizeBearing(float degrees) {
        float result = degrees % 360f;
        return result < 0f ? result + 360f : result;
    }

    private static float angleDeltaAbs(float a, float b) {
        float delta = (b - a + 540f) % 360f - 180f;
        return Math.abs(delta);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
