package com.airportbus.bus.hash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;

/** E2:共享 canonicalizer。NFC + trim;null/空串=缺失;key 有序 + 数组规则排序。 */
public final class Canonicalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MISSING = " "; // 缺失哨兵,绝不会与真实文本冲突

    private Canonicalizer() {}

    public static String canonicalJson(CanonicalBus b) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("route", norm(b.route()));
        root.put("destination", norm(b.destination()));
        root.put("operator", norm(b.operator()));
        root.put("duration", norm(b.duration()));
        root.put("price", norm(b.price()));
        root.put("operatingHours", norm(b.operatingHours()));

        // stops:保序
        List<String> stops = new ArrayList<>();
        for (String s : nz(b.stops())) stops.add(norm(s));
        root.put("stops", stops);

        // schedules:按 (timeRange, intervalText) 稳定排序
        List<Map<String, String>> schedules = new ArrayList<>();
        for (CanonicalBus.Schedule s : nz(b.schedules())) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("timeRange", norm(s.timeRange()));
            m.put("intervalText", norm(s.intervalText()));
            m.put("note", norm(s.note()));
            schedules.add(m);
        }
        schedules.sort(Comparator.comparing((Map<String, String> m) -> m.get("timeRange"))
                .thenComparing(m -> m.get("intervalText")));
        root.put("schedules", schedules);

        // alerts:按 (type, startDate, endDate) 稳定排序
        List<Map<String, String>> alerts = new ArrayList<>();
        for (CanonicalBus.Alert a : nz(b.alerts())) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("type", norm(a.type()));
            m.put("message", norm(a.message()));
            m.put("startDate", norm(a.startDate()));
            m.put("endDate", norm(a.endDate()));
            alerts.add(m);
        }
        alerts.sort(Comparator.comparing((Map<String, String> m) -> m.get("type"))
                .thenComparing(m -> m.get("startDate")).thenComparing(m -> m.get("endDate")));
        root.put("alerts", alerts);

        root.put("images", media(b.images()));
        root.put("files", media(b.files()));

        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("canonical json failed", e);
        }
    }

    public static String contentHash(CanonicalBus b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonicalJson(b).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : digest) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** media:按 url 稳定排序。 */
    private static List<Map<String, String>> media(List<CanonicalBus.Media> in) {
        List<Map<String, String>> out = new ArrayList<>();
        for (CanonicalBus.Media m : nz(in)) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("url", norm(m.url()));
            e.put("label", norm(m.label()));
            out.add(e);
        }
        out.sort(Comparator.comparing(m -> m.get("url")));
        return out;
    }

    /** NFC 归一 + trim;null/空 → 缺失哨兵。 */
    private static String norm(String s) {
        if (s == null) return MISSING;
        String n = Normalizer.normalize(s, Normalizer.Form.NFC).trim();
        return n.isEmpty() ? MISSING : n;
    }

    private static <T> List<T> nz(List<T> in) {
        return in == null ? List.of() : in;
    }
}
