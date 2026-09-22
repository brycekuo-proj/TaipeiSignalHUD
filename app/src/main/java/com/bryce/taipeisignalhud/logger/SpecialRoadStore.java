package com.bryce.taipeisignalhud.logger;

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

    /**
     * Sticky road-level state. A stacked road is not allowed to jump directly
     * back to SURFACE because of a single noisy GNSS fix; it must transition
     * through a ramp/connector or age out after repeated misses.
     */
    public enum RoadState {
        SURFACE,
        RAMP_ENTER,
        ELEVATED,
        EXPRESSWAY,
        FREEWAY,
        UNDERPASS,
        RAMP_EXIT
    }

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
            // Road class alone is not proof of grade separation. Many expressways,
            // freeway approaches and complex connectors run at surface level.
            return segmentSuppressesSurfaceSignals(segment);
        }

        public String displayName() {
            return segment.displayName();
        }
    }

    public static final class ExitAnchor {
        public final String name;
        public final double latitude;
        public final double longitude;
        public final double distanceM;
        public final float bearingDeg;

        ExitAnchor(String name, double latitude, double longitude,
                   double distanceM, float bearingDeg) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceM = distanceM;
            this.bearingDeg = bearingDeg;
        }
    }

    private static final double CELL_DEG = 0.0020; // ~200 m at Taiwan latitudes.
    private final Map<Long, ArrayList<Segment>> grid = new HashMap<>();
    private final Map<String, ArrayList<Segment>> segmentsByWay = new HashMap<>();

    private static final int RAMP_CONFIRM_HITS = 4;

    private Match activeMatch;
    private RoadState roadState = RoadState.SURFACE;
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
            // Exit ramps age out a little faster so surface-road matching can resume
            // shortly after the vehicle has actually completed the ramp.
            int maxMissHits = roadState == RoadState.RAMP_EXIT ? 3 : 6;
            if (activeMatch != null && missHits <= maxMissHits) return activeMatch;
            activeMatch = null;
            roadState = RoadState.SURFACE;
            return null;
        }

        missHits = 0;

        if (activeMatch != null && sameCorridor(activeMatch.segment, raw.segment)) {
            activeMatch = new Match(raw.segment, raw.distanceM, raw.headingDeltaDeg, true);
            if (raw.segment.structure != Structure.RAMP) {
                roadState = stateForSegment(raw.segment);
            }
            pendingWayKey = null;
            pendingHits = 0;
            return activeMatch;
        }

        if (raw.segment.structure == Structure.RAMP) {
            // A ramp can run only a few metres from the freeway/expressway mainline.
            // Never drop the road-level guard on a single ambiguous GNSS match.
            boolean leavingProtectedMainline = activeMatch != null
                    && activeMatch.suppressSurfaceSignals();
            if (activeMatch != null && leavingProtectedMainline) {
                if (raw.segment.wayKey.equals(pendingWayKey)) {
                    pendingHits++;
                } else {
                    pendingWayKey = raw.segment.wayKey;
                    pendingHits = 1;
                }
                if (pendingHits < RAMP_CONFIRM_HITS) {
                    return activeMatch;
                }
            }

            if (roadState != RoadState.RAMP_ENTER && roadState != RoadState.RAMP_EXIT) {
                roadState = leavingProtectedMainline
                        ? RoadState.RAMP_EXIT : RoadState.RAMP_ENTER;
            }
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

        if (activeMatch == null && segmentSuppressesSurfaceSignals(raw.segment)) {
            // Surface-first policy: an elevated/tunnel segment that merely overlaps the
            // phone GNSS trace is not enough to suppress surface-road signals. Require
            // a reliable travel heading, tight geometry, and several consecutive hits.
            // This avoids snapping 長安東路/堤頂大道 surface traffic onto nearby
            // elevated structures while still allowing a true grade-separated lock.
            double accuracy = location.hasAccuracy()
                    ? Math.max(5.0, location.getAccuracy()) : 20.0;
            double strongDistanceM = Math.max(12.0, Math.min(24.0, accuracy * 0.90));
            boolean strongHeading = stableBearingDeg != null
                    && speed >= 2.0f
                    && raw.headingDeltaDeg <= 28.0;
            boolean strongGeometry = raw.distanceM <= strongDistanceM;
            if (!strongHeading || !strongGeometry) {
                pendingWayKey = null;
                pendingHits = 0;
                return null;
            }
            neededHits = speed >= 12.0f ? 5 : 7;
        }

        if (pendingHits >= neededHits) {
            activeMatch = new Match(raw.segment, raw.distanceM, raw.headingDeltaDeg, true);
            roadState = stateForSegment(raw.segment);
            pendingWayKey = null;
            pendingHits = 0;
        }
        return activeMatch;
    }

    /**
     * Suppress surface signals only after a stable match has explicit grade-separation
     * evidence. Proximity, HIGHWAY/EXPRESSWAY class, and ramp state by themselves are
     * no longer enough because many of those segments are physically at grade.
     */
    public boolean shouldSuppressSurfaceSignals() {
        return activeMatch != null && activeMatch.suppressSurfaceSignals();
    }

    public boolean isOnRamp() {
        return roadState == RoadState.RAMP_ENTER || roadState == RoadState.RAMP_EXIT;
    }

    public RoadState getRoadState() {
        return roadState;
    }

    public Match getActiveMatch() {
        return activeMatch;
    }

    public ExitAnchor findForwardExitRamp(Location location, Float travelBearingDeg) {
        if (location == null || travelBearingDeg == null
                || activeMatch == null || !activeMatch.suppressSurfaceSignals()) {
            return null;
        }

        int gx = cell(location.getLongitude());
        int gy = cell(location.getLatitude());
        String token = corridorToken(activeMatch.segment.name);
        Set<String> seen = new HashSet<>();
        Segment bestRamp = null;
        double bestScore = Double.MAX_VALUE;

        // Search roughly 2 km around the vehicle for an exit ramp that lies ahead.
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -10; dy <= 10; dy++) {
                List<Segment> bucket = grid.get(key(gx + dx, gy + dy));
                if (bucket == null) continue;
                for (Segment s : bucket) {
                    if (s.structure != Structure.RAMP || !seen.add(s.id)) continue;
                    if (s.name == null || !s.name.contains("出口")) continue;

                    String rampToken = corridorToken(s.name);
                    if (!token.isEmpty() && !rampToken.isEmpty()
                            && !rampToken.contains(token) && !token.contains(rampToken)) {
                        continue;
                    }

                    double midLat = (s.lat1 + s.lat2) * 0.5;
                    double midLon = (s.lon1 + s.lon2) * 0.5;
                    double distance = distanceMeters(
                            location.getLatitude(), location.getLongitude(), midLat, midLon);
                    if (distance < 25.0 || distance > 2200.0) continue;

                    double bearing = bearingDegrees(
                            location.getLatitude(), location.getLongitude(), midLat, midLon);
                    double relative = angleDeltaAbs(travelBearingDeg, bearing);
                    if (relative > 75.0) continue;

                    double along = distance * Math.cos(Math.toRadians(relative));
                    double cross = distance * Math.sin(Math.toRadians(relative));
                    if (along < 20.0) continue;

                    double score = along + Math.abs(cross) * 0.45;
                    if (score < bestScore) {
                        bestScore = score;
                        bestRamp = s;
                    }
                }
            }
        }

        if (bestRamp == null) return null;

        // Use the downstream/farthest-ahead end of the whole ramp way as the surface
        // handoff point, not merely the nearest ramp segment.
        double bestLat = bestRamp.lat2;
        double bestLon = bestRamp.lon2;
        double bestAlong = -1.0;
        ArrayList<Segment> waySegments = segmentsByWay.get(bestRamp.wayKey);
        if (waySegments == null) waySegments = new ArrayList<>();
        for (Segment s : waySegments) {
            double[][] points = new double[][]{{s.lat1, s.lon1}, {s.lat2, s.lon2}};
            for (double[] point : points) {
                double distance = distanceMeters(
                        location.getLatitude(), location.getLongitude(), point[0], point[1]);
                if (distance > 2600.0) continue;
                double bearing = bearingDegrees(
                        location.getLatitude(), location.getLongitude(), point[0], point[1]);
                double relative = angleDeltaAbs(travelBearingDeg, bearing);
                if (relative > 100.0) continue;
                double along = distance * Math.cos(Math.toRadians(relative));
                if (along > bestAlong) {
                    bestAlong = along;
                    bestLat = point[0];
                    bestLon = point[1];
                }
            }
        }

        double distance = distanceMeters(
                location.getLatitude(), location.getLongitude(), bestLat, bestLon);
        return new ExitAnchor(
                bestRamp.name == null || bestRamp.name.isEmpty() ? "出口匝道" : bestRamp.name,
                bestLat,
                bestLon,
                distance,
                travelBearingDeg);
    }

    private Match findBest(Location location, Float stableBearingDeg) {
        int gx = cell(location.getLongitude());
        int gy = cell(location.getLatitude());
        Set<String> seen = new HashSet<>();
        Segment best = null;
        double bestDistance = Double.MAX_VALUE;
        double bestHeadingDelta = 180.0;
        double bestScore = Double.MAX_VALUE;
        Segment bestSuppressingMainline = null;
        double bestSuppressingDistance = Double.MAX_VALUE;
        double bestSuppressingHeadingDelta = 180.0;
        double bestSuppressingScore = Double.MAX_VALUE;

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
                    boolean gradeSeparated = segmentSuppressesSurfaceSignals(s);
                    // Only confirmed grade-separated geometry receives a strong class
                    // preference. At-grade expressway/highway segments compete mostly
                    // on actual distance + heading instead of their road class.
                    if (gradeSeparated) {
                        if (s.roadClass == RoadClass.HIGHWAY) classBonus += 7.0;
                        else if (s.roadClass == RoadClass.EXPRESSWAY) classBonus += 5.0;
                        if (s.structure == Structure.TUNNEL) classBonus += 4.0;
                        else if (s.structure == Structure.ELEVATED) classBonus += 3.0;
                        else if (s.layer != 0) classBonus += 2.0;
                    } else if (s.structure == Structure.RAMP) {
                        classBonus += 1.5;
                    }

                    double score = distance + delta * 0.30 - classBonus;
                    if (activeMatch != null && sameCorridor(activeMatch.segment, s)) {
                        score -= 18.0; // continuity/hysteresis beats parallel-road jitter.
                    }
                    if (s.structure != Structure.RAMP
                            && segmentSuppressesSurfaceSignals(s)
                            && score < bestSuppressingScore) {
                        bestSuppressingScore = score;
                        bestSuppressingMainline = s;
                        bestSuppressingDistance = distance;
                        bestSuppressingHeadingDelta = delta;
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

        // At interchanges the ramp and mainline often overlap inside normal phone-GNSS
        // error. If both are plausible, keep the grade-separated mainline lock unless
        // the ramp is clearly the better geometric/heading match. This prevents a
        // freeway vehicle from suddenly acquiring surface traffic lights below it.
        if (best != null
                && best.structure == Structure.RAMP
                && bestSuppressingMainline != null
                && bestSuppressingScore <= bestScore + 16.0) {
            best = bestSuppressingMainline;
            bestDistance = bestSuppressingDistance;
            bestHeadingDelta = bestSuppressingHeadingDelta;
        }

        return best == null ? null : new Match(best, bestDistance, bestHeadingDelta, false);
    }

    private static boolean segmentSuppressesSurfaceSignals(Segment s) {
        if (s == null || s.structure == Structure.RAMP) return false;
        if (s.structure == Structure.ELEVATED || s.structure == Structure.TUNNEL) {
            return true;
        }
        // OSM/official layer is explicit vertical evidence. A MAINLINE/COMPLEX
        // segment at layer 0 is treated as surface even when classed highway/expressway.
        return s.layer != 0;
    }

    private static RoadState stateForSegment(Segment s) {
        if (s == null) return RoadState.SURFACE;
        if (s.structure == Structure.TUNNEL) return RoadState.UNDERPASS;
        if (s.structure == Structure.ELEVATED) return RoadState.ELEVATED;
        if (s.roadClass == RoadClass.HIGHWAY) return RoadState.FREEWAY;
        if (s.roadClass == RoadClass.EXPRESSWAY) return RoadState.EXPRESSWAY;
        return RoadState.SURFACE;
    }

    private static boolean isProtectedRoadState(RoadState state) {
        return state == RoadState.RAMP_ENTER
                || state == RoadState.ELEVATED
                || state == RoadState.EXPRESSWAY
                || state == RoadState.FREEWAY
                || state == RoadState.UNDERPASS
                || state == RoadState.RAMP_EXIT;
    }

    private double ambiguityThreshold(Location location) {
        double accuracy = location.hasAccuracy() ? Math.max(5.0, location.getAccuracy()) : 20.0;
        // Wide enough for phone GNSS, narrow enough to reject the next parallel street.
        return Math.max(22.0, Math.min(48.0, accuracy * 1.55));
    }

    private void load(Context context) {
        loadAsset(context, "special_roads.psv");
        // Official open-data overlay supplements OSM around known elevated roads,
        // ramps and vehicle underpasses. Missing overlay is non-fatal for old builds.
        loadAsset(context, "special_roads_official.psv");
    }

    private void loadAsset(Context context, String assetName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(assetName), StandardCharsets.UTF_8))) {
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
        segmentsByWay.computeIfAbsent(s.wayKey, ignored -> new ArrayList<>()).add(s);
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

    private static String corridorToken(String raw) {
        if (raw == null) return "";
        return raw.replace("高速公路", "")
                .replace("快速道路", "")
                .replace("高架道路", "")
                .replace("高架", "")
                .replace("道路", "")
                .replace("主線", "")
                .replace("入口", "")
                .replace("出口", "")
                .replace("匝道", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0) * Math.sin(dp / 2.0)
                + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2.0) * Math.sin(dl / 2.0);
        return r * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
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
