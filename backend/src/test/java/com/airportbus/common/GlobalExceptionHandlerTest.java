package com.airportbus.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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

    @Test
    void apiExceptionMapsToStatusAndEnvelope() throws Exception {
        mvc.perform(get("/__probe/notfound"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("BUS_NOT_FOUND"))
           .andExpect(jsonPath("$.message").value("no such bus"))
           .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
