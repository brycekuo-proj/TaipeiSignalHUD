package com.bryce.taipeisignalhud;

import android.content.Context;
import android.location.Location;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometry-based classifier for grade-separated / limited-access roads.
 *
 * The key design goal is not perfect navigation map matching. It is to avoid
 * showing surface-road traffic lights while the vehicle is actually on an
 * elevated road, expressway, freeway, tunnel, or their ramps.
 */
public final class SpecialRoadStore {
    public enum RoadClass { HIGHWAY, EXPRESSWAY, URBAN }
    public enum Structure { MAINLINE, ELEVATED, TUNNEL, RAMP, COMPLEX }

    public static final class Segment {
        final String id;
        final String wayKey;
        final String region;
        final RoadClass roadClass;
        final Structure structure;
        final String name;
        final String ref;
        final int layer;
        final double lat1;
        final double lon1;
        final double lat2;
        final double lon2;
        final String oneway;
        final String maxspeed;

        Segment(String id, String region, RoadClass roadClass, Structure structure,
                String name, String ref, int layer,
                double lat1, double lon1, double lat2, double lon2,
                String oneway, String maxspeed) {
            this.id = id;
            int cut = id.lastIndexOf('-');
            this.wayKey = cut > 0 ? id.substring(0, cut) : id;
            this.region = region;
            this.roadClass = roadClass;
            this.structure = structure;
            this.name = name;
            this.ref = ref;
            this.layer = layer;
            this.lat1 = lat1;
            this.lon1 = lon1;
            this.lat2 = lat2;
            this.lon2 = lon2;
            this.oneway = oneway;
            this.maxspeed = maxspeed;
        }

        String displayName() {
            if (name != null && !name.isEmpty()) return name;
            if (ref != null && !ref.isEmpty()) return ref;
            if (roadClass == RoadClass.HIGHWAY) return "國道";
            if (roadClass == RoadClass.EXPRESSWAY) return "快速道路";
            return "特殊道路";
        }
    }

    public static final class Match {
        public final Segment segment;
        public final double distanceM;
        public final double headingDeltaDeg;
        public final boolean stable;

        Match(Segment segment, double distanceM, double headingDeltaDeg, boolean stable) {
            this.segment = segment;
            this.distanceM = distanceM;
            this.headingDeltaDeg = headingDeltaDeg;
            this.stable = stable;
        }

        public boolean isRamp() {
            return segment.structure == Structure.RAMP;
        }

        public boolean suppressSurfaceSignals() {
            if (isRamp()) return false;
            return segment.roadClass == RoadClass.HIGHWAY
                    || segment.roadClass == RoadClass.EXPRESSWAY
                    || segment.structure == Structure.ELEVATED
                    || segment.structure == Structure.TUNNEL;
        }

        public String displayName() {
            return segment.displayName();
        }
    }

    private static final double CELL_DEG = 0.0020; // ~200 m at Taiwan latitudes.
    private final Map<Long, ArrayList<Segment>> grid = new HashMap<>();

    private Match activeMatch;
    private String pendingWayKey;
    private int pendingHits = 0;
    private int missHits = 0;
    private boolean nearSpecialCorridor = false;

    public SpecialRoadStore(Context context) {
        load(context);
    }

    public Match update(Location location, Float stableBearingDeg) {
        if (location == null) return activeMatch;

        Match raw = findBest(location, stableBearingDeg);
        nearSpecialCorridor = raw != null
                && raw.suppressSurfaceSignals()
                && raw.distanceM <= ambiguityThreshold(location);

        if (raw == null) {
            pendingWayKey = null;
            pendingHits = 0;
            missHits++;
            // Keep the current road-level lock briefly across noisy GNSS / tunnel fixes.
            if (activeMatch != null && missHits <= 6) return activeMatch;
            activeMatch = null;
            return null;
        }

        missHits = 0;

        if (activeMatch != null && sameCorridor(activeMatch.segment, raw.segment)) {
            activeMatch = new Match(raw.segment, raw.distanceM, raw.headingDeltaDeg, true);
            pendingWayKey = null;
            pendingHits = 0;
            return activeMatch;
        }

        if (raw.segment.structure == Structure.RAMP) {
            // Ramps are the transition boundary: switch immediately so the HUD can
            // acquire the first reachable signal at the ramp exit.
            activeMatch = new Match(raw.segment, raw.distanceM, raw.headingDeltaDeg, true);
            pendingWayKey = null;
            pendingHits = 0;
            return activeMatch;
        }

        if (raw.segment.wayKey.equals(pendingWayKey)) {
            pendingHits++;
        } else {
            pendingWayKey = raw.segment.wayKey;
            pendingHits = 1;
        }

        float speed = location.hasSpeed() ? location.getSpeed() : 0f;
        int neededHits = speed >= 8.0f ? 2 : 3;
        if (pendingHits >= neededHits) {
            activeMatch = new Match(raw.segment, raw.distanceM, raw.headingDeltaDeg, true);
            pendingWayKey = null;
            pendingHits = 0;
        }
        return activeMatch;
    }

    /**
     * Conservative ambiguity guard. Even before the level lock is stable, if the
     * GNSS fix lies directly on a known grade-separated corridor, hiding a surface
     * signal is safer than presenting the wrong light/countdown.
     */
    public boolean shouldSuppressSurfaceSignals() {
        return (activeMatch != null && activeMatch.suppressSurfaceSignals())
                || (activeMatch == null && nearSpecialCorridor);
    }

    public boolean isOnRamp() {
        return activeMatch != null && activeMatch.isRamp();
    }

    public Match getActiveMatch() {
        return activeMatch;
    }

