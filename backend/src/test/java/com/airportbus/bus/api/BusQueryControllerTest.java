package com.airportbus.bus.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.SearchResultDto;
import com.airportbus.bus.mapper.BusQueryMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import com.airportbus.user.mapper.RefreshTokenMapper;
import com.airportbus.user.mapper.UserMapper;
import com.airportbus.bus.service.BusQueryService;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusQueryController.class)
@Import(GlobalExceptionHandler.class)
class BusQueryControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BusQueryService service;

    // @MapperScan 在主类上,@WebMvcTest 切片会注册这两个 mapper bean
    // 但不装配 MyBatis sqlSessionFactory,故用 @MockBean 顶替避免上下文启动失败
    @MockBean BusWriteMapper busWriteMapper;
    @MockBean BusQueryMapper busQueryMapper;
    @MockBean SearchHotnessMapper searchHotnessMapper;
    @MockBean UserMapper userMapper;
    @MockBean RefreshTokenMapper refreshTokenMapper;

    @Test
    void detailReturnsResourceBody() throws Exception {
        when(service.detail("vie-vab1")).thenReturn(new BusDetailDto(
                "vie-vab1", "VAB 1", "Westbahnhof", "ÖBB", "http://x", "40min", "€11", "03:00-24:00",
                null, false, List.of("A"), List.of(), List.of(), List.of(), List.of()));
        mvc.perform(get("/api/v1/buses/vie-vab1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sourceId").value("vie-vab1"))
           .andExpect(jsonPath("$.route").value("VAB 1"));
    }

    @Test
    void searchReturnsAirportsAndRoutes() throws Exception {
        when(service.search("中央")).thenReturn(new SearchResultDto(
                List.of(new SearchResultDto.AirportHit("VIE", "维也纳国际机场", "Vienna", "AT")),
                List.of(new SearchResultDto.RouteHit("vie-vab1", "VAB 1", "西站", "VIE", "维也纳中央车站 Hauptbahnhof (南入口)"))));
        mvc.perform(get("/api/v1/search").param("q", "中央"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.airports[0].code").value("VIE"))
           .andExpect(jsonPath("$.routes[0].sourceId").value("vie-vab1"))
           .andExpect(jsonPath("$.routes[0].matchedStop").value("维也纳中央车站 Hauptbahnhof (南入口)"));
    }

    @Test
    void blankSearchReturnsEmpty() throws Exception {
        when(service.search("")).thenReturn(new SearchResultDto(List.of(), List.of()));
        mvc.perform(get("/api/v1/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.airports").isEmpty())
           .andExpect(jsonPath("$.routes").isEmpty());
    }

    @Test
    void unknownBusReturns404Envelope() throws Exception {
        when(service.detail("nope")).thenThrow(new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: nope"));
        mvc.perform(get("/api/v1/buses/nope"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"));
    }
}
