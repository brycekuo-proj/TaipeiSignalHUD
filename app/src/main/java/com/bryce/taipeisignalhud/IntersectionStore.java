package com.bryce.taipeisignalhud;

import android.content.Context;
import android.location.Location;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class IntersectionStore {
    public static final class Intersection {
        public final String id;
        public final String name;
        public final double longitude;
        public final double latitude;

        Intersection(String id, String name, double longitude, double latitude) {
            this.id = id;
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    public static final class Candidate {
        public final Intersection intersection;
        public final double distanceM;
        public final double relativeBearingDeg;

        Candidate(Intersection intersection, double distanceM, double relativeBearingDeg) {
            this.intersection = intersection;
            this.distanceM = distanceM;
            this.relativeBearingDeg = relativeBearingDeg;
        }
    }

    private final List<Intersection> intersections = new ArrayList<>();

    public IntersectionStore(Context context) {
        load(context);
    }

    public int size() {
        return intersections.size();
    }

    public List<Candidate> findAhead(Location location, int limit) {
        if (location == null || intersections.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        boolean headingReliable = location.hasBearing() &&
                (!location.hasSpeed() || location.getSpeed() >= 1.5f);
        double heading = normalizeBearing(location.getBearing());

        ArrayList<Candidate> result = new ArrayList<>();
        for (Intersection s : intersections) {
            double distance = distanceMeters(location.getLatitude(), location.getLongitude(), s.latitude, s.longitude);
            if (distance < 12.0 || distance > 1200.0) continue;

            double bearing = bearingDegrees(location.getLatitude(), location.getLongitude(), s.latitude, s.longitude);
            double relative = headingReliable ? signedAngleDelta(heading, bearing) : 0.0;
            if (headingReliable && Math.abs(relative) > 55.0) continue;

            result.add(new Candidate(s, distance, relative));
        }

        Collections.sort(result, Comparator.comparingDouble(c -> c.distanceM));

        ArrayList<Candidate> deduped = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (Candidate c : result) {
            String key = normalizeName(c.intersection.name);
            if (!seenNames.add(key)) continue;
            deduped.add(c);
            if (deduped.size() >= limit) break;
        }
        return deduped;
    }

    private void load(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("intersections.psv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 4) continue;
                try {
                    String id = p[0].trim();
                    String name = cleanupDisplayName(p[1]);
                    double lon = Double.parseDouble(p[2]);
                    double lat = Double.parseDouble(p[3]);
                    if (name.isEmpty()) name = id;
                    intersections.add(new Intersection(id, name, lon, lat));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String cleanupDisplayName(String s) {
        if (s == null) return "";
        String out = s.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
        return out;
    }

    private static String normalizeName(String s) {
        return cleanupDisplayName(s).toLowerCase(Locale.TAIWAN);
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return r * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return normalizeBearing(Math.toDegrees(Math.atan2(y, x)));
    }

    private static double normalizeBearing(double v) {
        double out = v % 360.0;
        return out < 0 ? out + 360.0 : out;
    }

    private static double signedAngleDelta(double from, double to) {
        double d = (to - from + 540.0) % 360.0 - 180.0;
        return d;
    }
}
