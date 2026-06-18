package com.airportbus.bus.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AirportStopOrdererTest {

    private String first(List<String> stops, String airport, String city) {
        return AirportStopOrderer.airportFirst(stops, airport, city).get(0);
    }

    @Test
    void airportEndpointAlwaysFirst() {
        // 机场在末尾 → 反转
        assertThat(first(List.of("维也纳西站 Westbahnhof", "维也纳中央车站 Hauptbahnhof (南入口)", "维也纳机场"),
                "维也纳国际机场", "维也纳")).isEqualTo("维也纳机场");
        assertThat(first(List.of("Morzinplatz / Schwedenplatz", "维也纳机场"),
                "维也纳国际机场", "维也纳")).isEqualTo("维也纳机场");
        // 机场在开头 → 保持
        assertThat(first(List.of("浦东机场", "上海火车站(北广场)"),
                "上海浦东国际机场", "上海")).isEqualTo("浦东机场");
        assertThat(first(List.of("浦东机场", "上海南站"),
                "上海浦东国际机场", "上海")).isEqualTo("浦东机场");
        // 机场端不含「机场」字样(虹桥东交通中心),靠特征 ref「虹桥」判定
        assertThat(first(List.of("虹桥东交通中心", "上海火车站(南广场)"),
                "上海虹桥国际机场", "上海")).isEqualTo("虹桥东交通中心");
        // 另一端含「机场」但属别的机场(浦东),不能误选 → 仍选虹桥端
        assertThat(first(List.of("虹桥东交通中心", "浦东国际机场"),
                "上海虹桥国际机场", "上海")).isEqualTo("虹桥东交通中心");
        assertThat(first(List.of("虹桥机场 T1 航站楼", "清涧新村"),
                "上海虹桥国际机场", "上海")).isEqualTo("虹桥机场 T1 航站楼");
        // 机场内摆渡(停车库 ↔ 航站楼),ref 打平 → 关键词「航站楼」兜底
        assertThat(first(List.of("P4 长时停车库", "T1 航站楼", "T2 航站楼"),
                "上海浦东国际机场", "上海")).isEqualTo("T2 航站楼");
    }

    @Test
    void shortListsUntouched() {
        assertThat(AirportStopOrderer.airportFirst(List.of("唯一站"), "X机场", "X")).containsExactly("唯一站");
        assertThat(AirportStopOrderer.airportFirst(List.of(), "X机场", "X")).isEmpty();
    }
}
