package com.airportbus.bus.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 让 stops 的首元素始终是机场端(展示期重排,不改库内 seq)。 */
public final class AirportStopOrderer {

    private AirportStopOrderer() {}

    private static final String[] GENERIC = {
            "国际", "航站楼", "航站", "机场", "枢纽", "交通中心", "交通", "中心",
            "火车站", "车站", "地铁", "站", "international", "airport", "terminal",
            "station", "bus", "center", "centre"
    };
    private static final String[] AIRPORT_WORDS = {"机场", "航站楼", "航站", "airport", "terminal"};

    public static List<String> airportFirst(List<String> stops, String airportName, String cityName) {
        if (stops == null || stops.size() < 2) return stops;
        String ref = distinctiveRef(airportName, cityName);
        String first = stops.get(0), last = stops.get(stops.size() - 1);
        boolean reverse;
        int sf = score(first, ref), sl = score(last, ref);
        if (sf != sl) {
            reverse = sl > sf;
        } else {
            int af = airportness(first), al = airportness(last);
            reverse = al > af; // 打平(al==af)时 false,保持原序
        }
        if (!reverse) return stops;
        List<String> copy = new ArrayList<>(stops);
        Collections.reverse(copy);
        return copy;
    }

    private static int score(String stop, String ref) {
        if (ref.isEmpty()) return 0;
        return norm(stop).contains(ref) ? ref.length() : 0;
    }

    private static int airportness(String stop) {
        String n = norm(stop);
        int c = 0;
        for (String w : AIRPORT_WORDS) if (n.contains(w)) c++;
        return c;
    }

    /** airportName 去掉 cityName 与通用词,得到机场特征(如 虹桥 / 浦东;维也纳国际机场→空)。 */
    private static String distinctiveRef(String airportName, String cityName) {
        String r = norm(airportName);
        if (cityName != null) r = r.replace(norm(cityName), "");
        for (String g : GENERIC) r = r.replace(g, "");
        return r;
    }

    /** NFC + 小写 + 去空白与标点,保留 CJK / 字母 / 数字。 */
    private static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }
}
