package com.bryce.taipeisignalhud;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public final class SignalPlanStore {
    public static final class Estimate {
        public final TrafficLightView.State state;
        public final int remainingSeconds;
        public final boolean supported;
        public final String planId;

        private Estimate(TrafficLightView.State state, int remainingSeconds,
                         boolean supported, String planId) {
            this.state = state;
            this.remainingSeconds = remainingSeconds;
            this.supported = supported;
            this.planId = planId;
        }

        public static Estimate unsupported() {
            return new Estimate(TrafficLightView.State.UNKNOWN, -1, false, "");
        }
    }

    private static final class Phase {
        final int green;
        final int yellow;
        final int allRed;
        final int pedFlash;

        Phase(int green, int yellow, int allRed, int pedFlash) {
            this.green = Math.max(0, green);
            this.yellow = Math.max(0, yellow);
            this.allRed = Math.max(0, allRed);
            this.pedFlash = Math.max(0, pedFlash);
        }

        int duration() {
            return green + yellow + allRed;
        }
    }

    private static final class Plan {
        final String id;
        final int baseDirection;
        final int cycle;
        final int offset;
        final String phaseOrder;
        final List<Phase> phases;

        Plan(String id, int baseDirection, int cycle, int offset,
             String phaseOrder, List<Phase> phases) {
            this.id = id;
            this.baseDirection = baseDirection;
            this.cycle = cycle;
            this.offset = offset;
            this.phaseOrder = phaseOrder;
            this.phases = phases;
        }
    }

    private static final class DaySchedule {
        final int[] starts;
        final String[] planIds;

        DaySchedule(int[] starts, String[] planIds) {
            this.starts = starts;
            this.planIds = planIds;
        }

        String activePlan(int minuteOfDay) {
            if (starts.length == 0) return null;
            String active = planIds[0];
            for (int i = 0; i < starts.length; i++) {
                if (starts[i] <= minuteOfDay) active = planIds[i];
                else break;
            }
            return active;
        }
    }

    private final Map<String, Plan> plans = new HashMap<>();
    private final Map<String, DaySchedule> schedules = new HashMap<>();
    private final TimeZone taipei = TimeZone.getTimeZone("Asia/Taipei");

    public SignalPlanStore(Context context) {
        loadPlans(context);
        loadSchedules(context);
    }

    public Estimate estimate(String intersectionId, float travelBearingDeg, long nowMs) {
        if (intersectionId == null || intersectionId.isEmpty()) return Estimate.unsupported();

        Calendar cal = Calendar.getInstance(taipei);
        cal.setTimeInMillis(nowMs);
        int day = mondayBasedDay(cal.get(Calendar.DAY_OF_WEEK));
        int minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        DaySchedule schedule = schedules.get(intersectionId + "|" + day);
        if (schedule == null) return Estimate.unsupported();
        String planId = schedule.activePlan(minuteOfDay);
        if (planId == null) return Estimate.unsupported();

        Plan plan = plans.get(intersectionId + "|" + planId);
        if (plan == null || plan.cycle <= 0 || plan.phases.isEmpty()) {
            return Estimate.unsupported();
        }

        ArrayList<Integer> major = new ArrayList<>(4);
        for (int i = 0; i < plan.phases.size(); i++) {
            Phase p = plan.phases.get(i);
            if (p.pedFlash > 0 && p.green >= 15) major.add(i);
        }

        if (major.size() < 2) return Estimate.unsupported();

        // When a plan contains extra protected-turn phases, use the two dominant
        // pedestrian/through phases only if the third-largest phase is clearly smaller.
        // This expands road-test coverage without blindly decoding every phaseorder code.
        if (major.size() > 2) {
            major.sort((a, b) -> Integer.compare(
                    plan.phases.get(b).green,
                    plan.phases.get(a).green));
            int secondGreen = plan.phases.get(major.get(1)).green;
            int thirdGreen = plan.phases.get(major.get(2)).green;
            if (thirdGreen > secondGreen * 0.80f) return Estimate.unsupported();
            int a = major.get(0);
            int b = major.get(1);
            major.clear();
            if (a < b) {
                major.add(a);
                major.add(b);
            } else {
                major.add(b);
                major.add(a);
            }
        }

        int headingBucket = cardinalBucket(travelBearingDeg);
        boolean sameAxis = (headingBucket % 4) == (plan.baseDirection % 4);
        int selectedIndex = sameAxis ? major.get(0) : major.get(1);

        int timeline = 0;
        int selectedStart = -1;
        Phase selected = null;
        for (int i = 0; i < plan.phases.size(); i++) {
            if (i == selectedIndex) {
                selectedStart = timeline;
                selected = plan.phases.get(i);
            }
            timeline += plan.phases.get(i).duration();
        }
        if (selected == null || selectedStart < 0 || timeline <= 0) {
            return Estimate.unsupported();
        }

        // Nearly every official record sums to cycletime; use the explicit timeline
        // when a one-second rounding mismatch exists.
        int cycle = Math.abs(timeline - plan.cycle) <= 2 ? timeline : plan.cycle;
        if (cycle <= 0 || selectedStart >= cycle) return Estimate.unsupported();

        int secondOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600
                + cal.get(Calendar.MINUTE) * 60
                + cal.get(Calendar.SECOND);
        int pos = floorMod(secondOfDay - plan.offset, cycle);

        int greenEnd = selectedStart + selected.green;
        int yellowEnd = greenEnd + selected.yellow;
        int phaseEnd = yellowEnd + selected.allRed;

        if (pos >= selectedStart && pos < greenEnd) {
            return new Estimate(
                    TrafficLightView.State.GREEN,
                    Math.max(1, greenEnd - pos),
                    true,
                    plan.id);
        }

        if (selected.yellow > 0 && pos >= greenEnd && pos < yellowEnd) {
            return new Estimate(
                    TrafficLightView.State.YELLOW,
                    Math.max(1, yellowEnd - pos),
                    true,
                    plan.id);
        }

        int untilNextGreen;
        if (pos < selectedStart) {
            untilNextGreen = selectedStart - pos;
        } else {
            untilNextGreen = cycle - pos + selectedStart;
        }
        if (pos >= yellowEnd && pos < phaseEnd && selected.allRed > 0) {
            untilNextGreen = cycle - pos + selectedStart;
        }

        return new Estimate(
                TrafficLightView.State.RED,
                Math.max(1, untilNextGreen),
                true,
                plan.id);
    }

    public int planCount() {
        return plans.size();
    }

    private void loadPlans(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("signal_plans.psv"), StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 7) continue;
                try {
                    String icid = p[0];
                    String planId = p[1];
                    int direction = Integer.parseInt(p[2]);
                    int cycle = Integer.parseInt(p[3]);
                    int offset = Integer.parseInt(p[4]);
                    String phaseOrder = p[5];
                    String[] phaseParts = p[6].split(";");
                    ArrayList<Phase> phases = new ArrayList<>(phaseParts.length);
                    for (String phasePart : phaseParts) {
                        String[] f = phasePart.split(",", -1);
                        if (f.length < 4) continue;
                        phases.add(new Phase(
                                Integer.parseInt(f[0]),
                                Integer.parseInt(f[1]),
                                Integer.parseInt(f[2]),
                                Integer.parseInt(f[3])));
                    }
                    plans.put(icid + "|" + planId,
                            new Plan(planId, direction, cycle, offset, phaseOrder, phases));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void loadSchedules(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("signal_schedule.psv"), StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 3) continue;
                try {
                    String icid = p[0];
                    int day = Integer.parseInt(p[1]);
                    String[] pairs = p[2].split(",");
                    int[] starts = new int[pairs.length];
                    String[] planIds = new String[pairs.length];
                    int count = 0;
                    for (String pair : pairs) {
                        String[] kv = pair.split(":", -1);
                        if (kv.length != 2 || kv[0].length() < 3) continue;
                        int hhmm = Integer.parseInt(kv[0]);
                        int hour = hhmm / 100;
                        int minute = hhmm % 100;
                        starts[count] = hour * 60 + minute;
                        planIds[count] = kv[1];
                        count++;
                    }
                    if (count == 0) continue;
                    if (count != starts.length) {
                        int[] trimmedStarts = new int[count];
                        String[] trimmedPlans = new String[count];
                        System.arraycopy(starts, 0, trimmedStarts, 0, count);
                        System.arraycopy(planIds, 0, trimmedPlans, 0, count);
                        starts = trimmedStarts;
                        planIds = trimmedPlans;
                    }
                    schedules.put(icid + "|" + day, new DaySchedule(starts, planIds));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static int mondayBasedDay(int calendarDay) {
        switch (calendarDay) {
            case Calendar.MONDAY: return 1;
            case Calendar.TUESDAY: return 2;
            case Calendar.WEDNESDAY: return 3;
            case Calendar.THURSDAY: return 4;
            case Calendar.FRIDAY: return 5;
            case Calendar.SATURDAY: return 6;
            case Calendar.SUNDAY: return 7;
            default: return 1;
        }
    }

    private static int cardinalBucket(float degrees) {
        double d = degrees % 360.0;
        if (d < 0) d += 360.0;
        return ((int) Math.round(d / 45.0)) & 7;
    }

    private static int floorMod(int value, int mod) {
        int r = value % mod;
        return r < 0 ? r + mod : r;
    }
}
