package com.bryce.taipeisignalhud;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class HudMcpClient {
    static final class Row {
        final String intersectionId;
        final String name;
        final TrafficLightView.State state;
        final int remainingSeconds;

        Row(String intersectionId, String name,
            TrafficLightView.State state, int remainingSeconds) {
            this.intersectionId = intersectionId;
            this.name = name;
            this.state = state;
            this.remainingSeconds = remainingSeconds;
        }
    }

    static final class Snapshot {
        final boolean ok;
        final long serverTimeMs;
        final List<Row> rows;
        final String error;

        Snapshot(boolean ok, long serverTimeMs, List<Row> rows, String error) {
            this.ok = ok;
            this.serverTimeMs = serverTimeMs;
            this.rows = rows;
            this.error = error;
        }

        static Snapshot error(String message) {
            return new Snapshot(false, 0L, new ArrayList<>(), message);
        }
    }

    private final String endpoint;
    private final String token;

    HudMcpClient(Context context) {
        String endpointValue = "";
        String tokenValue = "";
        try (InputStream in = context.getAssets().open("mcp_runtime.json");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject config = new JSONObject(sb.toString());
            endpointValue = config.optString("endpoint", "").trim();
            tokenValue = config.optString("token", "").trim();
        } catch (Exception ignored) {
        }
        endpoint = endpointValue;
        token = tokenValue;
    }

    boolean isConfigured() {
        return (endpoint.startsWith("https://") || endpoint.startsWith("http://"))
                && !token.isEmpty();
    }

    Snapshot fetch(Location location, float stableBearingDeg,
                   List<IntersectionStore.Candidate> candidates) {
        if (!isConfigured()) return Snapshot.error("MCP not configured");
        if (location == null) return Snapshot.error("No location");

        HttpURLConnection connection = null;
        try {
            JSONObject args = new JSONObject();
            args.put("latitude", location.getLatitude());
            args.put("longitude", location.getLongitude());
            args.put("bearing_deg", stableBearingDeg);
            args.put("speed_mps", location.hasSpeed() ? location.getSpeed() : 0.0);
            args.put("accuracy_m", location.hasAccuracy() ? location.getAccuracy() : 20.0);
            args.put("limit", 3);
            JSONArray preferred = new JSONArray();
            if (candidates != null) {
                for (IntersectionStore.Candidate c : candidates) {
                    if (c != null && c.intersection != null && c.intersection.id != null) {
                        preferred.put(c.intersection.id);
                        if (preferred.length() >= 3) break;
                    }
                }
            }
            args.put("intersection_ids", preferred);

            JSONObject params = new JSONObject();
            params.put("name", "signal.hud_snapshot");
            params.put("arguments", args);

            JSONObject request = new JSONObject();
            request.put("jsonrpc", "2.0");
            request.put("id", UUID.randomUUID().toString());
            request.put("method", "tools/call");
            request.put("params", params);

            byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(3500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }

            int code = connection.getResponseCode();
            InputStream in = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(in);
            if (code < 200 || code >= 300) {
                return Snapshot.error("HTTP " + code);
            }

            JSONObject response = new JSONObject(body);
            if (response.has("error")) {
                return Snapshot.error(response.getJSONObject("error")
                        .optString("message", "MCP error"));
            }

            JSONObject result = response.optJSONObject("result");
            if (result == null) return Snapshot.error("Missing MCP result");

            JSONObject structured = result.optJSONObject("structuredContent");
            if (structured == null) {
                JSONArray content = result.optJSONArray("content");
                if (content != null && content.length() > 0) {
                    JSONObject first = content.optJSONObject(0);
                    if (first != null) {
                        structured = new JSONObject(first.optString("text", "{}"));
                    }
                }
            }
            if (structured == null || !structured.optBoolean("ok", false)) {
                return Snapshot.error("Invalid MCP snapshot");
            }

            ArrayList<Row> rows = new ArrayList<>();
            JSONArray items = structured.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String stateName = item.optString("state", "UNKNOWN");
                    TrafficLightView.State state;
                    try {
                        state = TrafficLightView.State.valueOf(stateName);
                    } catch (Exception ignored) {
                        state = TrafficLightView.State.UNKNOWN;
                    }
                    int remaining = item.isNull("remaining_s")
                            ? -1 : item.optInt("remaining_s", -1);
                    rows.add(new Row(
                            item.optString("intersection_id", ""),
                            item.optString("name", "—"),
                            state,
                            remaining));
                }
            }

            return new Snapshot(
                    true,
                    structured.optLong("server_time_ms", System.currentTimeMillis()),
                    rows,
                    "");
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            return Snapshot.error(message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