    private Match findBest(Location location, Float stableBearingDeg) {
        int gx = cell(location.getLongitude());
        int gy = cell(location.getLatitude());
        Set<String> seen = new HashSet<>();
        Segment best = null;
        double bestDistance = Double.MAX_VALUE;
        double bestHeadingDelta = 180.0;
        double bestScore = Double.MAX_VALUE;

        double threshold = ambiguityThreshold(location);
        float speed = location.hasSpeed() ? location.getSpeed() : 0f;
        boolean headingReliable = stableBearingDeg != null && speed >= 2.0f;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                List<Segment> bucket = grid.get(key(gx + dx, gy + dy));
                if (bucket == null) continue;
                for (Segment s : bucket) {
                    if (!seen.add(s.id)) continue;
                    double distance = distancePointToSegmentM(
                            location.getLatitude(), location.getLongitude(), s);
                    double allowed = s.structure == Structure.RAMP
                            ? Math.max(55.0, threshold) : threshold;
                    if (distance > allowed) continue;

                    double delta = 0.0;
                    if (headingReliable) {
                        double segmentBearing = bearingDegrees(s.lat1, s.lon1, s.lat2, s.lon2);
                        if ("-1".equals(s.oneway)) {
                            segmentBearing = normalize(segmentBearing + 180.0);
                        }
                        delta = angleDeltaAbs(stableBearingDeg, segmentBearing);
                        if (!isOneWay(s.oneway)) {
                            delta = Math.min(delta,
                                    angleDeltaAbs(stableBearingDeg, normalize(segmentBearing + 180.0)));
                        }
                        double headingLimit = s.structure == Structure.RAMP ? 70.0 : 52.0;
                        if (delta > headingLimit) continue;
                    }

                    double classBonus = 0.0;
                    if (s.roadClass == RoadClass.HIGHWAY) classBonus = 14.0;
                    else if (s.roadClass == RoadClass.EXPRESSWAY) classBonus = 10.0;
                    if (s.structure == Structure.RAMP) classBonus += 5.0;
                    else if (s.structure == Structure.TUNNEL) classBonus += 4.0;
                    else if (s.structure == Structure.ELEVATED) classBonus += 3.0;

                    double score = distance + delta * 0.30 - classBonus;
                    if (activeMatch != null && sameCorridor(activeMatch.segment, s)) {
                        score -= 18.0; // continuity/hysteresis beats parallel-road jitter.
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = s;
                        bestDistance = distance;
                        bestHeadingDelta = delta;
                    }
                }
            }
        }

        return best == null ? null : new Match(best, bestDistance, bestHeadingDelta, false);
    }

    private double ambiguityThreshold(Location location) {
        double accuracy = location.hasAccuracy() ? Math.max(5.0, location.getAccuracy()) : 20.0;
        // Wide enough for phone GNSS, narrow enough to reject the next parallel street.
        return Math.max(22.0, Math.min(48.0, accuracy * 1.55));
    }

    private void load(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("special_roads.psv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 13) continue;
                try {
                    Segment s = new Segment(
                            p[0],
                            p[1],
                            RoadClass.valueOf(p[2]),
                            Structure.valueOf(p[3]),
                            p[4],
                            p[5],
                            Integer.parseInt(p[6]),
                            Double.parseDouble(p[7]),
                            Double.parseDouble(p[8]),
                            Double.parseDouble(p[9]),
                            Double.parseDouble(p[10]),
                            p[11],
                            p[12]);
                    index(s);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void index(Segment s) {
        int minX = cell(Math.min(s.lon1, s.lon2) - 0.00025);
        int maxX = cell(Math.max(s.lon1, s.lon2) + 0.00025);
        int minY = cell(Math.min(s.lat1, s.lat2) - 0.00025);
        int maxY = cell(Math.max(s.lat1, s.lat2) + 0.00025);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                long key = key(x, y);
                ArrayList<Segment> bucket = grid.get(key);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    grid.put(key, bucket);
                }
                bucket.add(s);
            }
        }
    }

    private static boolean sameCorridor(Segment a, Segment b) {
        if (a == null || b == null) return false;
        if (a.wayKey.equals(b.wayKey)) return true;
        if (!a.ref.isEmpty() && a.ref.equals(b.ref)
                && a.roadClass == b.roadClass
                && a.structure == b.structure) return true;
        return !a.name.isEmpty() && a.name.equals(b.name)
                && a.roadClass == b.roadClass
                && a.structure == b.structure;
    }

    private static boolean isOneWay(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase();
        return "yes".equals(v) || "1".equals(v) || "true".equals(v) || "-1".equals(v);
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_DEG);
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private static double distancePointToSegmentM(double lat, double lon, Segment s) {
        double refLat = Math.toRadians(lat);
        double metersPerLat = 111320.0;
        double metersPerLon = 111320.0 * Math.cos(refLat);

        double ax = (s.lon1 - lon) * metersPerLon;
        double ay = (s.lat1 - lat) * metersPerLat;
        double bx = (s.lon2 - lon) * metersPerLon;
        double by = (s.lat2 - lat) * metersPerLat;

        double vx = bx - ax;
        double vy = by - ay;
        double denom = vx * vx + vy * vy;
        if (denom <= 1e-9) return Math.sqrt(ax * ax + ay * ay);

        double t = -(ax * vx + ay * vy) / denom;
        t = Math.max(0.0, Math.min(1.0, t));
        double px = ax + t * vx;
        double py = ay + t * vy;
        return Math.sqrt(px * px + py * py);
    }

    private static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2)
                - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return normalize(Math.toDegrees(Math.atan2(y, x)));
    }

    private static double angleDeltaAbs(double a, double b) {
        double d = Math.abs(normalize(a) - normalize(b));
        return Math.min(d, 360.0 - d);
    }

    private static double normalize(double v) {
        double out = v % 360.0;
        return out < 0.0 ? out + 360.0 : out;
    }
}
