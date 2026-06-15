package com.airportbus.common;

import com.airportbus.bus.mapper.BusQueryMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.Probe.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.Probe.class})
class GlobalExceptionHandlerTest {

    @RestController
    static class Probe {
        @GetMapping("/__probe/notfound")
        String notFound() { throw new ApiException(ErrorCode.BUS_NOT_FOUND, "no such bus"); }
    }

    @Autowired MockMvc mvc;

    // @MapperScan("com.airportbus.bus.mapper") 在主类上,@WebMvcTest 切片会注册 BusWriteMapper
    // 这个 bean 但不装配 MyBatis sqlSessionFactory,故用 @MockBean 顶替,避免切片上下文启动失败。
    @MockBean BusWriteMapper busWriteMapper;
    @MockBean BusQueryMapper busQueryMapper;
    @MockBean SearchHotnessMapper searchHotnessMapper;

    @Test
    void apiExceptionMapsToStatusAndEnvelope() throws Exception {
        mvc.perform(get("/__probe/notfound"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"))
           .andExpect(jsonPath("$.message").value("no such bus"))
           .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
