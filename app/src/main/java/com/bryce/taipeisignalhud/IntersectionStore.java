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
        public final String region;
        public final String coverageGrade;
        public final String source;

        Intersection(String id, String name, double longitude, double latitude,
                     String region, String coverageGrade, String source) {
            this.id = id;
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
            this.region = region == null ? "" : region;
            this.coverageGrade = coverageGrade == null ? "" : coverageGrade;
            this.source = source == null ? "" : source;
        }

        public boolean isCoverageOnly() {
            return "C".equalsIgnoreCase(coverageGrade);
        }
    }

    public static final class Candidate {
        public final Intersection intersection;
        public final double distanceM;
        public final double alongTrackM;
        public final double crossTrackM;
        public final double relativeBearingDeg;

        Candidate(Intersection intersection, double distanceM, double alongTrackM,
                  double crossTrackM, double relativeBearingDeg) {
            this.intersection = intersection;
            this.distanceM = distanceM;
            this.alongTrackM = alongTrackM;
            this.crossTrackM = crossTrackM;
            this.relativeBearingDeg = relativeBearingDeg;
        }
    }

    private final List<Intersection> intersections = new ArrayList<>();

    public IntersectionStore(Context context) {
        load(context, "intersections.psv", "TPE", "TIMED", "TPE_OFFICIAL");
        load(context, "intersections_c.psv", "", "C", "OSM");
    }

    public int size() {
        return intersections.size();
    }

    public List<Candidate> findAhead(Location location, int limit) {
        if (location == null || intersections.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        float speed = location.hasSpeed() ? location.getSpeed() : 0f;
        boolean headingReliable = location.hasBearing() && speed >= 2.0f;
        double heading = normalizeBearing(location.getBearing());
        double accuracy = location.hasAccuracy() ? Math.max(5.0, location.getAccuracy()) : 25.0;

        double maxDistance = speed >= 12f ? 1800.0 : 1200.0;
        double maxAngle = speed >= 12f ? 32.0 : 45.0;
        double maxCrossTrack = Math.max(speed >= 12f ? 55.0 : 70.0, accuracy * 1.8);

        ArrayList<Candidate> result = new ArrayList<>();
        for (Intersection s : intersections) {
            double distance = distanceMeters(
                    location.getLatitude(), location.getLongitude(),
                    s.latitude, s.longitude);
            if (distance < 10.0 || distance > maxDistance) continue;

            double bearing = bearingDegrees(
                    location.getLatitude(), location.getLongitude(),
                    s.latitude, s.longitude);
            double relative = headingReliable ? signedAngleDelta(heading, bearing) : 0.0;

            double along;
            double cross;
            if (headingReliable) {
                double rad = Math.toRadians(relative);
                along = distance * Math.cos(rad);
                cross = Math.abs(distance * Math.sin(rad));
                if (along <= 8.0) continue;
                if (Math.abs(relative) > maxAngle) continue;
                if (cross > maxCrossTrack) continue;
            } else {
                along = distance;
                cross = 0.0;
            }

            result.add(new Candidate(s, distance, along, cross, relative));
        }

        // Prefer the geometrically closest point along the current travel corridor,
        // then use cross-track distance to reject adjacent/parallel streets.
        Collections.sort(result, Comparator
                .comparingDouble((Candidate c) -> c.alongTrackM)
                .thenComparingDouble(c -> c.crossTrackM)
                .thenComparingDouble(c -> c.distanceM));

        ArrayList<Candidate> deduped = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (Candidate c : result) {
            // Taipei's official rows can still be de-duplicated by display name.
            // C-grade OSM rows are separate physical coverage anchors; two
            // consecutive signals on the same road may intentionally share a name.
            String key = c.intersection.isCoverageOnly()
                    ? c.intersection.id
                    : normalizeName(c.intersection.name);
            if (!seenNames.add(key)) continue;
            deduped.add(c);
            if (deduped.size() >= limit) break;
        }
        return deduped;
    }

    private void load(Context context, String assetName,
                      String defaultRegion, String defaultGrade, String defaultSource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(assetName), StandardCharsets.UTF_8))) {
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
                    String region = p.length > 4 && !p[4].trim().isEmpty()
                            ? p[4].trim() : defaultRegion;
                    String grade = p.length > 5 && !p[5].trim().isEmpty()
                            ? p[5].trim() : defaultGrade;
                    String source = p.length > 6 && !p[6].trim().isEmpty()
                            ? p[6].trim() : defaultSource;
                    if (name.isEmpty()) name = id;
                    intersections.add(new Intersection(
                            id, name, lon, lat, region, grade, source));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String cleanupDisplayName(String s) {
        if (s == null) return "";
        return s.replace('\u3000', ' ').replaceAll("\\s+", " ").trim();
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
        double x = Math.cos(p1) * Math.sin(p2)
                - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return normalizeBearing(Math.toDegrees(Math.atan2(y, x)));
    }

    private static double normalizeBearing(double v) {
        double out = v % 360.0;
        return out < 0 ? out + 360.0 : out;
    }

    private static double signedAngleDelta(double from, double to) {
        return (to - from + 540.0) % 360.0 - 180.0;
    }
}
