package com.airportbus.ticket.api;

import com.airportbus.common.GlobalExceptionHandler;
import com.airportbus.ticket.CorrectionReport;
import com.airportbus.ticket.CorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CorrectionController.class, AdminCorrectionController.class})
@Import(GlobalExceptionHandler.class)
class CorrectionControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CorrectionService service;
    // @MapperScan 扫到的 mapper 都需 @MockBean,否则上下文起不来(见 BusQueryControllerTest 注释)
    @MockBean com.airportbus.user.mapper.UserMapper userMapper;
    @MockBean com.airportbus.user.mapper.RefreshTokenMapper refreshTokenMapper;
    @MockBean com.airportbus.user.mapper.FavoriteMapper favoriteMapper;
    @MockBean com.airportbus.bus.mapper.BusWriteMapper busWriteMapper;
    @MockBean com.airportbus.bus.mapper.BusQueryMapper busQueryMapper;
    @MockBean com.airportbus.bus.mapper.BusVersionMapper busVersionMapper;
    @MockBean com.airportbus.bus.mapper.SearchHotnessMapper searchHotnessMapper;
    @MockBean com.airportbus.message.mapper.MessageMapper messageMapper;
    @MockBean com.airportbus.audit.AuditMapper auditMapper;
    @MockBean com.airportbus.ticket.mapper.CorrectionMapper correctionMapper;

    @Test
    void publicSubmitReturns200WithoutAuth() throws Exception {
        CorrectionReport r = new CorrectionReport();
        r.id = 7; r.status = "OPEN"; r.description = "x";
        when(service.submit(any(), any())).thenReturn(r);
        mvc.perform(post("/api/v1/corrections").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(7))
           .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void adminListWithoutTokenIs401() throws Exception {
        // requireAdmin() 无主体 → ApiException(UNAUTHORIZED) → GlobalExceptionHandler → 401
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/corrections"))
           .andExpect(status().isUnauthorized());
    }
}
