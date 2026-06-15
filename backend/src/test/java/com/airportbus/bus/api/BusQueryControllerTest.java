package com.airportbus.bus.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.mapper.BusQueryMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
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
    void unknownBusReturns404Envelope() throws Exception {
        when(service.detail("nope")).thenThrow(new ApiException(ErrorCode.BUS_NOT_FOUND, "no bus: nope"));
        mvc.perform(get("/api/v1/buses/nope"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"));
    }
}
