package com.airportbus.ticket.api;

import com.airportbus.common.GlobalExceptionHandler;
import com.airportbus.ticket.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({TicketController.class, AdminTicketController.class})
@Import(GlobalExceptionHandler.class)
class TicketControllerTest {
    @Autowired MockMvc mvc;
    @MockBean TicketService service;
    // @MapperScan 扫到的 mapper 都需 @MockBean,否则上下文起不来
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
    @MockBean com.airportbus.ticket.mapper.TicketMapper ticketMapper;
    @MockBean com.airportbus.ticket.mapper.TicketReplyMapper ticketReplyMapper;

    @Test
    void createWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/v1/tickets").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void adminListWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/admin/tickets")).andExpect(status().isUnauthorized());
    }
}
